package com.paypilot.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.paypilot.common.web.PayPilotHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class ApiKeyAuthFilterTest {

    private final ApiKeyAuthFilter filter = new ApiKeyAuthFilter(new PayPilotGatewayProperties(
            List.of(new PayPilotGatewayProperties.Merchant("pk_test_x", "m_x")),
            "sk_admin", null, null));

    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    private final GatewayFilterChain chain = exchange -> {
        forwarded.set(exchange);
        return Mono.empty();
    };

    @Test
    void rejectsRequestWithoutApiKey() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/payments"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded.get()).isNull();
    }

    @Test
    void rejectsUnknownApiKey() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/payments")
                .header(PayPilotHeaders.API_KEY, "pk_test_wrong"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validKeyForwardsTrustedMerchantIdAndDropsSpoofedOne() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/payments")
                .header(PayPilotHeaders.API_KEY, "pk_test_x")
                .header(PayPilotHeaders.MERCHANT_ID, "m_someone_else"));

        filter.filter(exchange, chain).block();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.get(PayPilotHeaders.MERCHANT_ID)).containsExactly("m_x");
        assertThat(headers.getFirst(PayPilotHeaders.API_KEY)).isNull();
        assertThat(headers.getFirst(PayPilotHeaders.REQUEST_ID)).isNotBlank();
    }

    @Test
    void merchantKeyCannotReadTheLedger() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/ledger/accounts")
                .header(PayPilotHeaders.API_KEY, "pk_test_x"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminKeyCanReadTheLedger() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/ledger/accounts")
                .header(PayPilotHeaders.API_KEY, "sk_admin"));

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst(PayPilotHeaders.MERCHANT_ID)).isNull();
    }
}
