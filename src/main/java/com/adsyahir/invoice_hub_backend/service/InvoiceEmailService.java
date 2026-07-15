package com.adsyahir.invoice_hub_backend.service;

import com.adsyahir.invoice_hub_backend.dao.InvoiceRepo;
import com.adsyahir.invoice_hub_backend.model.Invoice;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Outbound invoice email. Extracted from {@link InvoiceService} so a Kafka consumer can
 * own delivery.
 *
 * <p>The important difference from the old inline version: these methods are ALLOWED TO
 * THROW. Previously the send happened inside the request transaction, so a failure had to
 * be swallowed (log.warn and carry on) or it would have broken the business action —
 * meaning a dead SMTP server silently lost the email with nobody the wiser. Now the caller
 * is a consumer: a throw means "retry me", and after the retries are exhausted the record
 * lands on a dead-letter topic where it can be seen and replayed. The failure became
 * recoverable instead of ignored.
 *
 * <p>Read-only transaction: the invoice is reloaded by the consumer and its lazy
 * associations (client, tenant, lineItems) are touched while rendering the PDF.
 */
@Service
@RequiredArgsConstructor
public class InvoiceEmailService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceEmailService.class);

    private final InvoiceRepo invoiceRepo;
    private final InvoicePdfService pdfService;
    private final JavaMailSender mailSender;

    @Value("${app.base-url:http://localhost:5173}")
    private String appBaseUrl;

    /**
     * Email the client the invoice PDF and a pay link.
     *
     * @throws org.springframework.mail.MailException if delivery fails — the consumer
     *         retries, so do NOT swallow this
     */
    @Transactional(readOnly = true)
    public void sendInvoice(Long invoiceId) {
        // Loaded HERE, inside this transaction. If the caller loaded it and passed the entity,
        // its lazy associations (client, tenant, lineItems) would belong to a session that has
        // already closed by the time we run, and the first getClient() would throw
        // LazyInitializationException. One session, one unit of work.
        Invoice invoice = invoiceRepo.findById(invoiceId)
                .orElseThrow(() -> new IllegalStateException("Invoice " + invoiceId + " not found"));

        String to = invoice.getClient() != null ? invoice.getClient().getEmail() : null;
        if (to == null || to.isBlank()) {
            // Nothing to retry — a missing address will still be missing next attempt.
            log.info("Invoice {} has no client email — skipping delivery", invoice.getInvoiceNumber());
            return;
        }

        byte[] pdf = pdfService.render(invoice);
        String payLink = appBaseUrl + "/pay/" + invoice.getPaymentLinkToken();
        String org = invoice.getTenant() != null ? invoice.getTenant().getName() : "InvoiceHub";

        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom("no-reply@invoicehub.local");
            helper.setTo(to);
            helper.setSubject("Invoice " + invoice.getInvoiceNumber() + " from " + org);
            helper.setText("""
                    <p>Hello,</p>
                    <p>Please find attached invoice <strong>%s</strong> for <strong>%s %s</strong>,
                    due %s.</p>
                    <p>You can view and pay online here:<br/>
                    <a href="%s">%s</a></p>
                    <p>Thank you,<br/>%s</p>
                    """.formatted(
                    invoice.getInvoiceNumber(), invoice.getCurrency(), nz(invoice.getAmountDue()),
                    invoice.getDueDate(), payLink, payLink, org), true);
            helper.addAttachment(invoice.getInvoiceNumber() + ".pdf",
                    new ByteArrayResource(pdf), "application/pdf");
        } catch (jakarta.mail.MessagingException e) {
            // Malformed message — retrying will produce the same result, so fail fast to
            // the DLT rather than burning the retry budget.
            throw new IllegalStateException(
                    "Could not build the invoice email for " + invoice.getInvoiceNumber(), e);
        }

        mailSender.send(message);
        log.info("Emailed invoice {} to {}", invoice.getInvoiceNumber(), to);
    }

    /** Receipt for a payment against an invoice. */
    @Transactional(readOnly = true)
    public void sendReceipt(Long invoiceId, BigDecimal amount, boolean settled) {
        Invoice invoice = invoiceRepo.findById(invoiceId)
                .orElseThrow(() -> new IllegalStateException("Invoice " + invoiceId + " not found"));

        String to = invoice.getClient() != null ? invoice.getClient().getEmail() : null;
        if (to == null || to.isBlank()) {
            log.info("Invoice {} has no client email — skipping receipt", invoice.getInvoiceNumber());
            return;
        }

        String org = invoice.getTenant() != null ? invoice.getTenant().getName() : "InvoiceHub";
        String closing = settled
                ? "<p>This invoice is now fully paid. Thank you!</p>"
                : "<p>Outstanding balance: <strong>%s %s</strong>.</p>"
                        .formatted(invoice.getCurrency(), nz(invoice.getAmountDue()));

        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom("no-reply@invoicehub.local");
            helper.setTo(to);
            helper.setSubject("Payment received for invoice " + invoice.getInvoiceNumber());
            helper.setText("""
                    <p>Hello,</p>
                    <p>We have received a payment of <strong>%s %s</strong> for invoice
                    <strong>%s</strong>.</p>
                    %s
                    <p>Thank you,<br/>%s</p>
                    """.formatted(
                    invoice.getCurrency(), amount, invoice.getInvoiceNumber(), closing, org), true);
        } catch (jakarta.mail.MessagingException e) {
            throw new IllegalStateException(
                    "Could not build the receipt email for " + invoice.getInvoiceNumber(), e);
        }

        mailSender.send(message);
        log.info("Emailed receipt for {} to {}", invoice.getInvoiceNumber(), to);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
