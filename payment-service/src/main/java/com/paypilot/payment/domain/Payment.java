package com.paypilot.payment.domain;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A payment intent. State only changes through the methods below, which enforce
 * {@link PaymentStatus#canTransitionTo}. There are deliberately no public setters.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private String id;
    private String merchantId;
    private String idempotencyKey;
    private String requestHash;
    private long amountMinor;
    private String currency;
    @Enumerated(EnumType.STRING)
    private PaymentMethod method;
    private String payer;
    private String description;
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;
    private String psp;
    private String pspReference;
    private String failureCode;

    /** Optimistic locking: two threads updating the same row cannot silently overwrite each other. */
    @Version
    private long version;

    private Instant createdAt;
    private Instant updatedAt;

    protected Payment() {
        // for JPA
    }

    public static Payment create(String merchantId, String idempotencyKey, String requestHash,
                                 long amountMinor, String currency, PaymentMethod method,
                                 String payer, String description) {
        Payment payment = new Payment();
        payment.id = Ids.newPaymentId();
        payment.merchantId = merchantId;
        payment.idempotencyKey = idempotencyKey;
        payment.requestHash = requestHash;
        payment.amountMinor = amountMinor;
        payment.currency = currency;
        payment.method = method;
        payment.payer = payer;
        payment.description = description;
        payment.status = PaymentStatus.CREATED;
        payment.createdAt = Instant.now();
        payment.updatedAt = payment.createdAt;
        return payment;
    }

    /** Called before the PSP request goes out, so a crash mid-call still tells us which PSP to ask. */
    public void startAttempt(String psp) {
        transitionTo(PaymentStatus.PROCESSING);
        this.psp = psp;
    }

    public void succeed(String pspReference) {
        transitionTo(PaymentStatus.SUCCEEDED);
        this.pspReference = pspReference;
        this.failureCode = null;
    }

    public void fail(String failureCode) {
        transitionTo(PaymentStatus.FAILED);
        this.failureCode = failureCode;
    }

    public void refund() {
        transitionTo(PaymentStatus.REFUNDED);
    }

    private void transitionTo(PaymentStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateTransitionException(id, status, next);
        }
        this.status = next;
        this.updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public String getPayer() {
        return payer;
    }

    public String getDescription() {
        return description;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getPsp() {
        return psp;
    }

    public String getPspReference() {
        return pspReference;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
