package com.inacio.mercurio.antifraud.api;

import com.inacio.mercurio.antifraud.domain.RiskAssessment;
import com.inacio.mercurio.antifraud.domain.RiskDecision;
import com.inacio.mercurio.antifraud.repository.RiskAssessmentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Analise de risco", description = "Laudos produzidos pelo antifraude")
@RestController
@RequestMapping("/api/v1/assessments")
@RequiredArgsConstructor
public class AssessmentController {

    private final RiskAssessmentRepository assessmentRepository;

    @Operation(
            summary = "Laudo de um pagamento",
            description = "Pontuacao, decisao e as regras que dispararam na analise.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Laudo encontrado",
                    content = @Content(schema = @Schema(implementation = RiskAssessment.class))),
            @ApiResponse(responseCode = "404", description = "Pagamento ainda nao avaliado")
    })
    @GetMapping("/{paymentId}")
    public RiskAssessment byPayment(
            @Parameter(description = "Identificador do pagamento") @PathVariable UUID paymentId) {
        return assessmentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Nenhum laudo para o pagamento " + paymentId));
    }

    @Operation(
            summary = "Lista laudos",
            description = "Filtra por decisao ou por conta pagadora.")
    @ApiResponse(responseCode = "200", description = "Laudos encontrados",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = RiskAssessment.class))))
    @GetMapping
    public ResponseEntity<List<RiskAssessment>> list(
            @Parameter(description = "Filtra por decisao", example = "REJECTED")
            @RequestParam(required = false) RiskDecision decision,
            @Parameter(description = "Filtra pela conta pagadora", example = "ACC-1001")
            @RequestParam(required = false) String payerAccount,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        if (decision != null) {
            return ResponseEntity.ok(assessmentRepository
                    .findByDecisionOrderByAssessedAtDesc(decision, pageable).getContent());
        }
        if (payerAccount != null && !payerAccount.isBlank()) {
            return ResponseEntity.ok(assessmentRepository
                    .findByPayerAccountOrderByAssessedAtDesc(payerAccount, pageable).getContent());
        }
        return ResponseEntity.ok(assessmentRepository.findAll(pageable).getContent());
    }

    @Operation(
            summary = "Resumo das decisoes",
            description = "Quantidade de aprovados e reprovados desde o inicio.")
    @ApiResponse(responseCode = "200", description = "Contagem por decisao")
    @GetMapping("/summary")
    public Map<String, Long> summary() {
        return Map.of(
                "approved", assessmentRepository.countByDecision(RiskDecision.APPROVED),
                "rejected", assessmentRepository.countByDecision(RiskDecision.REJECTED),
                "total", assessmentRepository.count());
    }
}
