-- Razao contabil em partidas dobradas.

CREATE TABLE ledger_accounts (
    account_number VARCHAR(30)   PRIMARY KEY,
    owner_name     VARCHAR(120)  NOT NULL,
    currency       VARCHAR(3)    NOT NULL,
    balance        NUMERIC(19,2) NOT NULL DEFAULT 0,
    status         VARCHAR(20)   NOT NULL,
    version        BIGINT        NOT NULL DEFAULT 0,
    created_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_ledger_accounts_status  CHECK (status IN ('ACTIVE','BLOCKED','CLOSED')),
    -- Invariante do dominio: nenhuma conta fica negativa.
    CONSTRAINT ck_ledger_accounts_balance CHECK (balance >= 0)
);

CREATE TABLE ledger_entries (
    id             UUID          PRIMARY KEY,
    transaction_id UUID          NOT NULL,
    payment_id     UUID          NOT NULL,
    account_number VARCHAR(30)   NOT NULL,
    direction      VARCHAR(10)   NOT NULL,
    amount         NUMERIC(19,2) NOT NULL,
    balance_after  NUMERIC(19,2) NOT NULL,
    currency       VARCHAR(3)    NOT NULL,
    description    VARCHAR(255),
    created_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_ledger_entries_account FOREIGN KEY (account_number)
        REFERENCES ledger_accounts (account_number),
    CONSTRAINT ck_ledger_entries_direction CHECK (direction IN ('DEBIT','CREDIT')),
    CONSTRAINT ck_ledger_entries_amount    CHECK (amount > 0),
    -- Idempotencia da liquidacao: um pagamento gera no maximo uma partida por
    -- conta. Uma reentrega do evento esbarra aqui antes de mover dinheiro.
    CONSTRAINT uk_ledger_entries_payment_account UNIQUE (payment_id, account_number)
);

CREATE INDEX idx_ledger_entries_account_date ON ledger_entries (account_number, created_at DESC);
CREATE INDEX idx_ledger_entries_transaction  ON ledger_entries (transaction_id);
CREATE INDEX idx_ledger_entries_payment      ON ledger_entries (payment_id);
