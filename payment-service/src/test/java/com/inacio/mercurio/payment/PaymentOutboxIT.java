package com.inacio.mercurio.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inacio.mercurio.contracts.Topics;
import com.inacio.mercurio.payment.api.dto.CreatePaymentRequest;
import com.inacio.mercurio.payment.repository.OutboxEventRepository;
import com.inacio.mercurio.payment.repository.PaymentRepository;
import com.redis.testcontainers.RedisContainer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova o caminho outbox -> Kafka com infraestrutura real.
 *
 * <p>O que um teste com mock nao pegaria: a serializacao do evento, a chave de
 * particao, a transacionalidade entre o INSERT do pagamento e o do evento, e o
 * relay de fato entregando ao broker.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIf(value = "com.inacio.mercurio.payment.DockerSupport#isAvailable",
        disabledReason = "Docker nao esta disponivel nesta maquina")
@DisplayName("Outbox do payment-service")
class PaymentOutboxIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.8.0"));

    @Container
    @ServiceConnection
    static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private OutboxEventRepository outboxRepository;

    private KafkaConsumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        paymentRepository.deleteAll();

        Properties properties = new Properties();
        properties.put("bootstrap.servers", KAFKA.getBootstrapServers());
        properties.put("group.id", "test-" + UUID.randomUUID());
        properties.put("auto.offset.reset", "earliest");
        properties.put("key.deserializer", StringDeserializer.class.getName());
        properties.put("value.deserializer", StringDeserializer.class.getName());

        consumer = new KafkaConsumer<>(properties);
        consumer.subscribe(List.of(Topics.PAYMENT_REQUESTED));
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    @DisplayName("o pagamento aceito vira um evento publicado no Kafka, com o paymentId como chave")
    void publishesRequestedEvent() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "it-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePaymentRequest(
                                "ACC-1001", "ACC-2002", new BigDecimal("150.00"), "BRL", "teste de integracao"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        String paymentId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        // O relay roda a cada 500ms; a outbox precisa esvaziar sozinha.
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(outboxRepository.countByPublishedAtIsNull()).isZero());

        List<ConsumerRecord<String, String>> records = pollAll().stream()
                .filter(record -> paymentId.equals(record.key()))
                .toList();

        assertThat(records)
                .as("um evento publicado, com o paymentId como chave de particao — "
                        + "e o que preserva a ordem dos eventos de um mesmo pagamento")
                .hasSize(1);

        JsonNode event = objectMapper.readTree(records.getFirst().value());
        assertThat(event.get("paymentId").asText()).isEqualTo(paymentId);
        assertThat(event.get("payerAccount").asText()).isEqualTo("ACC-1001");
        assertThat(event.get("amount").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(event.get("eventId").asText()).as("todo evento carrega um id para deduplicacao").isNotBlank();
    }

    @Test
    @DisplayName("uma chave de idempotencia repetida nao gera um segundo evento")
    void doesNotDuplicateEventOnRetry() throws Exception {
        String body = objectMapper.writeValueAsString(new CreatePaymentRequest(
                "ACC-1001", "ACC-2002", new BigDecimal("99.90"), "BRL", null));

        MvcResult first = mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "it-002")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andReturn();

        String paymentId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

        // Mesmo pedido de novo: o cliente nao soube se o primeiro chegou.
        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "it-002")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId));

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(outboxRepository.countByPublishedAtIsNull()).isZero());

        assertThat(paymentRepository.count()).isEqualTo(1);

        // O consumidor le o topico desde o inicio e enxerga tambem os eventos dos
        // demais testes desta classe; o que importa e quantos existem para ESTE
        // pagamento.
        List<ConsumerRecord<String, String>> forThisPayment = pollAll().stream()
                .filter(record -> paymentId.equals(record.key()))
                .toList();

        assertThat(forThisPayment).as("apenas um evento para o pagamento %s", paymentId).hasSize(1);
    }

    @Test
    @DisplayName("um pedido recusado por regra de negocio nao deixa evento na outbox")
    void rejectedRequestLeavesNoEvent() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "it-003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePaymentRequest(
                                "ACC-1001", "ACC-1001", new BigDecimal("10.00"), "BRL", null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("SAME_ACCOUNT_PAYMENT"));

        assertThat(outboxRepository.count()).isZero();
        assertThat(paymentRepository.count()).isZero();
    }

    /**
     * Drena o topico por uma janela fixa. Parar na primeira rodada nao vazia
     * seria frágil: um evento pode chegar na rodada seguinte e o teste
     * concluiria, errado, que ele nao foi publicado.
     */
    private List<ConsumerRecord<String, String>> pollAll() {
        List<ConsumerRecord<String, String>> collected = new ArrayList<>();
        // As primeiras rodadas costumam voltar vazias enquanto o grupo completa
        // a atribuicao de particoes.
        for (int round = 0; round < 8; round++) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
            records.forEach(collected::add);
            if (!collected.isEmpty() && records.isEmpty()) {
                // Ja veio algo e a ultima rodada veio vazia: o topico drenou.
                break;
            }
        }
        return collected;
    }
}
