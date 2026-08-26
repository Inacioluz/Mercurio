package com.inacio.mercurio.payment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "CreatePaymentRequest", description = "Solicitacao de pagamento entre duas contas")
public record CreatePaymentRequest(

        @Schema(description = "Conta de origem", example = "ACC-1001", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "A conta de origem e obrigatoria")
        @Size(max = 30, message = "A conta deve ter no maximo 30 caracteres")
        String payerAccount,

        @Schema(description = "Conta de destino", example = "ACC-2002", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "A conta de destino e obrigatoria")
        @Size(max = 30, message = "A conta deve ter no maximo 30 caracteres")
        String payeeAccount,

        @Schema(description = "Valor a transferir", example = "150.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O valor e obrigatorio")
        @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
        @Digits(integer = 17, fraction = 2, message = "Valor monetario invalido")
        BigDecimal amount,

        @Schema(description = "Moeda no padrao ISO-4217", example = "BRL", defaultValue = "BRL")
        @Pattern(regexp = "[A-Z]{3}", message = "A moeda deve ter 3 letras maiusculas")
        String currency,

        @Schema(description = "Descricao livre", example = "Pagamento do pedido 8821")
        @Size(max = 255, message = "A descricao deve ter no maximo 255 caracteres")
        String description
) {

    public String currencyOrDefault() {
        return currency == null || currency.isBlank() ? "BRL" : currency;
    }
}
