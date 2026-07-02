-- Cities/towns within a state. A city name is only unique within its state
-- (e.g. "Ayer Hitam" exists in both Johor and Kedah), hence the composite key.
CREATE TABLE cities (
    id       SERIAL PRIMARY KEY,
    state_id INTEGER NOT NULL REFERENCES states(id) ON DELETE CASCADE,
    name     VARCHAR(100) NOT NULL,
    UNIQUE (state_id, name)
);

CREATE INDEX idx_cities_state_id ON cities(state_id);
