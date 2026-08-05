package com.adsyahir.invoice_hub_backend.service;

import com.adsyahir.invoice_hub_backend.dao.InvoiceRepo;
import com.adsyahir.invoice_hub_backend.enums.InvoiceStatus;
import com.adsyahir.invoice_hub_backend.model.Invoice;
import com.adsyahir.invoice_hub_backend.model.Tenant;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Turns an invoice's public payment link into a Stripe Checkout session.
 *
 * <p><b>Why the session is minted on click, not at send time.</b> Checkout sessions expire
 * (24h), but an emailed invoice might be paid three weeks later. So the email carries the
 * durable {@code /pay/{token}} link and this runs when the payer actually clicks Pay —
 * a fresh session every time, no dead links in anyone's inbox.
 *
 * <p><b>Direct charges.</b> The session is created ON the tenant's connected account
 * ({@code Stripe-Account}), so the tenant is the merchant of record, the money lands in
 * their balance, and the charge appears on the payer's statement under their name. No
 * application fee is taken — a Malaysian platform on the Stripe-liable model can't collect
 * one (see StripeConnectService), and InvoiceHub monetises by subscription anyway.
 */
@Service
public class StripeCheckoutService {

    private static final Logger log = LoggerFactory.getLogger(StripeCheckoutService.class);

    private final InvoiceRepo invoiceRepo;
    private final StripeClient stripe;
    private final StripeConnectService connectService;

    @Value("${stripe.key:}")
    private String stripeKey;

    @Value("${app.base-url}")
    private String appBaseUrl;

    public StripeCheckoutService(InvoiceRepo invoiceRepo, StripeClient stripe,
                                 StripeConnectService connectService) {
        this.invoiceRepo = invoiceRepo;
        this.stripe = stripe;
        this.connectService = connectService;
    }

    /**
     * Create a Checkout session for the invoice behind {@code token} and return its URL.
     *
     * <p>Unauthenticated: the token IS the capability, so every precondition the authed
     * path would enforce has to be re-checked here.
     */
    @Transactional
    public String createCheckoutUrl(String token) {
        if (stripeKey == null || stripeKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Online payment is not available");
        }

        Invoice invoice = invoiceRepo.findByPaymentLinkToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));

        if (invoice.getPaymentLinkExpiresAt() != null
                && invoice.getPaymentLinkExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "This payment link has expired");
        }
        if (invoice.getStatus() == InvoiceStatus.VOID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This invoice is no longer payable");
        }

        BigDecimal due = invoice.getAmountDue() == null ? BigDecimal.ZERO : invoice.getAmountDue();
        if (due.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This invoice is already paid");
        }

        Tenant tenant = invoice.getTenant();
        // Never refuse a payer on a stale cache: if our copy says charges are off, ask
        // Stripe before turning them away. The tenant may have finished onboarding since
        // we last looked — the webhook can't reach a dev machine at all.
        if (tenant == null
                || (!tenant.isStripeChargesEnabled() && !connectService.refreshChargesEnabled(tenant))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This business isn’t set up to accept online payments yet");
        }

        String payPage = appBaseUrl + "/pay/" + token;

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(payPage + "?paid=1")
                .setCancelUrl(payPage)
                // Ties the webhook back to this invoice without trusting anything the
                // browser sends back on the success URL.
                .setClientReferenceId(invoice.getUuid().toString())
                .putMetadata("invoice_id", String.valueOf(invoice.getId()))
                .putMetadata("invoice_number", invoice.getInvoiceNumber())
                .putMetadata("tenant_id", String.valueOf(tenant.getId()))
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(invoice.getCurrency().toLowerCase())
                                .setUnitAmount(toMinorUnits(due))
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Invoice " + invoice.getInvoiceNumber())
                                        .setDescription(tenant.getName())
                                        .build())
                                .build())
                        .build())
                .build();

        // Runs AS the connected account — this is what makes it a direct charge.
        RequestOptions options = RequestOptions.builder()
                .setStripeAccount(tenant.getStripeAccountId())
                .build();

        try {
            Session session = stripe.checkout().sessions().create(params, options);
            log.info("Created Checkout session {} for invoice {} on {}",
                    session.getId(), invoice.getInvoiceNumber(), tenant.getStripeAccountId());
            return session.getUrl();
        } catch (StripeException ex) {
            // Detail to the log, not to the payer: a currency/capability misconfiguration is
            // the merchant's problem to fix and means nothing to the person trying to pay.
            log.error("Checkout session failed for invoice {} on {}: {}",
                    invoice.getInvoiceNumber(), tenant.getStripeAccountId(), ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Couldn’t start the payment. Please try again shortly.");
        }
    }

    /**
     * Stripe works in the currency's smallest unit — 12.34 MYR is 1234 sen. Only correct
     * for 2-decimal currencies, which covers everything InvoiceHub issues today; a
     * zero-decimal currency (JPY) would need a per-currency exponent.
     */
    private long toMinorUnits(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }
}
