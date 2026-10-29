-- Descoberta pelo Flyway somente no perfil demo.
CREATE TABLE IF NOT EXISTS demo_seed (
    id integer PRIMARY KEY CHECK (id = 1),
    seeded_at timestamptz NOT NULL
);
