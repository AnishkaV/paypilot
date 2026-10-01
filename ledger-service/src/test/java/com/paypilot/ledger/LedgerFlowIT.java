package com.paypilot.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paypilot.common.events.PaymentEvent;
import com.paypilot.common.events.PaymentEventType;
import com.paypilot.common.events.Topics;
import com.paypilot.ledger.api.LedgerQueries;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/** Infra profile with real Postgres and Kafka in Docker. Skipped automatically when Docker is not running. */
@SpringBootTest
@ActiveProfiles("infra")
@Testcontainers(disabledWithoutDocker = true)
class LedgerFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.9.1");

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LedgerQueries queries;

    @Test
    void postsEachEventOnceEvenWhenKafkaDeliversItTwice() throws Exception {
        String paymentId = shortId("pay_it_");
        PaymentEvent created = event(PaymentEventType.PAYMENT_CREATED, paymentId);
        PaymentEvent succeeded = event(PaymentEventType.PAYMENT_SUCCEEDED, paymentId);
        PaymentEvent refunded = event(PaymentEventType.PAYMENT_REFUNDED, paymentId);

        send(created);
        send(succeeded);
        send(succeeded); // duplicate delivery
        send(refunded);  // same key -> same partition -> processed after the duplicate

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(queries.entriesForPayment(paymentId)).hasSize(2));

        assertThat(queries.entriesForPayment(paymentId))
                .extracting(LedgerQueries.EntryView::entryType)
                .containsExactly("PAYMENT_CAPTURE", "PAYMENT_REFUND");
        // Captured then fully refunded: everything nets back to zero.
        assertThat(queries.balance("merchant_payable:m_it").orElseThrow().balance()).isZero();
        assertThat(queries.balance("psp_receivable:it_psp").orElseThrow().balance()).isZero();
        assertThat(queries.integrity().balanced()).isTrue();
    }

    @Test
    void unreadableEventGoesToTheDeadLetterTopic() {
        String key = "poison-" + UUID.randomUUID();
        kafkaTemplate.send(Topics.PAYMENT_EVENTS, key, "this is not json");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-check-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of(Topics.PAYMENT_EVENTS_DLT));
            List<ConsumerRecord<String, String>> seen = new ArrayList<>();
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(seen::add);
                assertThat(seen).anySatisfy(r -> {
                    assertThat(r.key()).isEqualTo(key);
                    assertThat(r.value()).isEqualTo("this is not json");
                });
            });
        }
    }

    private void send(PaymentEvent event) throws Exception {
        kafkaTemplate.send(Topics.PAYMENT_EVENTS, event.paymentId(), objectMapper.writeValueAsString(event)).get();
    }
    private static String shortId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }


    private static PaymentEvent event(PaymentEventType type, String paymentId) {
        return new PaymentEvent("evt_" + UUID.randomUUID().toString().replace("-", ""), type, paymentId, "m_it",
                49_900, "INR", "it_psp", null, Instant.now());
    }
}
