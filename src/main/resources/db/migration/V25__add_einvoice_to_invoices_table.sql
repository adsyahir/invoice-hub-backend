-- LHDN MyInvois e-invoicing (Malaysia). Every invoice carries its own e-invoice
-- lifecycle independent of the payment status: an invoice can be PAID but still
-- NOT_SUBMITTED to LHDN. Existing rows default to NOT_SUBMITTED.
ALTER TABLE invoices
    ADD COLUMN einvoice_status          VARCHAR(20)  NOT NULL DEFAULT 'NOT_SUBMITTED',
    ADD COLUMN einvoice_type            VARCHAR(2)   NOT NULL DEFAULT '01', -- LHDN doc type: 01 Invoice, 02 Credit Note, 03 Debit Note
    ADD COLUMN myinvois_uuid            VARCHAR(64),   -- LHDN-assigned unique identifier
    ADD COLUMN myinvois_long_id         VARCHAR(128),  -- long id used to build the validation URL/QR
    ADD COLUMN einvoice_validation_url  TEXT,          -- encoded into the QR shown on the PDF
    ADD COLUMN einvoice_submitted_at    TIMESTAMP,
    ADD COLUMN einvoice_validated_at    TIMESTAMP,
    ADD COLUMN einvoice_rejection_reason TEXT;

-- Fast lookups when chasing invoices that still need submitting / are pending.
CREATE INDEX idx_invoices_einvoice_status ON invoices(tenant_id, einvoice_status)
    WHERE deleted_at IS NULL;
