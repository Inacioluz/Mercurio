package com.inacio.mercurio.ledger.service;

import com.inacio.mercurio.contracts.PaymentApproved;
import com.inacio.mercurio.contracts.PaymentFailed;
import com.inacio.mercurio.contracts.PaymentSettled;
import com.inacio.mercurio.contracts.Topics;
import com.inacio.mercurio.ledger.domain.Account;
import com.inacio.mercurio.ledger.domain.AccountStatus;
import com.inacio.mercurio.ledger.domain.EntryDirection;
import com.inacio.mercurio.ledger.domain.LedgerEntry;
import com.inacio.mercurio.ledger.repository.AccountRepository;
import com.inacio.mercurio.ledger.repository.LedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SettlementService")
class SettlementServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    @Mock
    private BalanceCache balanceCache;

    @InjectMocks
    private SettlementService settlementService;

    private Account payer;
    private Account payee;

    @BeforeEach
    void setUp() {
        payer = account("ACC-1001", "10000.00", AccountStatus.ACTIVE);
        payee = account("ACC-2002", "1200.00", AccountStatus.ACTIVE);

        when(ledgerEntryRepository.findFirstByPaymentIdAndDirection(any(), any())).thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate("ACC-1001")).thenReturn(Optional.of(payer));
        when(accountRepository.findByIdForUpdate("ACC-2002")).thenReturn(Optional.of(payee));
        when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));
        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Nested
    @DisplayName("liquidacao bem-sucedida")
    class Success {

        @Test
        @DisplayName("move o valor e devolve PaymentSettled")
        void movesMoney() {
            var outcome = settlementService.settle(approved("150.00"));

            assertThat(payer.getBalance()).isEqualByComparingTo("9850.00");
            assertThat(payee.getBalance()).isEqualByComparingTo("1350.00");
            assertThat(outcome.topic()).isEqualTo(Topics.PAYMENT_SETTLED);
            assertThat(outcome.event()).isInstanceOf(PaymentSettled.class);

            PaymentSettled settled = (PaymentSettled) outcome.event();
            assertThat(settled.payerBalanceAfter()).isEqualByComparingTo("9850.00");
        }

        @Test
        @DisplayName("grava exatamente duas partidas, que se anulam")
        void writesBalancedDoubleEntry() {
            settlementService.settle(approved("150.00"));

            ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
            verify(ledgerEntryRepository, times(2)).save(captor.capture());

            List<LedgerEntry> entries = captor.getAllValues();
            assertThat(entries).extracting(LedgerEntry::getDirection)
                    .containsExactly(EntryDirection.DEBIT, EntryDirection.CREDIT);
            assertThat(entries.get(0).getTransactionId()).isEqualTo(entries.get(1).getTransactionId());

            BigDecimal sum = entries.stream()
                    .map(LedgerEntry::signedAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(sum).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("invalida o cache de saldo das duas contas")
        void evictsBothBalances() {
            settlementService.settle(approved("150.00"));

            verify(balanceCache).evict("ACC-1001");
            verify(balanceCache).evict("ACC-2002");
        }

        @Test
        @DisplayName("permite gastar exatamente o saldo disponivel")
        void allowsExactBalance() {
            var outcome = settlementService.settle(approved("10000.00"));

            assertThat(payer.getBalance()).isEqualByComparingTo("0.00");
            assertThat(outcome.event()).isInstanceOf(PaymentSettled.class);
        }
    }

    @Nested
    @DisplayName("recusa")
    class Rejection {

        @Test
        @DisplayName("saldo insuficiente devolve PaymentFailed sem lancar excecao")
        void insufficientFunds() {
            var outcome = settlementService.settle(approved("10000.01"));

            assertThat(outcome.topic()).isEqualTo(Topics.PAYMENT_FAILED);
            assertThat(((PaymentFailed) outcome.event()).reasonCode()).isEqualTo("INSUFFICIENT_FUNDS");

            // Nada foi movido nem gravado.
            assertThat(payer.getBalance()).isEqualByComparingTo("10000.00");
            assertThat(payee.getBalance()).isEqualByComparingTo("1200.00");
            verify(ledgerEntryRepository, never()).save(any());
        }

        @Test
        @DisplayName("conta de origem inativa")
        void inactivePayer() {
            payer.setStatus(AccountStatus.BLOCKED);

            var outcome = settlementService.settle(approved("10.00"));

            assertThat(((PaymentFailed) outcome.event()).reasonCode()).isEqualTo("ACCOUNT_INACTIVE");
        }

        @Test
        @DisplayName("conta de destino inativa")
        void inactivePayee() {
            payee.setStatus(AccountStatus.CLOSED);

            var outcome = settlementService.settle(approved("10.00"));

            assertThat(((PaymentFailed) outcome.event()).reasonCode()).isEqualTo("ACCOUNT_INACTIVE");
        }

        @Test
        @DisplayName("conta inexistente no razao")
        void unknownAccount() {
            when(accountRepository.findByIdForUpdate("ACC-2002")).thenReturn(Optional.empty());

            var outcome = settlementService.settle(approved("10.00"));

            assertThat(((PaymentFailed) outcome.event()).reasonCode()).isEqualTo("ACCOUNT_NOT_FOUND");
        }

        @Test
        @DisplayName("moeda diferente da conta")
        void currencyMismatch() {
            PaymentApproved event = new PaymentApproved(UUID.randomUUID(), UUID.randomUUID(),
                    "ACC-1001", "ACC-2002", new BigDecimal("10.00"), "USD", 0, Instant.now());

            var outcome = settlementService.settle(event);

            assertThat(((PaymentFailed) outcome.event()).reasonCode()).isEqualTo("CURRENCY_MISMATCH");
        }
    }

    @Nested
    @DisplayName("idempotencia")
    class Idempotency {

        @Test
        @DisplayName("uma reentrega nao move dinheiro pela segunda vez")
        void doesNotSettleTwice() {
            UUID paymentId = UUID.randomUUID();
            LedgerEntry previous = LedgerEntry.builder()
                    .id(UUID.randomUUID())
                    .transactionId(UUID.randomUUID())
                    .paymentId(paymentId)
                    .accountNumber("ACC-1001")
                    .direction(EntryDirection.DEBIT)
                    .amount(new BigDecimal("150.00"))
                    .balanceAfter(new BigDecimal("9850.00"))
                    .currency("BRL")
                    .createdAt(Instant.now())
                    .build();

            when(ledgerEntryRepository.findFirstByPaymentIdAndDirection(eq(paymentId), eq(EntryDirection.DEBIT)))
                    .thenReturn(Optional.of(previous));

            PaymentApproved event = new PaymentApproved(UUID.randomUUID(), paymentId,
                    "ACC-1001", "ACC-2002", new BigDecimal("150.00"), "BRL", 0, Instant.now());

            var outcome = settlementService.settle(event);

            assertThat(outcome.duplicate()).isTrue();
            assertThat(payer.getBalance()).isEqualByComparingTo("10000.00");
            verify(accountRepository, never()).save(any());
            verify(ledgerEntryRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("trava as contas em ordem alfabetica, para transferencias cruzadas nao travarem uma na outra")
    void locksInDeterministicOrder() {
        // Pagamento no sentido inverso: 2002 -> 1001. A ordem de travamento deve
        // ser a mesma do sentido direto.
        PaymentApproved reverse = new PaymentApproved(UUID.randomUUID(), UUID.randomUUID(),
                "ACC-2002", "ACC-1001", new BigDecimal("10.00"), "BRL", 0, Instant.now());

        settlementService.settle(reverse);

        var inOrder = org.mockito.Mockito.inOrder(accountRepository);
        inOrder.verify(accountRepository).findByIdForUpdate("ACC-1001");
        inOrder.verify(accountRepository).findByIdForUpdate("ACC-2002");
    }

    private PaymentApproved approved(String amount) {
        return new PaymentApproved(UUID.randomUUID(), UUID.randomUUID(),
                "ACC-1001", "ACC-2002", new BigDecimal(amount), "BRL", 0, Instant.now());
    }

    private static Account account(String number, String balance, AccountStatus status) {
        return Account.builder()
                .accountNumber(number)
                .ownerName("Titular " + number)
                .currency("BRL")
                .balance(new BigDecimal(balance))
                .status(status)
                .build();
    }
}
