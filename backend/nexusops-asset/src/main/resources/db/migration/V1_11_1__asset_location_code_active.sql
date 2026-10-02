-- ADR-013 fase B: localidade como cidadã de primeira classe. O código é estável e usado por regras de
-- roteamento (ex.: FRANCA); "active" permite aposentar uma localidade sem apagar o que a referencia.
ALTER TABLE asset.locations ADD COLUMN IF NOT EXISTS code VARCHAR(50);
ALTER TABLE asset.locations ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE UNIQUE INDEX IF NOT EXISTS uk_locations_tenant_code
    ON asset.locations (tenant_id, upper(code))
    WHERE code IS NOT NULL;
