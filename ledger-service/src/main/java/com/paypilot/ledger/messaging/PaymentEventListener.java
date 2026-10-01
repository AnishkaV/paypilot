package com.paypilot.ledger.messaging;

import com.paypilot.common.events.PaymentEvent;
import com.paypilot.common.events.Topics;
import com.paypilot.ledger.posting.LedgerPostingService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Infra profile: consumes payment events from Kafka. */
@Component
@Profile("infra")
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final PaymentEventParser parser;
    private final LedgerPostingService postingService;

    public PaymentEventListener(PaymentEventParser parser, LedgerPostingService postingService) {
        this.parser = parser;
        this.postingService = postingService;
    }

    /**
     * The offset is committed only after this method returns, i.e. after the ledger transaction
     * committed. If we crash in between, Kafka redelivers and the idempotency check skips it.
     */
    @KafkaListener(topics = Topics.PAYMENT_EVENTS, groupId = "ledger-service")
    public void onPaymentEvent(ConsumerRecord<String, String> record) {
        PaymentEvent event = parser.parse(record.value(),
                record.topic() + "-" + record.partition() + "@" + record.offset());
        postingService.handle(event).ifPresent(entry ->
                log.info("Posted {} for payment {} ({} lines)", entry.getEntryType(), entry.getPaymentId(),
                        entry.getLines().size()));
    }
}
