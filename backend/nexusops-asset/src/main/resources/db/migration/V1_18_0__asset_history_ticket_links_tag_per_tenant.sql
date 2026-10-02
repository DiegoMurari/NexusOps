-- Ativos: histórico de alterações, vínculo com tickets e etiqueta única por tenant.

-- 1) A etiqueta (asset_tag) era única no banco inteiro: um tenant usando "NB-001" impedia todos os outros
--    e a colisão virava erro 500. A unicidade correta é por tenant (o serviço já checava assim).
ALTER TABLE asset.assets DROP CONSTRAINT IF EXISTS assets_asset_tag_key;
CREATE UNIQUE INDEX IF NOT EXISTS uk_assets_tenant_tag ON asset.assets (tenant_id, asset_tag);

-- 2) Histórico: uma linha por campo alterado (ou por evento: criação, vínculo com ticket). Nunca é editado.
--    "actor" guarda o principal autenticado (e-mail), como o restante do sistema.
CREATE TABLE asset.asset_history (
    id         VARCHAR(36)  PRIMARY KEY,
    tenant_id  VARCHAR(36)  NOT NULL,
    asset_id   VARCHAR(36)  NOT NULL REFERENCES asset.assets (id) ON DELETE CASCADE,
    event_type VARCHAR(30)  NOT NULL,
    field_name VARCHAR(60),
    old_value  VARCHAR(500),
    new_value  VARCHAR(500),
    actor      VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_asset_history_asset ON asset.asset_history (tenant_id, asset_id, created_at DESC);

-- 3) Vínculo ativo <-> ticket (o ticket é apenas referenciado por id; quem valida é o diretório de tickets).
CREATE TABLE asset.asset_ticket_links (
    id        VARCHAR(36)  PRIMARY KEY,
    tenant_id VARCHAR(36)  NOT NULL,
    asset_id  VARCHAR(36)  NOT NULL REFERENCES asset.assets (id) ON DELETE CASCADE,
    ticket_id VARCHAR(36)  NOT NULL,
    linked_by VARCHAR(255),
    linked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_asset_ticket UNIQUE (asset_id, ticket_id)
);
CREATE INDEX idx_asset_ticket_links_ticket ON asset.asset_ticket_links (tenant_id, ticket_id);
