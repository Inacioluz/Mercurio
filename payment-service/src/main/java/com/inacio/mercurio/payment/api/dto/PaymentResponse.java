package com.inacio.mercurio.payment.api.dto;

import com.inacio.mercurio.payment.domain.Payment;
import com.inacio.mercurio.payment.domain.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "PaymentResponse", description = "Estado atual de um pagamento na saga")
public record PaymentResponse(

        @Schema(description = "Identificador do pagamento", example = "3f1a9c20-5b8d-4e11-9f3e-77d5a1c2b3e4")
        UUID id,

        @Schema(description = "Conta de origem", example = "ACC-1001")
        String payerAccount,

        @Schema(description = "Conta de destino", example = "ACC-2002")
        String payeeAccount,

        @Schema(description = "Valor", example = "150.00")
        BigDecimal amount,

        @Schema(description = "Moeda", example = "BRL")
        String currency,

        @Schema(description = "Descricao", example = "Pagamento do pedido 8821", nullable = true)
        String description,

        @Schema(description = """
                Estado na saga. `PENDING` aguarda antifraude; `APPROVED` aguarda liquidacao;
                `REJECTED`, `SETTLED` e `FAILED` sao terminais.
                """, example = "PENDING")
        PaymentStatus status,

        @Schema(description = "Pontuacao de risco de 0 a 100, preenchida pelo antifraude", example = "18", nullable = true)
        Integer riskScore,

        @Schema(description = "Motivo, quando o pagamento termina em REJECTED ou FAILED", nullable = true,
                example = "Saldo insuficiente para concluir a operacao")
        String failureReason,

        @Schema(description = "Momento da solicitacao", example = "2026-08-26T13:45:30Z")
        Instant createdAt,

        @Schema(description = "Momento da ultima mudanca de estado", example = "2026-08-26T13:45:31Z")
        Instant updatedAt
) implements Serializable {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getPayerAccount(),
                payment.getPayeeAccount(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getDescription(),
                payment.getStatus(),
                payment.getRiskScore(),
                payment.getFailureReason(),
                payment.getCreatedAt(),
                payment.getUpdatedAt());
    }
}
