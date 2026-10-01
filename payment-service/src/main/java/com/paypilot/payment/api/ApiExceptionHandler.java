package com.paypilot.payment.api;

import com.paypilot.payment.domain.IllegalStateTransitionException;
import com.paypilot.payment.idempotency.IdempotencyKeyReusedException;
import com.paypilot.payment.idempotency.RequestInProgressException;
import com.paypilot.payment.service.PaymentNotFoundException;
import com.paypilot.payment.service.RefundFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps exceptions to RFC 9457 problem responses (application/problem+json). The parent class
 * already handles validation errors and missing headers with 400.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    ProblemDetail notFound(PaymentNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", e);
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ProblemDetail keyReused(IdempotencyKeyReusedException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED", e);
    }

    @ExceptionHandler(RequestInProgressException.class)
    ProblemDetail inProgress(RequestInProgressException e) {
        return problem(HttpStatus.CONFLICT, "REQUEST_IN_PROGRESS", e);
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    ProblemDetail invalidState(IllegalStateTransitionException e) {
        return problem(HttpStatus.CONFLICT, "INVALID_PAYMENT_STATE", e);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail concurrentUpdate(ObjectOptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "CONCURRENT_UPDATE",
                new IllegalStateException("The payment was updated by another request. Fetch it and retry."));
    }

    @ExceptionHandler(RefundFailedException.class)
    ProblemDetail refundFailed(RefundFailedException e) {
        return problem(HttpStatus.BAD_GATEWAY, "REFUND_FAILED", e);
    }

    private static ProblemDetail problem(HttpStatus status, String code, Exception e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problem.setTitle(code);
        return problem;
    }
}
