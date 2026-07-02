-- Replace the free-text city/state on clients with FKs into the geo lookup
-- tables. address_line1 and country stay as plain text.
ALTER TABLE clients DROP COLUMN IF EXISTS city;
ALTER TABLE clients DROP COLUMN IF EXISTS state;

ALTER TABLE clients
    ADD COLUMN state_id    INTEGER REFERENCES states(id),
    ADD COLUMN city_id     INTEGER REFERENCES cities(id),
    ADD COLUMN postcode_id INTEGER REFERENCES postcodes(id);

CREATE INDEX idx_clients_city_id     ON clients(city_id);
CREATE INDEX idx_clients_postcode_id ON clients(postcode_id);
