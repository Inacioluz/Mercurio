package com.inacio.mercurio.ledger.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "IntegrityReport", description = "Conferencia da integridade do razao")
public record IntegrityReport(

        @Schema(description = """
                Soma com sinal de todas as partidas. Em partidas dobradas tem de ser
                exatamente zero — outro valor denuncia lancamento desbalanceado.
                """, example = "0.00")
        BigDecimal signedEntrySum,

        @Schema(description = "Soma dos saldos de todas as contas", example = "31280.50")
        BigDecimal totalAccountBalance,

        @Schema(description = "Verdadeiro quando a soma das partidas e zero", example = "true")
        boolean balanced,

        @Schema(description = "Momento da conferencia", example = "2026-08-26T13:45:30Z")
        Instant checkedAt
) {

    public static IntegrityReport of(BigDecimal signedSum, BigDecimal totalBalance) {
        return new IntegrityReport(signedSum, totalBalance,
                signedSum.compareTo(BigDecimal.ZERO) == 0, Instant.now());
    }
}
