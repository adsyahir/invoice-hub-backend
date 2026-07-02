-- Line items belonging to an invoice. Deleted with the parent invoice.
-- tax_amount / line_total are computed server-side, not trusted from the client.
CREATE TABLE invoice_line_items (
    id           SERIAL PRIMARY KEY,
    invoice_id   BIGINT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,  -- FK to invoices.id (internal bigint)
    description  VARCHAR(500) NOT NULL,
    quantity     BIGINT NOT NULL CHECK (quantity > 0),               -- whole units only (for now)
    unit_price   NUMERIC(15,2) NOT NULL CHECK (unit_price >= 0),
    tax_rate     NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK (tax_rate >= 0 AND tax_rate <= 100), -- e.g. 8.00 for 8% SST
    tax_amount   NUMERIC(15,2) NOT NULL DEFAULT 0,                    -- base * tax_rate / 100
    line_total   NUMERIC(15,2) NOT NULL,                             -- (quantity * unit_price) + tax_amount
    sort_order   INTEGER NOT NULL                                    -- display order
);

CREATE INDEX idx_line_items_invoice ON invoice_line_items(invoice_id);
