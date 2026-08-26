-- O container cria mercurio_payments pela variavel POSTGRES_DB; o razao precisa
-- do proprio banco. Bancos separados por servico: nenhum consegue ler as
-- tabelas do outro por atalho, o que forcaria acoplamento pela base.
CREATE DATABASE mercurio_ledger OWNER mercurio;
