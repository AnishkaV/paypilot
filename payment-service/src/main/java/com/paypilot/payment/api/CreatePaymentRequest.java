package com.paypilot.payment.api;

import com.paypilot.payment.domain.PaymentMethod;
import com.paypilot.payment.service.PaymentService.NewPayment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreatePaymentRequest(
        @Schema(description = "Amount in paise (minor units). 49900 = Rs 499.00", example = "49900")
        @NotNull @Positive @Max(100_000_000) Long amount,

        @Schema(example = "INR")
        @NotBlank @Pattern(regexp = "INR", message = "only INR is supported for now") String currency,

        @Schema(example = "UPI")
        @NotNull PaymentMethod method,

        @Schema(description = "UPI VPA or card token. Put 'decline' or 'timeout' in it to force that outcome.",
                example = "anishka@okaxis")
        @NotBlank @Size(max = 100) String payer,

        @Schema(example = "Order #1042")
        @Size(max = 255) String description) {

    NewPayment toCommand() {
        return new NewPayment(amount, currency, method, payer, description);
    }
}
