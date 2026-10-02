-- SLA aplicado de verdade: o relógio pausa quando o chamado fica em espera ou aguardando o solicitante.
-- sla_paused_at guarda o início da pausa corrente (nulo = o relógio está correndo). Ao retomar, os prazos
-- do ciclo são estendidos pelo tempo pausado e o total entra em ticket_cycles.paused_seconds.
ALTER TABLE ticketing.tickets ADD COLUMN IF NOT EXISTS sla_paused_at TIMESTAMP WITH TIME ZONE;

-- Apoia o vigia de SLA, que varre os chamados em atendimento a cada minuto.
CREATE INDEX IF NOT EXISTS idx_tickets_sla_watch
    ON ticketing.tickets (status, resolution_due_at)
    WHERE status NOT IN ('RESOLVED', 'CLOSED');
