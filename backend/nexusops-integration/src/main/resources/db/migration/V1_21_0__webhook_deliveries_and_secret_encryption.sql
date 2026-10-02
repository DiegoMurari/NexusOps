-- Entrega automática de eventos aos webhooks (outbox com reentrega) e segredo do webhook criptografado em repouso.

-- AES-GCM + base64 + prefixo "enc:v1:" de um segredo de até 255 caracteres cabe em 512.
ALTER TABLE integration.webhooks ALTER COLUMN secret TYPE VARCHAR(512);

CREATE TABLE integration.webhook_deliveries (
    id                VARCHAR(36) PRIMARY KEY,
    tenant_id         VARCHAR(36)  NOT NULL,
    webhook_id        VARCHAR(36)  NOT NULL REFERENCES integration.webhooks (id) ON DELETE CASCADE,
    event_type        VARCHAR(100) NOT NULL,
    event_id          VARCHAR(36)  NOT NULL,
    payload           TEXT         NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts          INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_http_status  INTEGER,
    last_error        VARCHAR(255),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    delivered_at      TIMESTAMP WITH TIME ZONE,
    -- o mesmo evento nunca é enfileirado duas vezes para o mesmo webhook (idempotência)
    CONSTRAINT uk_webhook_delivery_event UNIQUE (webhook_id, event_id)
);

CREATE INDEX idx_webhook_deliveries_due ON integration.webhook_deliveries (status, next_attempt_at);
CREATE INDEX idx_webhook_deliveries_tenant ON integration.webhook_deliveries (tenant_id, created_at DESC);
