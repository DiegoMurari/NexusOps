-- ADR-013 fase B: perfil do usuário. A localidade padrão alimenta o roteamento (condição de primeira
-- classe) e o pré-preenchimento do chamado. Guarda só o ID: a localidade pertence ao módulo asset e é
-- validada pela porta LocationDirectory, sem acoplar os módulos.
ALTER TABLE iam.users ADD COLUMN IF NOT EXISTS job_title VARCHAR(100);
ALTER TABLE iam.users ADD COLUMN IF NOT EXISTS department VARCHAR(100);
ALTER TABLE iam.users ADD COLUMN IF NOT EXISTS default_location_id VARCHAR(36);

CREATE INDEX IF NOT EXISTS idx_users_default_location ON iam.users(default_location_id);
