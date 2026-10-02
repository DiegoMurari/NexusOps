-- Fase F (ADR-013): evidência ligada a um ponto do histórico, não solta no chamado.
-- Um anexo pertence ao chamado inteiro (TICKET), a um comentário (COMMENT) ou a um evento da timeline (EVENT,
-- que cobre as etapas: resolução, pedido de informação, retomada...). Anexo sem chamado ainda (ticket_id nulo) é
-- "preparado": foi enviado no formulário de abertura e ainda não foi confirmado junto com o chamado.
ALTER TABLE ticketing.attachments ALTER COLUMN ticket_id DROP NOT NULL;

ALTER TABLE ticketing.attachments ADD COLUMN subject_type VARCHAR(20) NOT NULL DEFAULT 'TICKET';
ALTER TABLE ticketing.attachments ADD COLUMN subject_id VARCHAR(36);
ALTER TABLE ticketing.attachments ADD COLUMN internal BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE ticketing.attachments
    ADD CONSTRAINT chk_attachment_subject_type CHECK (subject_type IN ('TICKET', 'COMMENT', 'EVENT'));
-- Comentário e evento exigem o ID do ponto a que se ligam; o chamado inteiro, não.
ALTER TABLE ticketing.attachments
    ADD CONSTRAINT chk_attachment_subject_id CHECK (subject_type = 'TICKET' OR subject_id IS NOT NULL);

CREATE INDEX idx_attachments_subject ON ticketing.attachments(subject_type, subject_id);
-- Limpeza dos anexos preparados que ninguém confirmou.
CREATE INDEX idx_attachments_staged ON ticketing.attachments(created_at) WHERE ticket_id IS NULL;

-- Correção: estas colunas guardam o principal autenticado (o e-mail), que passa de 36 caracteres. Com VARCHAR(36),
-- um usuário de e-mail longo falhava ao abrir chamado, comentar ou ser atribuído. 320 é o limite de um e-mail.
ALTER TABLE ticketing.tickets ALTER COLUMN assignee_id TYPE VARCHAR(320);
ALTER TABLE ticketing.tickets ALTER COLUMN created_by TYPE VARCHAR(320);
ALTER TABLE ticketing.tickets ALTER COLUMN updated_by TYPE VARCHAR(320);
ALTER TABLE ticketing.categories ALTER COLUMN created_by TYPE VARCHAR(320);
ALTER TABLE ticketing.categories ALTER COLUMN updated_by TYPE VARCHAR(320);
ALTER TABLE ticketing.comments ALTER COLUMN author_id TYPE VARCHAR(320);
ALTER TABLE ticketing.attachments ALTER COLUMN uploader_id TYPE VARCHAR(320);
ALTER TABLE ticketing.time_entries ALTER COLUMN user_id TYPE VARCHAR(320);
ALTER TABLE ticketing.ticket_events ALTER COLUMN actor_id TYPE VARCHAR(320);
ALTER TABLE ticketing.ticket_resolutions ALTER COLUMN resolved_by TYPE VARCHAR(320);
ALTER TABLE ticketing.ticket_resolutions ALTER COLUMN decided_by TYPE VARCHAR(320);
