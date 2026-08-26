package com.inacio.mercurio.ledger.service;

import com.inacio.mercurio.contracts.PaymentApproved;
import com.inacio.mercurio.contracts.PaymentFailed;
import com.inacio.mercurio.contracts.PaymentSettled;
import com.inacio.mercurio.contracts.Topics;
import com.inacio.mercurio.ledger.domain.Account;
import com.inacio.mercurio.ledger.domain.EntryDirection;
import com.inacio.mercurio.ledger.domain.LedgerEntry;
import com.inacio.mercurio.ledger.repository.AccountRepository;
import com.inacio.mercurio.ledger.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Liquidacao: transforma um pagamento aprovado em duas partidas no razao.
 *
 * <p>O dinheiro so se move aqui. Os demais servicos opinam sobre o pagamento;
 * este e o unico que altera saldo, e o faz numa transacao de banco com as duas
 * contas travadas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final BalanceCache balanceCache;

    /**
     * Liquida o pagamento e devolve o evento a publicar — {@link PaymentSettled}
     * em caso de sucesso, {@link PaymentFailed} quando o razao recusa.
     *
     * <p>Uma recusa aqui nao e excecao: saldo insuficiente e um desfecho previsto
     * da saga, que precisa ser comunicado, nao um erro a reprocessar. Lancar
     * excecao mandaria o evento para a DLT e deixaria o pagamento preso em
     * APPROVED para sempre.
     */
    @Transactional
    public SettlementOutcome settle(PaymentApproved event) {
        // Idempotencia: se ja existe partida para este pagamento, a liquidacao
        // aconteceu numa entrega anterior do mesmo evento.
        Optional<LedgerEntry> existing = ledgerEntryRepository
                .findFirstByPaymentIdAndDirection(event.paymentId(), EntryDirection.DEBIT);
        if (existing.isPresent()) {
            LedgerEntry entry = existing.get();
            log.debug("Pagamento {} ja liquidado na transacao {}", event.paymentId(), entry.getTransactionId());
            return SettlementOutcome.alreadySettled(settledEvent(event, entry));
        }

        // Trava as duas contas sempre na mesma ordem alfabetica. Sem isso, dois
        // pagamentos cruzados (A->B e B->A) simultaneos travariam um o do outro.
        List<String> lockOrder = Stream.of(event.payerAccount(), event.payeeAccount())
                .sorted(Comparator.naturalOrder())
                .toList();

        Optional<Account> first = accountRepository.findByIdForUpdate(lockOrder.get(0));
        Optional<Account> second = accountRepository.findByIdForUpdate(lockOrder.get(1));

        if (first.isEmpty() || second.isEmpty()) {
            String missing = first.isEmpty() ? lockOrder.get(0) : lockOrder.get(1);
            return SettlementOutcome.failed(failedEvent(event, "ACCOUNT_NOT_FOUND",
                    "Conta nao encontrada no razao: " + missing));
        }

        Account payer = first.get().getAccountNumber().equals(event.payerAccount()) ? first.get() : second.get();
        Account payee = payer == first.get() ? second.get() : first.get();

        if (!payer.getStatus().acceptsMovement()) {
            return SettlementOutcome.failed(failedEvent(event, "ACCOUNT_INACTIVE",
                    "A conta de origem " + payer.getAccountNumber() + " nao aceita movimentacoes"));
        }
        if (!payee.getStatus().acceptsMovement()) {
            return SettlementOutcome.failed(failedEvent(event, "ACCOUNT_INACTIVE",
                    "A conta de destino " + payee.getAccountNumber() + " nao aceita movimentacoes"));
        }
        if (!payer.getCurrency().equals(event.currency()) || !payee.getCurrency().equals(event.currency())) {
            return SettlementOutcome.failed(failedEvent(event, "CURRENCY_MISMATCH",
                    "Moeda do pagamento (" + event.currency() + ") incompativel com as contas"));
        }
        if (!payer.hasSufficientFunds(event.amount())) {
            return SettlementOutcome.failed(failedEvent(event, "INSUFFICIENT_FUNDS",
                    "Saldo insuficiente na conta " + payer.getAccountNumber()));
        }

        UUID transactionId = UUID.randomUUID();
        Instant now = Instant.now();

        payer.debit(event.amount());
        payee.credit(event.amount());
        accountRepository.save(payer);
        accountRepository.save(payee);

        LedgerEntry debit = record(transactionId, event, payer, EntryDirection.DEBIT, now);
        record(transactionId, event, payee, EntryDirection.CREDIT, now);

        balanceCache.evict(payer.getAccountNumber());
        balanceCache.evict(payee.getAccountNumber());

        log.info("Pagamento {} liquidado: {} {} de {} para {} (transacao {})",
                event.paymentId(), event.currency(), event.amount(),
                payer.getAccountNumber(), payee.getAccountNumber(), transactionId);

        return SettlementOutcome.settled(settledEvent(event, debit));
    }

    private LedgerEntry record(UUID transactionId, PaymentApproved event, Account account,
                               EntryDirection direction, Instant when) {
        return ledgerEntryRepository.save(LedgerEntry.builder()
                .transactionId(transactionId)
                .paymentId(event.paymentId())
                .accountNumber(account.getAccountNumber())
                .direction(direction)
                .amount(event.amount())
                .balanceAfter(account.getBalance())
                .currency(event.currency())
                .description("Pagamento " + event.paymentId())
                .createdAt(when)
                .build());
    }

    private PaymentSettled settledEvent(PaymentApproved source, LedgerEntry debitEntry) {
        return new PaymentSettled(
                UUID.randomUUID(),
                source.paymentId(),
                debitEntry.getTransactionId(),
                source.payerAccount(),
                source.payeeAccount(),
                source.amount(),
                debitEntry.getBalanceAfter(),
                Instant.now());
    }

    private PaymentFailed failedEvent(PaymentApproved source, String reasonCode, String reason) {
        log.warn("Pagamento {} recusado pelo razao: {} — {}", source.paymentId(), reasonCode, reason);
        return new PaymentFailed(UUID.randomUUID(), source.paymentId(), reasonCode, reason, Instant.now());
    }

    /** Evento resultante e o topico em que publica-lo. */
    public record SettlementOutcome(Object event, String topic, boolean duplicate) {

        static SettlementOutcome settled(PaymentSettled event) {
            return new SettlementOutcome(event, Topics.PAYMENT_SETTLED, false);
        }

        static SettlementOutcome alreadySettled(PaymentSettled event) {
            return new SettlementOutcome(event, Topics.PAYMENT_SETTLED, true);
        }

        static SettlementOutcome failed(PaymentFailed event) {
            return new SettlementOutcome(event, Topics.PAYMENT_FAILED, false);
        }
    }

    /** Saldo consultavel, para a API de leitura do razao. */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> currentBalance(String accountNumber) {
        return accountRepository.findById(accountNumber).map(Account::getBalance);
    }
}
