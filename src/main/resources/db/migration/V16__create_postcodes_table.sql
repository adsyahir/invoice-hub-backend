-- Postcodes within a city. A postcode is unique per city (the same 5-digit code
-- can appear under more than one city), so the key is (city_id, code).
CREATE TABLE postcodes (
    id      SERIAL PRIMARY KEY,
    city_id INTEGER NOT NULL REFERENCES cities(id) ON DELETE CASCADE,
    code    VARCHAR(5) NOT NULL,
    UNIQUE (city_id, code)
);

CREATE INDEX idx_postcodes_city_id ON postcodes(city_id);
CREATE INDEX idx_postcodes_code    ON postcodes(code);
