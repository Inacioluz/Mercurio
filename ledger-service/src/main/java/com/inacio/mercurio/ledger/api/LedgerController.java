package com.inacio.mercurio.ledger.api;

import com.inacio.mercurio.ledger.api.dto.AccountBalanceResponse;
import com.inacio.mercurio.ledger.api.dto.IntegrityReport;
import com.inacio.mercurio.ledger.api.dto.LedgerEntryResponse;
import com.inacio.mercurio.ledger.repository.AccountRepository;
import com.inacio.mercurio.ledger.repository.LedgerEntryRepository;
import com.inacio.mercurio.ledger.service.BalanceCache;
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
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

@Tag(name = "Razao", description = "Consulta de saldos, partidas e integridade contabil")
@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Operation(
            summary = "Saldo de uma conta",
            description = "Resposta servida pelo cache Redis, invalidado a cada liquidacao que toca a conta.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Saldo atual",
                    content = @Content(schema = @Schema(implementation = AccountBalanceResponse.class))),
            @ApiResponse(responseCode = "404", description = "Conta nao encontrada no razao")
    })
    @Cacheable(cacheNames = BalanceCache.CACHE_NAME, key = "#accountNumber")
    @GetMapping("/accounts/{accountNumber}")
    public AccountBalanceResponse balance(
            @Parameter(description = "Numero da conta", example = "ACC-1001")
            @PathVariable String accountNumber) {
        return accountRepository.findById(accountNumber)
                .map(AccountBalanceResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Conta nao encontrada no razao: " + accountNumber));
    }

    @Operation(
            summary = "Extrato de uma conta",
            description = "Partidas da conta, da mais recente para a mais antiga.")
    @ApiResponse(responseCode = "200", description = "Partidas encontradas",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = LedgerEntryResponse.class))))
    @GetMapping("/accounts/{accountNumber}/entries")
    public ResponseEntity<List<LedgerEntryResponse>> entries(
            @Parameter(description = "Numero da conta", example = "ACC-1001")
            @PathVariable String accountNumber,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ledgerEntryRepository
                .findByAccountNumberOrderByCreatedAtDesc(accountNumber, pageable)
                .map(LedgerEntryResponse::from)
                .getContent());
    }

    @Operation(
            summary = "As duas partidas de uma movimentacao",
            description = "Dado o identificador da transacao, devolve o debito e o credito correspondentes.")
    @ApiResponse(responseCode = "200", description = "Partidas da transacao",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = LedgerEntryResponse.class))))
    @GetMapping("/transactions/{transactionId}")
    public ResponseEntity<List<LedgerEntryResponse>> transaction(
            @Parameter(description = "Identificador da transacao no razao")
            @PathVariable UUID transactionId) {
        return ResponseEntity.ok(ledgerEntryRepository
                .findByTransactionIdOrderByDirectionAsc(transactionId).stream()
                .map(LedgerEntryResponse::from)
                .toList());
    }

    @Operation(
            summary = "Confere a integridade do razao",
            description = """
                    Soma com sinal de todas as partidas. Em partidas dobradas o resultado tem de
                    ser exatamente zero: todo debito tem um credito de igual valor. Qualquer outro
                    valor indica lancamento desbalanceado — util para auditoria e como teste de
                    fumaca depois de uma carga.
                    """)
    @ApiResponse(responseCode = "200", description = "Relatorio de integridade",
            content = @Content(schema = @Schema(implementation = IntegrityReport.class)))
    @GetMapping("/integrity")
    public ResponseEntity<IntegrityReport> integrity() {
        return ResponseEntity.ok(IntegrityReport.of(
                ledgerEntryRepository.signedSum(),
                accountRepository.totalBalance()));
    }
}
