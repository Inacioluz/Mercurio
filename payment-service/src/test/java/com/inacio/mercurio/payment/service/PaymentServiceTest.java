package com.inacio.mercurio.payment.service;

import com.inacio.mercurio.contracts.PaymentRequested;
import com.inacio.mercurio.contracts.Topics;
import com.inacio.mercurio.payment.api.dto.CreatePaymentRequest;
import com.inacio.mercurio.payment.domain.Payment;
import com.inacio.mercurio.payment.domain.PaymentStatus;
import com.inacio.mercurio.payment.exception.BusinessRuleException;
import com.inacio.mercurio.payment.exception.IdempotencyConflictException;
import com.inacio.mercurio.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaymentService")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OutboxWriter outboxWriter;

    private IdempotencyService idempotencyService;
    private PaymentService paymentService;

    /** Idempotencia real, com um Redis que sempre falha: o banco e quem decide. */
    @BeforeEach
    void setUp() {
        var redisTemplate = org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenThrow(new org.springframework.dao.QueryTimeoutException("redis fora"));

        idempotencyService = new IdempotencyService(redisTemplate);
        paymentService = new PaymentService(paymentRepository, outboxWriter, idempotencyService);

        when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            if (payment.getId() == null) {
                payment.setId(UUID.randomUUID());
            }
            payment.setCreatedAt(Instant.now());
            payment.setUpdatedAt(Instant.now());
            return payment;
        });
    }

    @Nested
    @DisplayName("criacao")
    class Creation {

        @Test
        @DisplayName("grava o pagamento como PENDING e enfileira o evento na mesma transacao")
        void createsPendingAndEnqueues() {
            var result = paymentService.create("chave-1", request("150.00"));

            assertThat(result.replayed()).isFalse();
            assertThat(result.payment().status()).isEqualTo(PaymentStatus.PENDING);
            assertThat(result.payment().amount()).isEqualByComparingTo("150.00");

            ArgumentCaptor<PaymentRequested> captor = ArgumentCaptor.forClass(PaymentRequested.class);
            verify(outboxWriter).enqueue(eq(Topics.PAYMENT_REQUESTED), captor.capture());
            assertThat(captor.getValue().paymentId()).isEqualTo(result.payment().id());
            assertThat(captor.getValue().payerAccount()).isEqualTo("ACC-1001");
        }

        @Test
        @DisplayName("normaliza o valor para duas casas decimais")
        void normalizesAmountScale() {
            var result = paymentService.create("chave-2", request("150.005"));

            assertThat(result.payment().amount()).isEqualByComparingTo("150.01");
        }

        @Test
        @DisplayName("assume BRL quando a moeda nao vem")
        void defaultsCurrency() {
            var result = paymentService.create("chave-3",
                    new CreatePaymentRequest("ACC-1001", "ACC-2002", new BigDecimal("10.00"), null, null));

            assertThat(result.payment().currency()).isEqualTo("BRL");
        }

        @Test
        @DisplayName("recusa pagamento para a propria conta, sem gravar nada")
        void rejectsSameAccount() {
            assertThatThrownBy(() -> paymentService.create("chave-4",
                    new CreatePaymentRequest("ACC-1001", "ACC-1001", new BigDecimal("10.00"), "BRL", null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting("errorCode").isEqualTo("SAME_ACCOUNT_PAYMENT");

            verify(paymentRepository, never()).saveAndFlush(any());
            verify(outboxWriter, never()).enqueue(any(), any());
        }
    }

    @Nested
    @DisplayName("idempotencia")
    class Idempotency {

        @Test
        @DisplayName("a mesma chave com o mesmo corpo devolve o pagamento original, sem novo evento")
        void replaysOriginal() {
            Payment original = existingPayment("chave-5", "150.00");
            when(paymentRepository.findByIdempotencyKey("chave-5")).thenReturn(Optional.of(original));

            var result = paymentService.create("chave-5", request("150.00"));

            assertThat(result.replayed()).isTrue();
            assertThat(result.payment().id()).isEqualTo(original.getId());
            verify(paymentRepository, never()).saveAndFlush(any());
            verify(outboxWriter, never()).enqueue(any(), any());
        }

        @Test
        @DisplayName("a mesma chave com corpo diferente e recusada")
        void rejectsConflictingBody() {
            Payment original = existingPayment("chave-6", "150.00");
            when(paymentRepository.findByIdempotencyKey("chave-6")).thenReturn(Optional.of(original));

            assertThatThrownBy(() -> paymentService.create("chave-6", request("999.00")))
                    .isInstanceOf(IdempotencyConflictException.class);

            verify(outboxWriter, never()).enqueue(any(), any());
        }

        @Test
        @DisplayName("numa corrida, quem perde o indice unico devolve o pagamento do vencedor")
        void resolvesRaceViaUniqueIndex() {
            Payment winner = existingPayment("chave-7", "150.00");

            // O INSERT falha porque a outra requisicao gravou primeiro; a segunda
            // consulta ja encontra o registro do vencedor.
            when(paymentRepository.saveAndFlush(any(Payment.class)))
                    .thenThrow(new DataIntegrityViolationException("uk_payments_idempotency"));
            when(paymentRepository.findByIdempotencyKey("chave-7"))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(winner));

            var result = paymentService.create("chave-7", request("150.00"));

            assertThat(result.replayed()).isTrue();
            assertThat(result.payment().id()).isEqualTo(winner.getId());
            verify(outboxWriter, never()).enqueue(any(), any());
        }

        @Test
        @DisplayName("a idempotencia funciona com o Redis fora do ar")
        void worksWithoutRedis() {
            // O setUp faz o Redis lancar excecao em toda operacao.
            Payment original = existingPayment("chave-8", "150.00");
            when(paymentRepository.findByIdempotencyKey("chave-8")).thenReturn(Optional.of(original));

            var result = paymentService.create("chave-8", request("150.00"));

            assertThat(result.replayed()).isTrue();
        }
    }

    @Nested
    @DisplayName("transicoes vindas de evento")
    class Transitions {

        @Test
        @DisplayName("aplica a transicao valida e guarda a pontuacao de risco")
        void appliesValidTransition() {
            Payment payment = existingPayment("chave-9", "150.00");
            when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

            boolean applied = paymentService.applyTransition(
                    payment.getId(), PaymentStatus.APPROVED, 42, null);

            assertThat(applied).isTrue();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
            assertThat(payment.getRiskScore()).isEqualTo(42);
        }

        @Test
        @DisplayName("descarta evento fora de ordem sem alterar o pagamento")
        void ignoresOutOfOrderEvent() {
            Payment payment = existingPayment("chave-10", "150.00");
            payment.setStatus(PaymentStatus.SETTLED);
            when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));

            boolean applied = paymentService.applyTransition(
                    payment.getId(), PaymentStatus.APPROVED, 10, null);

            assertThat(applied).isFalse();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SETTLED);
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("registra o motivo quando a saga termina em falha")
        void recordsFailureReason() {
            Payment payment = existingPayment("chave-11", "150.00");
            payment.setStatus(PaymentStatus.APPROVED);
            when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

            paymentService.applyTransition(payment.getId(), PaymentStatus.FAILED, null, "Saldo insuficiente");

            assertThat(payment.getFailureReason()).isEqualTo("Saldo insuficiente");
        }
    }

    private CreatePaymentRequest request(String amount) {
        return new CreatePaymentRequest("ACC-1001", "ACC-2002", new BigDecimal(amount), "BRL", "teste");
    }

    private Payment existingPayment(String key, String amount) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .idempotencyKey(key)
                .payerAccount("ACC-1001")
                .payeeAccount("ACC-2002")
                .amount(new BigDecimal(amount))
                .currency("BRL")
                .status(PaymentStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
