-- Base de conhecimento: vínculo artigo <-> ticket ("este artigo ajudou a resolver este chamado").
-- O ticket é apenas referenciado por id; quem valida que ele existe no tenant é o diretório de tickets.
CREATE TABLE knowledge.article_ticket_links (
    id        VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    article_id VARCHAR(36) NOT NULL REFERENCES knowledge.articles (id) ON DELETE CASCADE,
    ticket_id VARCHAR(36) NOT NULL,
    linked_by VARCHAR(255),
    linked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_article_ticket UNIQUE (article_id, ticket_id)
);
CREATE INDEX idx_article_ticket_links_ticket ON knowledge.article_ticket_links (tenant_id, ticket_id);
