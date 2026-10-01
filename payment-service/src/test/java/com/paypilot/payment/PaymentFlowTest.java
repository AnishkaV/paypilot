package com.paypilot.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.paypilot.common.web.PayPilotHeaders;
import com.paypilot.payment.outbox.EventTransport;
import com.paypilot.payment.outbox.OutboxEvent;
import com.paypilot.payment.outbox.OutboxRepository;
import com.paypilot.payment.psp.PspClient;
import com.paypilot.payment.psp.PspResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The whole payment flow in the default local profile (in-memory H2). Runs without Docker.
 * The PSP and the event transport are mocked; everything else is real.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:payment-flow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
class PaymentFlowTest {

    @MockitoBean
    PspClient pspClient;

    @MockitoBean
    EventTransport eventTransport;

    @Autowired
    MockMvc mvc;

    @Autowired
    OutboxRepository outbox;

    @Test
    void createsPaymentWritesOutboxAndReplaysSafely() throws Exception {
        when(pspClient.charge(eq("alpha"), any())).thenReturn(PspResult.succeeded("alpha_ref_1", 12));
        String key = newKey();

        String body = create("m_t1", key, 49_900, "t@upi")
                .andExpect(status().isCreated())
                .andExpect(header().string(PayPilotHeaders.IDEMPOTENT_REPLAYED, "false"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.psp").value("alpha"))
                .andReturn().getResponse().getContentAsString();
        String paymentId = JsonPath.read(body, "$.id");

        create("m_t1", key, 49_900, "t@upi")
                .andExpect(status().isOk())
                .andExpect(header().string(PayPilotHeaders.IDEMPOTENT_REPLAYED, "true"))
                .andExpect(jsonPath("$.id").value(paymentId));
        verify(pspClient, times(1)).charge(anyString(), any());

        // The outbox publisher picks the events up and hands them to the transport.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(outbox.findByAggregateIdOrderByIdAsc(paymentId))
                        .extracting(OutboxEvent::getEventType)
                        .containsExactly("PAYMENT_CREATED", "PAYMENT_SUCCEEDED"));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(outbox.findByAggregateIdOrderByIdAsc(paymentId))
                        .allSatisfy(e -> assertThat(e.getPublishedAt()).isNotNull()));
    }

    @Test
    void rejectsIdempotencyKeyReusedWithADifferentBody() throws Exception {
        when(pspClient.charge(anyString(), any())).thenReturn(PspResult.succeeded("ref", 10));
        String key = newKey();

        create("m_t2", key, 10_000, "a@upi").andExpect(status().isCreated());
        create("m_t2", key, 99_000, "a@upi")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void failsOverAndRecordsEveryAttempt() throws Exception {
        when(pspClient.charge(eq("alpha"), any())).thenReturn(PspResult.notProcessed("PSP_UNAVAILABLE", 3));
        when(pspClient.charge(eq("beta"), any())).thenReturn(PspResult.succeeded("beta_ref", 25));

        String body = create("m_t3", newKey(), 20_000, "b@upi")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.psp").value("beta"))
                .andReturn().getResponse().getContentAsString();
        String paymentId = JsonPath.read(body, "$.id");

        mvc.perform(get("/api/v1/payments/{id}/attempts", paymentId).header(PayPilotHeaders.MERCHANT_ID, "m_t3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].outcome").value("NOT_PROCESSED"))
                .andExpect(jsonPath("$[1].outcome").value("SUCCEEDED"));
    }

    @Test
    void timeoutLeavesPaymentProcessingAndDoesNotFailOver() throws Exception {
        when(pspClient.charge(eq("alpha"), any())).thenReturn(PspResult.unknown("PSP_TIMEOUT", 2_000));

        create("m_t4", newKey(), 30_000, "c@upi")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
        verify(pspClient, never()).charge(eq("beta"), any());
    }

    @Test
    void refundsASucceededPayment() throws Exception {
        when(pspClient.charge(anyString(), any())).thenReturn(PspResult.succeeded("ref", 10));
        when(pspClient.refund(anyString(), anyString())).thenReturn(PspResult.succeeded("ref", 8));
        String body = create("m_t5", newKey(), 5_000, "d@upi").andReturn().getResponse().getContentAsString();
        String paymentId = JsonPath.read(body, "$.id");

        mvc.perform(post("/api/v1/payments/{id}/refund", paymentId).header(PayPilotHeaders.MERCHANT_ID, "m_t5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    void merchantsCannotSeeEachOthersPayments() throws Exception {
        when(pspClient.charge(anyString(), any())).thenReturn(PspResult.succeeded("ref", 10));
        String body = create("m_t6", newKey(), 5_000, "e@upi").andReturn().getResponse().getContentAsString();
        String paymentId = JsonPath.read(body, "$.id");

        mvc.perform(get("/api/v1/payments/{id}", paymentId).header(PayPilotHeaders.MERCHANT_ID, "m_other"))
                .andExpect(status().isNotFound());
    }

    @Test
    void opsStatsCountPaymentsAcrossMerchants() throws Exception {
        when(pspClient.charge(anyString(), any())).thenReturn(PspResult.succeeded("ref", 10));
        create("m_t7", newKey(), 1_000, "f@upi").andExpect(status().isCreated());

        mvc.perform(get("/internal/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.succeeded").isNumber())
                .andExpect(jsonPath("$.psps.length()").value(3))
                .andExpect(jsonPath("$.timeline.length()").value(30));
    }

    @Test
    void requestWithoutIdempotencyKeyIsRejected() throws Exception {
        mvc.perform(post("/api/v1/payments")
                        .header(PayPilotHeaders.MERCHANT_ID, "m_t8")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(1_000, "g@upi")))
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
                {"amount": %d, "currency": "INR", "method": "UPI", "payer": "%s", "description": "test"}
                """.formatted(amount, payer);
    }

    private static String newKey() {
        return "t-" + UUID.randomUUID();
    }
}
