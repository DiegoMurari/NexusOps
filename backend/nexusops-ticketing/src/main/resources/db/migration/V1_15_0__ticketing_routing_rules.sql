-- NexusOps Ticketing - Fase E (ADR-013): regras de roteamento configuráveis.
-- Avaliadas em ordem (position) na abertura do chamado; vale a primeira que casar. Sem regra, valem os
-- padrões do tópico. Condições (todas precisam casar) e ações ficam em JSON validado pelo serviço.

CREATE TABLE ticketing.routing_rules (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(500),
    position INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    conditions JSONB NOT NULL DEFAULT '[]'::jsonb,
    actions JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);
CREATE UNIQUE INDEX uq_routing_rules_tenant_name ON ticketing.routing_rules (tenant_id, LOWER(name));
CREATE INDEX idx_routing_rules_order ON ticketing.routing_rules (tenant_id, position);
