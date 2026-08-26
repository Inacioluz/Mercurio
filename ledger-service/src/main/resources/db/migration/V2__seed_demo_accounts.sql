-- Contas de demonstracao citadas na documentacao.
-- ACC-9999 fica inativa de proposito, para exercitar o caminho de falha.

INSERT INTO ledger_accounts (account_number, owner_name, currency, balance, status, version, created_at, updated_at)
VALUES
    ('ACC-1001', 'Maria Souza',        'BRL', 10000.00, 'ACTIVE',  0, now(), now()),
    ('ACC-1002', 'Joao Pereira',       'BRL',  7500.00, 'ACTIVE',  0, now(), now()),
    ('ACC-2002', 'Loja do Bairro LTDA','BRL',  1200.00, 'ACTIVE',  0, now(), now()),
    ('ACC-2003', 'Servicos Tech ME',   'BRL',   300.50, 'ACTIVE',  0, now(), now()),
    ('ACC-9999', 'Conta Encerrada',    'BRL',     0.00, 'BLOCKED', 0, now(), now())
ON CONFLICT (account_number) DO NOTHING;
