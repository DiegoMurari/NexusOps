-- NexusOps Ticketing - Fase C do fluxo de atendimento (ADR-013): catálogo, filas e membros.
-- Fila = equipe operacional. Responsável (tickets.assignee_id) é individual e independente da fila.
-- Área do catálogo = rótulo de exibição do Portal; cada tópico aponta para uma fila padrão interna.

CREATE TABLE ticketing.queues (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    name VARCHAR(120) NOT NULL,
    code VARCHAR(40) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);
CREATE UNIQUE INDEX uq_queues_tenant_code ON ticketing.queues (tenant_id, UPPER(code));
CREATE UNIQUE INDEX uq_queues_tenant_name ON ticketing.queues (tenant_id, LOWER(name));

CREATE TABLE ticketing.queue_members (
    id VARCHAR(36) PRIMARY KEY,
    queue_id VARCHAR(36) NOT NULL REFERENCES ticketing.queues(id) ON DELETE CASCADE,
    user_id VARCHAR(36) NOT NULL,
    member_role VARCHAR(10) NOT NULL DEFAULT 'MEMBER',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_queue_member UNIQUE (queue_id, user_id),
    CONSTRAINT chk_queue_member_role CHECK (member_role IN ('MEMBER', 'LEAD'))
);
CREATE INDEX idx_queue_members_user ON ticketing.queue_members (user_id);

CREATE TABLE ticketing.catalog_areas (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    icon VARCHAR(60),
    sort_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);
CREATE UNIQUE INDEX uq_catalog_areas_tenant_name ON ticketing.catalog_areas (tenant_id, LOWER(name));

CREATE TABLE ticketing.catalog_topics (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    area_id VARCHAR(36) NOT NULL REFERENCES ticketing.catalog_areas(id),
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000),
    default_queue_id VARCHAR(36) REFERENCES ticketing.queues(id),
    default_priority VARCHAR(20),
    sla_definition_id VARCHAR(36),
    category_id VARCHAR(36),
    sort_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);
CREATE UNIQUE INDEX uq_catalog_topics_area_name ON ticketing.catalog_topics (area_id, LOWER(name));
CREATE INDEX idx_catalog_topics_queue ON ticketing.catalog_topics (default_queue_id);
