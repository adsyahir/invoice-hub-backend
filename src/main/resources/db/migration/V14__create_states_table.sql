-- Malaysian states (geo reference data). Rows loaded by GeoSeeder, not here.
CREATE TABLE states (
    id   SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);
