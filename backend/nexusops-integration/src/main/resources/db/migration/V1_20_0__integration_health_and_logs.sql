-- Integrações: estado de saúde (última entrega / última verificação), liga/desliga de conectores e log de atividade.
-- O log guarda só o que aconteceu e o resultado; nunca segredos, corpo de requisição nem URL completa.

ALTER TABLE integration.webhooks
    ADD COLUMN last_delivery_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN last_delivery_status VARCHAR(20),
    ADD COLUMN last_delivery_http_status INTEGER,
    ADD COLUMN last_error VARCHAR(255);

ALTER TABLE integration.connectors
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN last_check_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN last_check_status VARCHAR(20),
    ADD COLUMN last_check_message VARCHAR(255);

CREATE TABLE integration.integration_logs (
    id               VARCHAR(36) PRIMARY KEY,
    tenant_id        VARCHAR(36)  NOT NULL,
    integration_kind VARCHAR(20)  NOT NULL,
    integration_id   VARCHAR(36)  NOT NULL,
    integration_name VARCHAR(255) NOT NULL,
    event            VARCHAR(20)  NOT NULL,
    outcome          VARCHAR(20)  NOT NULL,
    http_status      INTEGER,
    duration_ms      BIGINT,
    message          VARCHAR(500),
    actor            VARCHAR(255),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_integration_logs_tenant_created ON integration.integration_logs (tenant_id, created_at DESC);
CREATE INDEX idx_integration_logs_integration ON integration.integration_logs (tenant_id, integration_id);
