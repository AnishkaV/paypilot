package com.paypilot.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paypilot.common.events.PaymentEvent;
import com.paypilot.common.events.PaymentEventType;
import com.paypilot.ledger.api.LedgerQueries;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Local profile (in-memory H2), events delivered over HTTP. Runs without Docker. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ledger-ingest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
class LedgerIngestTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LedgerQueries queries;

    @Test
    void postsCaptureOnceAndRefundReversesIt() throws Exception {
        String paymentId = shortId("pay_t_");
        PaymentEvent succeeded = event(PaymentEventType.PAYMENT_SUCCEEDED, paymentId, "m_t1", "psp_t1");

        send(event(PaymentEventType.PAYMENT_CREATED, paymentId, "m_t1", "psp_t1"))
                .andExpect(jsonPath("$.result").value("skipped"));
        send(succeeded).andExpect(status().isOk()).andExpect(jsonPath("$.result").value("posted"));
        send(succeeded).andExpect(status().isOk()).andExpect(jsonPath("$.result").value("skipped")); // duplicate

        assertThat(queries.balance("merchant_payable:m_t1").orElseThrow().balance()).isEqualTo(48_902);
        assertThat(queries.balance("platform_fee_revenue").orElseThrow().balance()).isPositive();

        send(event(PaymentEventType.PAYMENT_REFUNDED, paymentId, "m_t1", "psp_t1"))
                .andExpect(jsonPath("$.result").value("posted"));

        assertThat(queries.entriesForPayment(paymentId)).hasSize(2);
        assertThat(queries.balance("merchant_payable:m_t1").orElseThrow().balance()).isZero();
        assertThat(queries.balance("psp_receivable:psp_t1").orElseThrow().balance()).isZero();
        assertThat(queries.integrity().balanced()).isTrue();
    }

    @Test
    void refundBeforeCaptureAsksTheSenderToRetry() throws Exception {
        send(event(PaymentEventType.PAYMENT_REFUNDED, shortId("pay_t_"), "m_t2", "psp_t2"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void unreadableEventIsRejectedWith400() throws Exception {
        mvc.perform(post("/internal/events").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ledgerApiShowsBalancedBooks() throws Exception {
        send(event(PaymentEventType.PAYMENT_SUCCEEDED, shortId("pay_t_"), "m_t3", "psp_t3"));

        mvc.perform(get("/api/v1/ledger/integrity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanced").value(true));
        mvc.perform(get("/api/v1/ledger/entries/recent").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lines.length()").value(3));
    }

    private ResultActions send(PaymentEvent event) throws Exception {
        return mvc.perform(post("/internal/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(event)));
    }
    private static String shortId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }


    private static PaymentEvent event(PaymentEventType type, String paymentId, String merchant, String psp) {
        return new PaymentEvent("evt_" + UUID.randomUUID().toString().replace("-", ""), type, paymentId, merchant,
                49_900, "INR", psp, null, Instant.now());
    }
}
