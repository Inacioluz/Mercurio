package com.inacio.mercurio.ledger.api.dto;

import com.inacio.mercurio.ledger.domain.Account;
import com.inacio.mercurio.ledger.domain.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "AccountBalanceResponse", description = "Saldo de uma conta do razao")
public record AccountBalanceResponse(

        @Schema(description = "Numero da conta", example = "ACC-1001") String accountNumber,
        @Schema(description = "Titular", example = "Maria Souza") String ownerName,
        @Schema(description = "Saldo atual", example = "8500.00") BigDecimal balance,
        @Schema(description = "Moeda", example = "BRL") String currency,
        @Schema(description = "Situacao", example = "ACTIVE") AccountStatus status,
        @Schema(description = "Momento da apuracao", example = "2026-08-26T13:45:30Z") Instant retrievedAt
) implements Serializable {

    public static AccountBalanceResponse from(Account account) {
        return new AccountBalanceResponse(
                account.getAccountNumber(), account.getOwnerName(), account.getBalance(),
                account.getCurrency(), account.getStatus(), Instant.now());
    }
}
