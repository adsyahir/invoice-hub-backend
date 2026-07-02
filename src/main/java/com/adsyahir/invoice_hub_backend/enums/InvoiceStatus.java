package com.adsyahir.invoice_hub_backend.enums;

/**
 * Lifecycle status of an invoice. Persisted as the enum NAME via
 * {@code @Enumerated(EnumType.STRING)} on the Invoice entity's {@code status}
 * field, which maps to the {@code invoices.status VARCHAR(50)} column.
 */
public enum InvoiceStatus {
    DRAFT("Draft"),
    SENT("Sent"),
    PARTIALLY_PAID("Partially Paid"),
    PAID("Paid"),
    OVERDUE("Overdue"),
    VOID("Void"),
    REFUNDED("Refunded");

    private final String label;

    InvoiceStatus(String label) {
        this.label = label;
    }

    /** Human-friendly display label for UI/PDF. */
    public String getLabel() {
        return label;
    }
}
