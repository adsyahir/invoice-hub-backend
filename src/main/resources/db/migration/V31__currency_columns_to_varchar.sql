-- Currency columns: CHAR(3) -> VARCHAR(3).
--
-- WHY: the entities map currency as a plain String with @Column(length = 3), which
-- Hibernate expects to be VARCHAR(3). The migrations declared CHAR(3) (bpchar), so
-- schema validation fails:
--
--   Schema validation: wrong column type encountered in column [currency] in table
--   [clients]; found [bpchar (Types#CHAR)], but expecting [varchar(3) (Types#VARCHAR)]
--
-- CHAR is also the wrong type on its own merits: it is blank-padded, so a comparison
-- against a shorter literal can behave surprisingly. ISO 4217 codes are always exactly
-- 3 characters, so VARCHAR(3) stores the same data with none of the padding semantics.
--
-- SAFETY: every existing value is already exactly 3 characters ('MYR'), so there is no
-- truncation and no trailing whitespace to strip.

ALTER TABLE clients  ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE invoices ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE payments ALTER COLUMN currency TYPE VARCHAR(3);

ALTER TABLE tenants  ALTER COLUMN default_currency TYPE VARCHAR(3);
