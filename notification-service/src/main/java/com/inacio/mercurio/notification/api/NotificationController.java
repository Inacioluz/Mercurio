package com.inacio.mercurio.notification.api;

import com.inacio.mercurio.notification.domain.Notification;
import com.inacio.mercurio.notification.repository.NotificationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Notificacoes", description = "Avisos gerados nos desfechos da saga")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;

    @Operation(
            summary = "Notificacoes de um pagamento",
            description = "Um pagamento liquidado gera dois avisos: um para quem pagou, outro para quem recebeu.")
    @ApiResponse(responseCode = "200", description = "Notificacoes do pagamento",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = Notification.class))))
    @GetMapping("/payments/{paymentId}")
    public ResponseEntity<List<Notification>> byPayment(
            @Parameter(description = "Identificador do pagamento") @PathVariable UUID paymentId) {
        return ResponseEntity.ok(notificationRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId));
    }

    @Operation(
            summary = "Lista notificacoes",
            description = "Filtra pela conta destinataria ou pelo tipo de aviso.")
    @ApiResponse(responseCode = "200", description = "Notificacoes encontradas",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = Notification.class))))
    @GetMapping
    public ResponseEntity<List<Notification>> list(
            @Parameter(description = "Conta destinataria", example = "ACC-1001")
            @RequestParam(required = false) String account,
            @Parameter(description = "Tipo do aviso", example = "PAYMENT_SETTLED")
            @RequestParam(required = false) String type,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        if (account != null && !account.isBlank()) {
            return ResponseEntity.ok(notificationRepository
                    .findByRecipientAccountOrderByCreatedAtDesc(account, pageable).getContent());
        }
        if (type != null && !type.isBlank()) {
            return ResponseEntity.ok(notificationRepository
                    .findByTypeOrderByCreatedAtDesc(type, pageable).getContent());
        }
        return ResponseEntity.ok(notificationRepository.findAll(pageable).getContent());
    }

    @Operation(summary = "Resumo por tipo de aviso")
    @ApiResponse(responseCode = "200", description = "Contagem por tipo")
    @GetMapping("/summary")
    public Map<String, Long> summary() {
        return Map.of(
                "settled", notificationRepository.countByType("PAYMENT_SETTLED"),
                "rejected", notificationRepository.countByType("PAYMENT_REJECTED"),
                "failed", notificationRepository.countByType("PAYMENT_FAILED"),
                "total", notificationRepository.count());
    }
}
