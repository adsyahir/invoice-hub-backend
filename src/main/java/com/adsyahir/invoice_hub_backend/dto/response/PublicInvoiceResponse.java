package com.adsyahir.invoice_hub_backend.dto.response;

import com.adsyahir.invoice_hub_backend.enums.InvoiceStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Public (unauthenticated) view of an invoice, resolved by its payment-link
 * token. Intentionally minimal — only what a payer needs to see. No tenant ids,
 * internal notes or e-invoice internals are exposed.
 */
@Getter
@Builder
public class PublicInvoiceResponse {
    private String invoiceNumber;
    private String organizationName;
    private String clientName;
    private String currency;
    private BigDecimal totalAmount;
    private BigDecimal amountPaid;
    private BigDecimal amountDue;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private InvoiceStatus status;
    private boolean payable;
    private List<Line> lineItems;

    @Getter
    @Builder
    public static class Line {
        private String description;
        private Long quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
    }
}
