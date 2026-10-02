-- NexusOps Ticketing - Fase D (ADR-013): formulários dinâmicos e versionados por tópico do catálogo.
-- Cada tópico tem versões do formulário (rascunho -> publicado -> arquivado). O chamado guarda a versão
-- exata com que foi aberto e as respostas validadas pelo servidor, então republicar nunca reescreve o passado.

CREATE TABLE ticketing.topic_form_versions (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    topic_id VARCHAR(36) NOT NULL REFERENCES ticketing.catalog_topics(id),
    version INTEGER NOT NULL,
    status VARCHAR(12) NOT NULL,
    definition JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published_at TIMESTAMP WITH TIME ZONE,
    published_by VARCHAR(255),
    CONSTRAINT uq_topic_form_version UNIQUE (topic_id, version),
    CONSTRAINT chk_topic_form_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);
-- No máximo um rascunho e uma versão publicada por tópico.
CREATE UNIQUE INDEX uq_topic_form_one_draft ON ticketing.topic_form_versions (topic_id) WHERE status = 'DRAFT';
CREATE UNIQUE INDEX uq_topic_form_one_published ON ticketing.topic_form_versions (topic_id) WHERE status = 'PUBLISHED';

ALTER TABLE ticketing.tickets
    ADD COLUMN form_version_id VARCHAR(36) REFERENCES ticketing.topic_form_versions(id),
    ADD COLUMN form_answers JSONB;
CREATE INDEX idx_tickets_form_version ON ticketing.tickets (form_version_id);
