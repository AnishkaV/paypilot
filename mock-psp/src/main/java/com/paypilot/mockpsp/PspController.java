package com.paypilot.mockpsp;

import java.util.Map;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PspController {

    private final PspSimulator simulator;

    public PspController(PspSimulator simulator) {
        this.simulator = simulator;
    }

    @PostMapping("/psp/{psp}/charges")
    public Charge charge(@PathVariable String psp, @Valid @RequestBody ChargeRequest request) {
        return simulator.charge(psp, request);
    }

    @GetMapping("/psp/{psp}/charges/{reference}")
    public Charge get(@PathVariable String psp, @PathVariable String reference) {
        return simulator.get(psp, reference);
    }

    @PostMapping("/psp/{psp}/charges/{reference}/refunds")
    public Charge refund(@PathVariable String psp, @PathVariable String reference) {
        return simulator.refund(psp, reference);
    }

    // --- Chaos controls -------------------------------------------------------------------

    @GetMapping("/admin/psps")
    public Map<String, PspBehavior> behaviors() {
        return simulator.behaviors();
    }

    @PutMapping("/admin/psps/{psp}")
    public PspBehavior update(@PathVariable String psp, @Valid @RequestBody PspBehavior behavior) {
        return simulator.update(psp, behavior);
    }

    @PostMapping("/admin/psps/reset")
    public Map<String, PspBehavior> reset() {
        simulator.reset();
        return simulator.behaviors();
    }

    // --- Errors ---------------------------------------------------------------------------

    @ExceptionHandler(PspUnavailableException.class)
    ProblemDetail unavailable(PspUnavailableException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "PSP_UNAVAILABLE", e);
    }

    @ExceptionHandler({UnknownPspException.class, ChargeNotFoundException.class})
    ProblemDetail notFound(RuntimeException e) {
        return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", e);
    }

    @ExceptionHandler(NotRefundableException.class)
    ProblemDetail notRefundable(NotRefundableException e) {
        return problem(HttpStatus.CONFLICT, "NOT_REFUNDABLE", e);
    }

    private static ProblemDetail problem(HttpStatus status, String code, Exception e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problem.setTitle(code);
        return problem;
    }
}
