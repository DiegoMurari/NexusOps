-- NexusOps Ticketing - Fase A do fluxo de atendimento (ADR-013)
-- Ciclos de atendimento, timeline imutável (append-only) e resoluções imutáveis.

ALTER TABLE ticketing.tickets
    ADD COLUMN cycle_no INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN reopen_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN queue_id VARCHAR(36),
    ADD COLUMN location_id VARCHAR(36),
    ADD COLUMN topic_id VARCHAR(36);

CREATE INDEX idx_tickets_queue ON ticketing.tickets(queue_id);
CREATE INDEX idx_tickets_location ON ticketing.tickets(location_id);
CREATE INDEX idx_tickets_topic ON ticketing.tickets(topic_id);

-- ---------------------------------------------------------------------------
-- Timeline: append-only. Nenhum UPDATE nem DELETE, imposto pelo banco.
-- ---------------------------------------------------------------------------
CREATE TABLE ticketing.ticket_events (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    ticket_id VARCHAR(36) NOT NULL REFERENCES ticketing.tickets(id),
    tenant_id VARCHAR(36) NOT NULL,
    seq INTEGER NOT NULL,
    cycle_no INTEGER NOT NULL DEFAULT 1,
    event_type VARCHAR(40) NOT NULL,
    actor_id VARCHAR(36),
    actor_kind VARCHAR(10) NOT NULL DEFAULT 'USER',
    visibility VARCHAR(10) NOT NULL DEFAULT 'PUBLIC',
    payload JSONB,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_ticket_events_seq UNIQUE (ticket_id, seq),
    CONSTRAINT ck_ticket_events_actor_kind CHECK (actor_kind IN ('USER', 'SYSTEM', 'RULE')),
    CONSTRAINT ck_ticket_events_visibility CHECK (visibility IN ('PUBLIC', 'INTERNAL'))
);

CREATE INDEX idx_ticket_events_ticket ON ticketing.ticket_events(ticket_id, seq);
CREATE INDEX idx_ticket_events_tenant ON ticketing.ticket_events(tenant_id);
CREATE INDEX idx_ticket_events_type ON ticketing.ticket_events(event_type);

CREATE FUNCTION ticketing.reject_ticket_event_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ticket_events is append-only (% rejected)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ticket_events_immutable
    BEFORE UPDATE OR DELETE ON ticketing.ticket_events
    FOR EACH ROW EXECUTE FUNCTION ticketing.reject_ticket_event_change();

-- ---------------------------------------------------------------------------
-- Ciclos de atendimento: o SLA é medido por ciclo; nada é sobrescrito.
-- Ciclo 1 = abertura até a primeira resolução. Cada reabertura abre um novo ciclo.
-- ---------------------------------------------------------------------------
CREATE TABLE ticketing.ticket_cycles (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    ticket_id VARCHAR(36) NOT NULL REFERENCES ticketing.tickets(id),
    tenant_id VARCHAR(36) NOT NULL,
    cycle_no INTEGER NOT NULL,
    opened_reason VARCHAR(20) NOT NULL,
    reopen_comment TEXT,
    status VARCHAR(24) NOT NULL DEFAULT 'IN_PROGRESS',
    sla_definition_id VARCHAR(36),
    response_due_at TIMESTAMP WITH TIME ZONE,
    resolution_due_at TIMESTAMP WITH TIME ZONE,
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    first_response_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    validated_at TIMESTAMP WITH TIME ZONE,
    paused_seconds BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_ticket_cycles UNIQUE (ticket_id, cycle_no),
    CONSTRAINT ck_ticket_cycles_reason CHECK (opened_reason IN ('CREATED', 'REOPENED')),
    CONSTRAINT ck_ticket_cycles_status CHECK (status IN ('IN_PROGRESS', 'AWAITING_VALIDATION', 'ACCEPTED', 'CONTESTED'))
);

