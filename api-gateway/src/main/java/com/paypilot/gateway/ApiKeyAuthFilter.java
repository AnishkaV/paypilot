package com.paypilot.gateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.paypilot.common.web.PayPilotHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Runs before routing. Turns a merchant API key into a trusted X-Merchant-Id header.
 *
 * <ul>
 *   <li>/api/v1/payments/** needs a merchant key</li>
 *   <li>/api/v1/ledger/** and /api/v1/ops/** need the admin key</li>
 *   <li>Any X-Merchant-Id sent by the client is removed, so nobody can act as another merchant</li>
 *   <li>The API key itself never reaches internal services</li>
 * </ul>
 */
@Component
public class ApiKeyAuthFilter implements GlobalFilter, Ordered {

    /** Exchange attribute read by the rate limiter's key resolver. */
    public static final String PRINCIPAL_ATTRIBUTE = "paypilot.principal";

    private static final String ADMIN = "admin";

    private final Map<String, String> merchantsByApiKey;
    private final byte[] adminKey;

    public ApiKeyAuthFilter(PayPilotGatewayProperties properties) {
        this.merchantsByApiKey = properties.merchants().stream()
                .collect(Collectors.toUnmodifiableMap(PayPilotGatewayProperties.Merchant::apiKey,
                        PayPilotGatewayProperties.Merchant::merchantId));
        this.adminKey = properties.adminKey().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        if (!path.startsWith("/api/")) {
            return chain.filter(exchange);
        }

        String apiKey = request.getHeaders().getFirst(PayPilotHeaders.API_KEY);
        if (apiKey == null || apiKey.isBlank()) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "Missing X-Api-Key header");
        }

        String principal;
        if (path.startsWith("/api/v1/ledger") || path.startsWith("/api/v1/ops")) {
            if (!isAdminKey(apiKey)) {
                HttpStatus status = merchantsByApiKey.containsKey(apiKey) ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED;
                return reject(exchange, status, "This API needs the admin key");
            }
            principal = ADMIN;
        } else {
            principal = merchantsByApiKey.get(apiKey);
            if (principal == null) {
                return reject(exchange, HttpStatus.UNAUTHORIZED, "Invalid API key");
            }
        }

        String requestId = firstNonBlank(request.getHeaders().getFirst(PayPilotHeaders.REQUEST_ID),
                () -> UUID.randomUUID().toString());
        String merchantId = principal;
        ServerHttpRequest forwarded = request.mutate().headers(headers -> {
            headers.remove(PayPilotHeaders.API_KEY);
            headers.remove(PayPilotHeaders.MERCHANT_ID);
            if (!ADMIN.equals(merchantId)) {
                headers.set(PayPilotHeaders.MERCHANT_ID, merchantId);
            }
            headers.set(PayPilotHeaders.REQUEST_ID, requestId);
        }).build();

        exchange.getAttributes().put(PRINCIPAL_ATTRIBUTE, principal);
        exchange.getResponse().getHeaders().set(PayPilotHeaders.REQUEST_ID, requestId);
        return chain.filter(exchange.mutate().request(forwarded).build());
    }

    /** Constant-time comparison so response timing does not leak how much of the key was right. */
    private boolean isAdminKey(String candidate) {
        return MessageDigest.isEqual(adminKey, candidate.getBytes(StandardCharsets.UTF_8));
    }

    private static Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String detail) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        String body = "{\"title\":\"" + status.name() + "\",\"status\":" + status.value()
                + ",\"detail\":\"" + detail + "\"}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    private static String firstNonBlank(String value, Supplier<String> fallback) {
        return value != null && !value.isBlank() ? value : fallback.get();
    }
}
