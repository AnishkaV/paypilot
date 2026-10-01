package com.paypilot.payment.config;

import java.net.http.HttpClient;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class PspConfig {

    private static final Logger log = LoggerFactory.getLogger(PspConfig.class);

    /**
     * HTTP client for PSP calls. The two timeouts mean different things:
     * a connect timeout means the request never reached the PSP (safe to try another PSP),
     * a read timeout means it may have been charged (NOT safe to try another PSP).
     */
    @Bean
    RestClient pspRestClient(RestClient.Builder builder, PayPilotProperties properties) {
        PayPilotProperties.Psp psp = properties.psp();
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(psp.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(psp.readTimeout());
        return builder.baseUrl(psp.baseUrl()).requestFactory(requestFactory).build();
    }

    /** One circuit breaker per PSP, created lazily with this shared config. */
    @Bean
    CircuitBreakerRegistry pspCircuitBreakers(PayPilotProperties properties) {
        PayPilotProperties.CircuitBreaker cb = properties.psp().circuitBreaker();
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(cb.slidingWindowSize())
                .minimumNumberOfCalls(cb.minimumCalls())
                .failureRateThreshold(cb.failureRateThreshold())
                .waitDurationInOpenState(cb.waitInOpenState())
                .permittedNumberOfCallsInHalfOpenState(2)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        registry.getEventPublisher().onEntryAdded(added ->
                added.getAddedEntry().getEventPublisher().onStateTransition(event ->
                        log.warn("circuit breaker psp={} {}", event.getCircuitBreakerName(),
                                event.getStateTransition())));
        return registry;
    }
}
