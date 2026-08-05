package com.adsyahir.invoice_hub_backend.controller;

import com.adsyahir.invoice_hub_backend.service.PaymentService;
import com.adsyahir.invoice_hub_backend.service.StripeConnectService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Account;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Stripe's callback into InvoiceHub.
 *
 * <p>Stripe cannot present a JWT, so this path is permitAll and the HMAC signature IS the
 * authentication — it decides whether a tenant can accept money, so an unverified payload
 * would let anyone flip {@code charges_enabled} by guessing an id. The raw body is taken
 * as a String because re-serializing parsed JSON breaks the signature.
 *
 * <p>Failures return 4xx/5xx so Stripe retries; Stripe treats a slow endpoint as a failed
 * one, so success returns 200 immediately.
 */
@RestController
@RequestMapping("/webhooks/stripe")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final StripeConnectService connectService;
    private final PaymentService paymentService;

    /**
     * Signing secret (whsec_…). Blank means refuse everything — an empty secret must
     * never be read as "skip the check".
     */
    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    public StripeWebhookController(StripeConnectService connectService, PaymentService paymentService) {
        this.connectService = connectService;
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<String> handle(@RequestBody String payload,
                                         @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("Stripe webhook received but stripe.webhook-secret is not configured — rejecting");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("webhooks not configured");
        }
        if (signature == null || signature.isBlank()) {
            return ResponseEntity.badRequest().body("missing signature");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException ex) {
            // Often just a stale secret, but either way the payload is untrusted.
            log.warn("Rejected Stripe webhook with an invalid signature: {}", ex.getMessage());
            return ResponseEntity.badRequest().body("invalid signature");
        }

        // Unhandled events are acknowledged, not 500'd, so Stripe stops retrying them.
        switch (event.getType()) {
            case "account.updated" -> event.getDataObjectDeserializer().getObject()
                    .filter(Account.class::isInstance)
                    .map(Account.class::cast)
                    .ifPresentOrElse(
                            connectService::sync,
                            () -> log.warn("account.updated {} could not be deserialized — "
                                    + "likely an API version mismatch between Stripe and stripe-java",
                                    event.getId()));

            case "checkout.session.completed" -> event.getDataObjectDeserializer().getObject()
                    .filter(Session.class::isInstance)
                    .map(Session.class::cast)
                    .ifPresentOrElse(
                            this::onCheckoutCompleted,
                            () -> log.warn("checkout.session.completed {} could not be deserialized",
                                    event.getId()));

            default -> {
                if (log.isDebugEnabled()) {
                    log.debug("Ignoring Stripe event {} ({})", event.getId(), event.getType());
                }
            }
        }

        return ResponseEntity.ok("ok");
    }

    /**
     * A payer completed Checkout. Book the payment against the invoice named in the
     * session metadata.
     *
     * <p>{@code payment_status} is checked because a completed session is not always a paid
     * one — asynchronous methods (FPX, bank debits) complete the session while the funds are
     * still pending, and booking those as received would show money that hasn't arrived.
     */
    private void onCheckoutCompleted(Session session) {
        if (!"paid".equals(session.getPaymentStatus())) {
            log.info("Checkout session {} completed but payment_status={} — not booking yet",
                    session.getId(), session.getPaymentStatus());
            return;
        }

        String invoiceId = session.getMetadata() == null ? null : session.getMetadata().get("invoice_id");
        if (invoiceId == null) {
            log.warn("Checkout session {} has no invoice_id metadata — ignoring", session.getId());
            return;
        }

        // Amount as Stripe collected it, converted back from minor units.
        BigDecimal amount = BigDecimal.valueOf(session.getAmountTotal()).movePointLeft(2);
        String currency = session.getCurrency() == null
                ? null : session.getCurrency().toUpperCase(Locale.ROOT);
        // The PaymentIntent is the stable id for the money itself; the session id is only
        // the checkout attempt. Fall back if it is somehow absent.
        String txnId = session.getPaymentIntent() != null ? session.getPaymentIntent() : session.getId();

        boolean recorded = paymentService.recordStripePayment(
                Long.valueOf(invoiceId), amount, currency, txnId);

        if (recorded) {
            log.info("Recorded Stripe payment {} of {} {} for invoice {}",
                    txnId, currency, amount, session.getMetadata().get("invoice_number"));
        } else {
            log.debug("Stripe payment {} already recorded — duplicate delivery ignored", txnId);
        }
    }
}
