package com.paypilot.payment.api;

import java.util.List;

import com.paypilot.common.web.PayPilotHeaders;
import com.paypilot.payment.domain.PaymentStatus;
import com.paypilot.payment.service.PaymentService;
import com.paypilot.payment.service.PaymentService.PaymentOutcome;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Merchant-facing payments API. The X-Merchant-Id header is set by the API gateway after it
 * checks the merchant's API key; clients cannot set it themselves through the gateway.
 */
@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create a payment",
            description = "201 with the final status, 202 if the PSP outcome is still unknown, "
                    + "200 when replaying an earlier request with the same Idempotency-Key.")
    public ResponseEntity<PaymentResponse> create(
            @Parameter(hidden = true) @RequestHeader(PayPilotHeaders.MERCHANT_ID) String merchantId,
            @RequestHeader(PayPilotHeaders.IDEMPOTENCY_KEY)
            @Pattern(regexp = "[A-Za-z0-9_\\-]{8,64}", message = "8-64 chars: letters, digits, _ or -")
            String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {

        PaymentOutcome outcome = service.create(merchantId, idempotencyKey, request.toCommand());
        HttpStatus status;
        if (outcome.replayed()) {
            status = HttpStatus.OK;
        } else if (outcome.payment().getStatus() == PaymentStatus.PROCESSING) {
            status = HttpStatus.ACCEPTED;
        } else {
            status = HttpStatus.CREATED;
        }
        return ResponseEntity.status(status)
                .header(PayPilotHeaders.IDEMPOTENT_REPLAYED, String.valueOf(outcome.replayed()))
                .body(PaymentResponse.from(outcome.payment()));
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Get a payment")
    public PaymentResponse get(@Parameter(hidden = true) @RequestHeader(PayPilotHeaders.MERCHANT_ID) String merchantId,
                               @PathVariable String paymentId) {
        return PaymentResponse.from(service.get(merchantId, paymentId));
    }

    @GetMapping
    @Operation(summary = "List the merchant's most recent payments")
    public List<PaymentResponse> recent(@Parameter(hidden = true) @RequestHeader(PayPilotHeaders.MERCHANT_ID) String merchantId,
                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return service.recent(merchantId, limit).stream().map(PaymentResponse::from).toList();
    }

    @GetMapping("/{paymentId}/attempts")
    @Operation(summary = "Every PSP tried for this payment and what happened")
    public List<AttemptResponse> attempts(@Parameter(hidden = true) @RequestHeader(PayPilotHeaders.MERCHANT_ID) String merchantId,
                                          @PathVariable String paymentId) {
        return service.attempts(merchantId, paymentId).stream().map(AttemptResponse::from).toList();
    }

    @PostMapping("/{paymentId}/refund")
    @Operation(summary = "Refund a succeeded payment in full")
    public PaymentResponse refund(@Parameter(hidden = true) @RequestHeader(PayPilotHeaders.MERCHANT_ID) String merchantId,
                                  @PathVariable String paymentId) {
        return PaymentResponse.from(service.refund(merchantId, paymentId));
    }
}
