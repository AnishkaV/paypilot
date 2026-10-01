package com.paypilot.ledger.messaging;

import java.util.Map;

import com.paypilot.common.events.PaymentEvent;
import com.paypilot.ledger.domain.UnbalancedEntryException;
import com.paypilot.ledger.posting.LedgerPostingService;
import com.paypilot.ledger.posting.LedgerPostingService.MissingCaptureException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local profile: the payment service's outbox POSTs events here instead of publishing to Kafka.
 * Same idempotent posting service as the Kafka listener.
 *
 * <p>Status codes tell the sender what to do: 2xx done (or duplicate), 4xx never retry this
 * event, 5xx/503 try again later.
 */
@RestController
@RequestMapping("/internal/events")
@Tag(name = "Internal")
public class EventIngestController {

    private static final Logger log = LoggerFactory.getLogger(EventIngestController.class);

    private final PaymentEventParser parser;
    private final LedgerPostingService postingService;

    public EventIngestController(PaymentEventParser parser, LedgerPostingService postingService) {
        this.parser = parser;
        this.postingService = postingService;
    }

    @PostMapping
    @Operation(summary = "Receive one payment event (used by the outbox in the local profile)")
    public ResponseEntity<Map<String, String>> ingest(@RequestBody String payload) {
        PaymentEvent event;
        try {
            event = parser.parse(payload, "http");
        } catch (PoisonEventException e) {
            log.error("Rejected unreadable event: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("result", "rejected", "reason", e.getMessage()));
        }
        try {
            return postingService.handle(event)
                    .map(entry -> {
                        log.info("Posted {} for payment {} ({} lines)", entry.getEntryType(), entry.getPaymentId(),
                                entry.getLines().size());
                        return ResponseEntity.ok(Map.of("result", "posted", "entryType", entry.getEntryType().name()));
                    })
                    .orElseGet(() -> ResponseEntity.ok(Map.of("result", "skipped")));
        } catch (UnbalancedEntryException e) {
            log.error("Rejected event {}: {}", event.eventId(), e.getMessage());
            return ResponseEntity.unprocessableEntity().body(Map.of("result", "rejected", "reason", e.getMessage()));
        } catch (MissingCaptureException e) {
            // Refund arrived before its capture: ask the sender to retry later.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("result", "retry", "reason", e.getMessage()));
        }
    }
}
