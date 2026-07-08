package com.adsyahir.invoice_hub_backend.service;

import com.adsyahir.invoice_hub_backend.model.Invoice;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;

/**
 * Renders an invoice to a PDF. The invoice is first laid out as XHTML by a
 * Thymeleaf template (templates/invoice-pdf.html), then converted to PDF by
 * openhtmltopdf. Keeping the layout in a template means the PDF and any future
 * on-screen print view share one source of truth.
 */
@Service
public class InvoicePdfService {

    private final TemplateEngine templateEngine;

    public InvoicePdfService(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /** Build the PDF bytes for an invoice (line items, client and tenant must be loaded). */
    public byte[] render(Invoice invoice) {
        Context context = new Context();
        context.setVariable("inv", invoice);
        context.setVariable("client", invoice.getClient());
        context.setVariable("tenant", invoice.getTenant());
        context.setVariable("lineItems", invoice.getLineItems());

        // Thymeleaf HTML mode over a well-formed template yields valid XHTML,
        // which openhtmltopdf's strict XML parser requires.
        String html = templateEngine.process("invoice-pdf", context);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to render invoice PDF for " + invoice.getInvoiceNumber(), e);
        }
    }
}
