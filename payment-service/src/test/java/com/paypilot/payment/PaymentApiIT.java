package com.paypilot.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.paypilot.common.events.Topics;
import com.paypilot.common.web.PayPilotHeaders;
import com.paypilot.payment.outbox.OutboxEvent;
import com.paypilot.payment.outbox.OutboxRepository;
import com.paypilot.payment.psp.PspClient;
import com.paypilot.payment.psp.PspResult;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Full payment flow in the infra profile, against real Postgres, Kafka and Redis in Docker.
 * Only the PSP is mocked. Skipped automatically when Docker is not running.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("infra")
@Testcontainers(disabledWithoutDocker = true)
class PaymentApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.1");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @MockitoBean
    PspClient pspClient;

    @Autowired
    MockMvc mvc;

    @Autowired
    OutboxRepository outbox;

    @Test
    void createsPaymentPublishesEventsAndReplaysSafely() throws Exception {
        when(pspClient.charge(eq("alpha"), any())).thenReturn(PspResult.succeeded("alpha_ref_1", 12));
        String key = newKey();

        String body = create("m_it_1", key, 49_900, "it@upi")
                .andExpect(status().isCreated())
                .andExpect(header().string(PayPilotHeaders.IDEMPOTENT_REPLAYED, "false"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.psp").value("alpha"))
                .andReturn().getResponse().getContentAsString();
        String paymentId = JsonPath.read(body, "$.id");

        // Same key, same body: we get the same payment back and the PSP is not called again.
        create("m_it_1", key, 49_900, "it@upi")
                .andExpect(status().isOk())
                .andExpect(header().string(PayPilotHeaders.IDEMPOTENT_REPLAYED, "true"))
                .andExpect(jsonPath("$.id").value(paymentId));
        verify(pspClient, times(1)).charge(anyString(), any());

        // CREATED and SUCCEEDED were written to the outbox and published to Kafka, keyed by payment id.
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(outbox.findByAggregateIdOrderByIdAsc(paymentId))
                        .extracting(OutboxEvent::getEventType)
                        .containsExactly("PAYMENT_CREATED", "PAYMENT_SUCCEEDED"));
        assertThat(consumeKeys(paymentId, 2)).containsOnly(paymentId);
    }

    @Test
    void rejectsIdempotencyKeyReusedWithADifferentBody() throws Exception {
        when(pspClient.charge(anyString(), any())).thenReturn(PspResult.succeeded("ref", 10));
        String key = newKey();

        create("m_it_2", key, 10_000, "a@upi").andExpect(status().isCreated());
        create("m_it_2", key, 99_000, "a@upi")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void failsOverToTheNextPspAndRecordsEveryAttempt() throws Exception {
        when(pspClient.charge(eq("alpha"), any())).thenReturn(PspResult.notProcessed("PSP_UNAVAILABLE", 3));
        when(pspClient.charge(eq("beta"), any())).thenReturn(PspResult.succeeded("beta_ref", 25));

        String body = create("m_it_3", newKey(), 20_000, "b@upi")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.psp").value("beta"))
                .andReturn().getResponse().getContentAsString();
        String paymentId = JsonPath.read(body, "$.id");

        mvc.perform(get("/api/v1/payments/{id}/attempts", paymentId).header(PayPilotHeaders.MERCHANT_ID, "m_it_3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].psp").value("alpha"))
                .andExpect(jsonPath("$[0].outcome").value("NOT_PROCESSED"))
                .andExpect(jsonPath("$[1].psp").value("beta"))
                .andExpect(jsonPath("$[1].outcome").value("SUCCEEDED"));
    }

    @Test
    void timeoutLeavesPaymentProcessingInsteadOfTryingAnotherPsp() throws Exception {
        when(pspClient.charge(eq("alpha"), any())).thenReturn(PspResult.unknown("PSP_TIMEOUT", 2_000));

        create("m_it_4", newKey(), 30_000, "c@upi")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
        verify(pspClient, times(0)).charge(eq("beta"), any());
    }

    @Test
    void merchantsCannotSeeEachOthersPayments() throws Exception {
        when(pspClient.charge(anyString(), any())).thenReturn(PspResult.succeeded("ref", 10));
        String body = create("m_it_5", newKey(), 5_000, "d@upi").andReturn().getResponse().getContentAsString();
        String paymentId = JsonPath.read(body, "$.id");

        mvc.perform(get("/api/v1/payments/{id}", paymentId).header(PayPilotHeaders.MERCHANT_ID, "m_other"))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestWithoutIdempotencyKeyIsRejected() throws Exception {
        mvc.perform(post("/api/v1/payments")
                        .header(PayPilotHeaders.MERCHANT_ID, "m_it_6")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(1_000, "e@upi")))
                .andExpect(status().isBadRequest());
    }

    private ResultActions create(String merchantId, String key, long amount, String payer) throws Exception {
        return mvc.perform(post("/api/v1/payments")
                .header(PayPilotHeaders.MERCHANT_ID, merchantId)
                .header(PayPilotHeaders.IDEMPOTENCY_KEY, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(amount, payer)));
    }

    private static String json(long amount, String payer) {
        return """
                {"amount": %d, "currency": "INR", "method": "UPI", "payer": "%s", "description": "IT"}
                """.formatted(amount, payer);
    }

    private static String newKey() {
        return "it-" + UUID.randomUUID();
    }

    private static List<String> consumeKeys(String paymentId, int expected) {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of(Topics.PAYMENT_EVENTS));
            List<String> keys = new ArrayList<>();
            await().atMost(Duration.ofSeconds(20)).until(() -> {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (paymentId.equals(record.key())) {
                        keys.add(record.key());
                    }
                }
                return keys.size() >= expected;
            });
            return keys;
        }
    }
}
