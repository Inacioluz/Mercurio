package com.inacio.mercurio.payment.api;

import com.inacio.mercurio.payment.api.dto.CreatePaymentRequest;
import com.inacio.mercurio.payment.api.dto.PageResponse;
import com.inacio.mercurio.payment.api.dto.PaymentResponse;
import com.inacio.mercurio.payment.domain.PaymentStatus;
import com.inacio.mercurio.payment.exception.ErrorResponse;
import com.inacio.mercurio.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;
import java.util.function.Function;

@Tag(name = "Pagamentos", description = "Solicitacao e acompanhamento de pagamentos")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Validated
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(
            summary = "Solicita um pagamento",
            description = """
                    Aceita o pagamento e devolve **202 Accepted** — quando a resposta volta, o
                    pagamento existe com status `PENDING`, mas ainda nao foi analisado nem
                    liquidado. Acompanhe pelo `GET /api/v1/payments/{id}`.

                    ### Idempotencia
                    O header `Idempotency-Key` e obrigatorio. Reenviar a mesma chave com o mesmo
                    corpo devolve **200** com o pagamento original, sem criar outro — util quando
                    a rede cai e o cliente nao sabe se a primeira tentativa chegou.
                    Reenviar a mesma chave com um corpo diferente devolve **409**.

                    ### O que acontece depois
                    1. `payment.requested` -> antifraude analisa o risco
                    2. `payment.approved` ou `payment.rejected`
                    3. Aprovado, o razao liquida: `payment.settled` ou `payment.failed`
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Pagamento aceito e em processamento",
                    headers = @Header(name = "Location", description = "URI para acompanhar o pagamento",
                            schema = @Schema(type = "string")),
                    content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "200", description = "Chave de idempotencia repetida: devolve o pagamento original",
                    content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Campos invalidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Chave de idempotencia reusada com outro conteudo",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "timestamp": "2026-08-26T13:45:30Z",
                                      "status": 409,
                                      "error": "IDEMPOTENCY_KEY_REUSED",
                                      "message": "A chave de idempotencia 'pedido-8821' ja foi usada com outro conteudo",
                                      "path": "/api/v1/payments"
                                    }
                                    """))),
            @ApiResponse(responseCode = "422", description = "Origem igual ao destino",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "timestamp": "2026-08-26T13:45:30Z",
                                      "status": 422,
                                      "error": "SAME_ACCOUNT_PAYMENT",
                                      "message": "A conta de origem e destino devem ser diferentes",
                                      "path": "/api/v1/payments"
                                    }
                                    """)))
    })
    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @Parameter(description = "Chave unica da tentativa, escolhida pelo cliente",
                    example = "pedido-8821", required = true)
            @RequestHeader("Idempotency-Key")
            @NotBlank(message = "O header Idempotency-Key e obrigatorio")
            @Size(max = 100, message = "A chave deve ter no maximo 100 caracteres") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {

        PaymentService.CreationResult result = paymentService.create(idempotencyKey.trim(), request);

        if (result.replayed()) {
            return ResponseEntity.ok(result.payment());
        }
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .location(UriComponentsBuilder.fromPath("/api/v1/payments/{id}")
                        .buildAndExpand(result.payment().id()).toUri())
                .body(result.payment());
    }

    @Operation(
            summary = "Consulta um pagamento",
            description = "Estado atual na saga, incluindo pontuacao de risco e motivo da falha, quando houver.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagamento encontrado",
                    content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "404", description = "Pagamento nao encontrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> findById(
            @Parameter(description = "Identificador do pagamento", example = "3f1a9c20-5b8d-4e11-9f3e-77d5a1c2b3e4")
            @PathVariable UUID paymentId) {
        return ResponseEntity.ok(paymentService.findById(paymentId));
    }

    @Operation(
            summary = "Lista pagamentos",
            description = "Filtra por status ou por conta (origem ou destino). Sem filtro, lista tudo.")
    @ApiResponse(responseCode = "200", description = "Pagina de pagamentos",
            content = @Content(schema = @Schema(implementation = PageResponse.class)))
    @GetMapping
    public ResponseEntity<PageResponse<PaymentResponse>> list(
            @Parameter(description = "Filtra por status", example = "SETTLED")
            @RequestParam(required = false) PaymentStatus status,
            @Parameter(description = "Filtra por conta, de origem ou destino", example = "ACC-1001")
            @RequestParam(required = false) String account,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return ResponseEntity.ok(PageResponse.of(
                paymentService.list(status, account, pageable), Function.identity()));
    }
}
