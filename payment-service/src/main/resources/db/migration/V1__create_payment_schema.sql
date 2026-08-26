-- Schema do payment-service: pagamentos, outbox e deduplicacao de eventos.

CREATE TABLE payments (
    id              UUID          PRIMARY KEY,
    idempotency_key VARCHAR(100)  NOT NULL,
    payer_account   VARCHAR(30)   NOT NULL,
    payee_account   VARCHAR(30)   NOT NULL,
    amount          NUMERIC(19,2) NOT NULL,
    currency        VARCHAR(3)    NOT NULL,
    description     VARCHAR(255),
    status          VARCHAR(20)   NOT NULL,
    failure_reason  VARCHAR(500),
    risk_score      INTEGER,
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    -- A garantia real da idempotencia: o Redis e so um atalho de leitura.
    CONSTRAINT uk_payments_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_payments_status      CHECK (status IN ('PENDING','APPROVED','REJECTED','SETTLED','FAILED')),
    CONSTRAINT ck_payments_amount      CHECK (amount > 0),
    CONSTRAINT ck_payments_accounts    CHECK (payer_account <> payee_account)
);

CREATE INDEX idx_payments_status  ON payments (status);
CREATE INDEX idx_payments_payer   ON payments (payer_account);
CREATE INDEX idx_payments_payee   ON payments (payee_account);
CREATE INDEX idx_payments_created ON payments (created_at DESC);

-- Eventos gravados na mesma transacao do fato que os originou.
CREATE TABLE outbox_events (
    id           UUID         PRIMARY KEY,
    topic        VARCHAR(100) NOT NULL,
    message_key  VARCHAR(100) NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP(6) WITH TIME ZONE,
    attempts     INTEGER      NOT NULL DEFAULT 0,
    last_error   VARCHAR(500)
);

-- Indice parcial: o relay so consulta pendentes, entao indexar os ja
-- publicados seria peso morto numa tabela de alta rotatividade.
CREATE INDEX idx_outbox_pending ON outbox_events (created_at) WHERE published_at IS NULL;

-- Deduplicacao: a chave primaria e o proprio eventId.
CREATE TABLE processed_events (
    event_id     UUID         PRIMARY KEY,
    topic        VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_processed_events_at ON processed_events (processed_at);
