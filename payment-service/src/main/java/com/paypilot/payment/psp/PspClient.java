package com.paypilot.payment.psp;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Talks HTTP to the PSPs and turns every possible result into a {@link PspResult}.
 * This class never throws for PSP problems; the caller decides what to do based on the outcome.
 */
@Component
public class PspClient {

    private static final Logger log = LoggerFactory.getLogger(PspClient.class);

    private final RestClient restClient;

    public PspClient(RestClient pspRestClient) {
        this.restClient = pspRestClient;
    }

    public PspResult charge(String psp, ChargeCommand command) {
        long start = System.nanoTime();
        try {
            return restClient.post()
                    .uri("/psp/{psp}/charges", psp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(command)
                    .exchange((request, response) -> {
                        long latency = elapsedMs(start);
                        HttpStatusCode status = response.getStatusCode();
                        if (status.is2xxSuccessful()) {
                            return fromBody(response.bodyTo(PspChargeResponse.class), latency);
                        }
                        if (status.value() == 503) {
                            return PspResult.notProcessed("PSP_UNAVAILABLE", latency);
                        }
                        return PspResult.unknown("PSP_HTTP_" + status.value(), latency);
                    });
        } catch (RestClientException e) {
            return fromTransportFailure(psp, e, elapsedMs(start));
        }
    }

    /** Asks the PSP what happened to a charge we lost track of (used by the sweeper). */
    public PspResult getCharge(String psp, String reference) {
        long start = System.nanoTime();
        try {
            return restClient.get()
                    .uri("/psp/{psp}/charges/{reference}", psp, reference)
                    .exchange((request, response) -> {
                        long latency = elapsedMs(start);
                        HttpStatusCode status = response.getStatusCode();
                        if (status.is2xxSuccessful()) {
                            return fromBody(response.bodyTo(PspChargeResponse.class), latency);
                        }
                        if (status.value() == 404) {
                            return PspResult.notProcessed("PSP_NO_RECORD", latency);
                        }
                        return PspResult.unknown("PSP_HTTP_" + status.value(), latency);
                    });
        } catch (RestClientException e) {
            return PspResult.unknown("PSP_STATUS_CHECK_FAILED", elapsedMs(start));
        }
    }

    /** Returns SUCCEEDED when the PSP confirms the refund, otherwise a result carrying the reason. */
    public PspResult refund(String psp, String reference) {
        long start = System.nanoTime();
        try {
            return restClient.post()
                    .uri("/psp/{psp}/charges/{reference}/refunds", psp, reference)
                    .exchange((request, response) -> {
                        long latency = elapsedMs(start);
                        HttpStatusCode status = response.getStatusCode();
                        if (status.is2xxSuccessful()) {
                            PspChargeResponse body = response.bodyTo(PspChargeResponse.class);
                            if (body != null && "REFUNDED".equals(body.status())) {
                                return PspResult.succeeded(body.pspReference(), latency);
                            }
                            return PspResult.unknown("UNEXPECTED_REFUND_STATUS", latency);
                        }
                        return PspResult.notProcessed("PSP_REFUND_HTTP_" + status.value(), latency);
                    });
        } catch (RestClientException e) {
            return PspResult.unknown("PSP_REFUND_FAILED", elapsedMs(start));
        }
    }

    private static PspResult fromBody(PspChargeResponse body, long latency) {
        if (body == null || body.status() == null) {
            return PspResult.unknown("PSP_EMPTY_RESPONSE", latency);
        }
        return switch (body.status()) {
            case "SUCCEEDED" -> PspResult.succeeded(body.pspReference(), latency);
            case "DECLINED" -> PspResult.declined(body.declineCode(), latency);
            default -> PspResult.unknown("PSP_UNEXPECTED_STATUS_" + body.status(), latency);
        };
    }

    /**
     * No HTTP response at all. If we could not even connect, nothing was sent, so the PSP cannot
     * have charged anyone. A timeout after connecting is different: the PSP may have charged.
     */
    private static PspResult fromTransportFailure(String psp, RestClientException e, long latency) {
        Throwable cause = e instanceof ResourceAccessException ? e.getCause() : e;
        if (cause instanceof HttpConnectTimeoutException
                || cause instanceof ConnectException
                || cause instanceof UnknownHostException) {
            log.warn("psp={} unreachable: {}", psp, cause.toString());
            return PspResult.notProcessed("PSP_UNREACHABLE", latency);
        }
        if (cause instanceof HttpTimeoutException) {
            log.warn("psp={} read timeout after {} ms, outcome unknown", psp, latency);
            return PspResult.unknown("PSP_TIMEOUT", latency);
        }
        log.warn("psp={} call failed, outcome unknown: {}", psp, e.toString());
        return PspResult.unknown("PSP_IO_ERROR", latency);
    }

    private static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
