package com.paypilot.payment.outbox;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import com.paypilot.payment.config.PayPilotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Local profile: POSTs each event to every subscriber, one at a time and in order, which gives
 * the same per-payment ordering Kafka gives with a message key.
 *
 * <p>Like Kafka, this is at-least-once: a retried batch can deliver an event twice, and the
 * ledger's idempotent consumer drops the duplicate.
 */
@Component
@Profile("!infra")
public class HttpEventTransport implements EventTransport {

    private static final Logger log = LoggerFactory.getLogger(HttpEventTransport.class);

    private final RestClient restClient;
    private final List<String> subscribers;

    public HttpEventTransport(RestClient.Builder builder, PayPilotProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(1))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = builder.requestFactory(requestFactory).build();
        this.subscribers = List.copyOf(properties.events().httpSubscribers());
    }

    @Override
    public void deliver(List<OutboxEvent> events) {
        for (OutboxEvent event : events) {
            for (String subscriber : subscribers) {
                post(subscriber, event);
            }
        }
    }

    private void post(String subscriber, OutboxEvent event) {
        HttpStatusCode status;
        try {
            status = restClient.post()
                    .uri(subscriber)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Event-Id", event.getEventId())
                    .body(event.getPayload())
                    .retrieve()
                    .onStatus(code -> true, (request, response) -> { })
                    .toBodilessEntity()
                    .getStatusCode();
        } catch (RestClientException e) {
            throw new EventDeliveryException("Subscriber " + subscriber + " unreachable", e);
        }
        if (status.is5xxServerError()) {
            throw new EventDeliveryException("Subscriber " + subscriber + " answered " + status.value(), null);
        }
        if (status.is4xxClientError()) {
            // The subscriber can never accept this event. Retrying would block every event behind
            // it, so we log it and move on. With Kafka this is where a dead-letter topic comes in.
            log.error("Subscriber {} rejected event {} ({}) with {}; not retrying",
                    subscriber, event.getEventId(), event.getEventType(), status.value());
        }
    }

    @Override
    public String name() {
        return "http";
    }
}
