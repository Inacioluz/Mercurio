package com.inacio.mercurio.ledger.domain;

/**
 * Lado da partida. Na convencao aqui adotada, {@code DEBIT} tira valor da conta
 * e {@code CREDIT} acrescenta — a leitura do ponto de vista do titular, nao do
 * banco.
 */
public enum EntryDirection {
    DEBIT,
    CREDIT
}
