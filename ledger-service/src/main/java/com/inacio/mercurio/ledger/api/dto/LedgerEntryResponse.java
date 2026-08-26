package com.inacio.mercurio.ledger.api.dto;

import com.inacio.mercurio.ledger.domain.EntryDirection;
import com.inacio.mercurio.ledger.domain.LedgerEntry;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "LedgerEntryResponse", description = "Partida do razao")
public record LedgerEntryResponse(

        @Schema(description = "Identificador da partida") UUID id,
        @Schema(description = "Agrupa as duas partidas da mesma movimentacao") UUID transactionId,
        @Schema(description = "Pagamento que originou o lancamento") UUID paymentId,
        @Schema(description = "Conta movimentada", example = "ACC-1001") String accountNumber,
        @Schema(description = "DEBIT tira, CREDIT acrescenta", example = "DEBIT") EntryDirection direction,
        @Schema(description = "Valor, sempre positivo", example = "150.00") BigDecimal amount,
        @Schema(description = "Saldo apos a partida", example = "8350.00") BigDecimal balanceAfter,
        @Schema(description = "Moeda", example = "BRL") String currency,
        @Schema(description = "Momento do lancamento", example = "2026-08-26T13:45:30Z") Instant createdAt
) {

    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(), entry.getTransactionId(), entry.getPaymentId(), entry.getAccountNumber(),
                entry.getDirection(), entry.getAmount(), entry.getBalanceAfter(),
                entry.getCurrency(), entry.getCreatedAt());
    }
}
