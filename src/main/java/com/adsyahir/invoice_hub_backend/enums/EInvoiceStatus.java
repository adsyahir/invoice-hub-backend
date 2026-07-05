package com.adsyahir.invoice_hub_backend.enums;

/**
 * LHDN MyInvois e-invoice lifecycle (Malaysia). Persisted as the enum NAME via
 * {@code @Enumerated(EnumType.STRING)} on the Invoice entity, mapped to the
 * {@code invoices.einvoice_status VARCHAR(20)} column.
 *
 * <pre>
 *  NOT_SUBMITTED — created locally, not yet sent to LHDN
 *  PENDING       — submitted, awaiting LHDN validation
 *  VALIDATED     — LHDN accepted; UUID + validation QR issued
 *  REJECTED      — LHDN rejected (see einvoiceRejectionReason)
 *  CANCELLED     — cancelled within the 72-hour window after validation
 * </pre>
 */
public enum EInvoiceStatus {
    NOT_SUBMITTED("Not submitted"),
    PENDING("Pending LHDN"),
    VALIDATED("Validated"),
    REJECTED("Rejected"),
    CANCELLED("Cancelled");

    private final String label;

    EInvoiceStatus(String label) {
        this.label = label;
    }

    /** Human-friendly display label for UI/PDF. */
    public String getLabel() {
        return label;
    }
}