CREATE INDEX idx_ticket_cycles_ticket ON ticketing.ticket_cycles(ticket_id, cycle_no);
CREATE INDEX idx_ticket_cycles_tenant ON ticketing.ticket_cycles(tenant_id);

CREATE FUNCTION ticketing.reject_ticket_cycle_delete() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ticket_cycles rows are history and cannot be deleted';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ticket_cycles_no_delete
    BEFORE DELETE ON ticketing.ticket_cycles
    FOR EACH ROW EXECUTE FUNCTION ticketing.reject_ticket_cycle_delete();

-- ---------------------------------------------------------------------------
-- Resoluções: a solução apresentada é imutável; só o desfecho é decidido uma vez.
-- ---------------------------------------------------------------------------
CREATE TABLE ticketing.ticket_resolutions (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    ticket_id VARCHAR(36) NOT NULL REFERENCES ticketing.tickets(id),
    tenant_id VARCHAR(36) NOT NULL,
    cycle_no INTEGER NOT NULL,
    solution_text TEXT NOT NULL,
    resolved_by VARCHAR(36) NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    outcome VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    decided_by VARCHAR(36),
    decided_at TIMESTAMP WITH TIME ZONE,
    decision_comment TEXT,
    CONSTRAINT uk_ticket_resolutions UNIQUE (ticket_id, cycle_no),
    CONSTRAINT ck_ticket_resolutions_outcome CHECK (outcome IN ('PENDING', 'ACCEPTED', 'CONTESTED', 'AUTO_ACCEPTED'))
);

CREATE INDEX idx_ticket_resolutions_ticket ON ticketing.ticket_resolutions(ticket_id, cycle_no);
CREATE INDEX idx_ticket_resolutions_tenant ON ticketing.ticket_resolutions(tenant_id);

CREATE FUNCTION ticketing.guard_ticket_resolution() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'ticket_resolutions rows are history and cannot be deleted';
    END IF;
    IF NEW.ticket_id <> OLD.ticket_id OR NEW.cycle_no <> OLD.cycle_no
       OR NEW.solution_text <> OLD.solution_text
       OR NEW.resolved_by <> OLD.resolved_by OR NEW.resolved_at <> OLD.resolved_at THEN
        RAISE EXCEPTION 'the presented solution is immutable';
    END IF;
    IF OLD.outcome <> 'PENDING' THEN
        RAISE EXCEPTION 'the outcome of a resolution is decided only once';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ticket_resolutions_guard
    BEFORE UPDATE OR DELETE ON ticketing.ticket_resolutions
    FOR EACH ROW EXECUTE FUNCTION ticketing.guard_ticket_resolution();

-- ---------------------------------------------------------------------------
-- Backfill dos chamados existentes: ciclo 1 e evento CREATED.
-- ---------------------------------------------------------------------------
INSERT INTO ticketing.ticket_cycles (ticket_id, tenant_id, cycle_no, opened_reason, status, sla_definition_id,
                                     response_due_at, resolution_due_at, opened_at, first_response_at, resolved_at, validated_at)
SELECT t.id, t.tenant_id, 1, 'CREATED',
       CASE t.status WHEN 'RESOLVED' THEN 'AWAITING_VALIDATION' WHEN 'CLOSED' THEN 'ACCEPTED' ELSE 'IN_PROGRESS' END,
       t.sla_definition_id, t.response_due_at, t.resolution_due_at, t.created_at, t.first_response_at, t.resolved_at,
       t.closed_at
FROM ticketing.tickets t;

INSERT INTO ticketing.ticket_events (ticket_id, tenant_id, seq, cycle_no, event_type, actor_id, actor_kind, visibility, payload, occurred_at)
SELECT t.id, t.tenant_id, 1, 1, 'CREATED', COALESCE(t.created_by, t.reporter_id), 'USER', 'PUBLIC',
       '{"backfilled": true}'::jsonb, t.created_at
FROM ticketing.tickets t;
