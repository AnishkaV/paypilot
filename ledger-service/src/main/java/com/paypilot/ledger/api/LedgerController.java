package com.paypilot.ledger.api;

import java.util.List;

import com.paypilot.ledger.api.LedgerQueries.AccountBalance;
import com.paypilot.ledger.api.LedgerQueries.EntryView;
import com.paypilot.ledger.api.LedgerQueries.IntegrityReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Read-only ledger API. The gateway only lets requests with the admin key through. */
@RestController
@RequestMapping("/api/v1/ledger")
@Tag(name = "Ledger")
public class LedgerController {

    private final LedgerQueries queries;

    public LedgerController(LedgerQueries queries) {
        this.queries = queries;
    }

    @GetMapping("/accounts")
    @Operation(summary = "Every account with its debits, credits and balance")
    public List<AccountBalance> accounts() {
        return queries.balances();
    }

    @GetMapping("/accounts/{code}")
    @Operation(summary = "One account, e.g. merchant_payable:m_demo")
    public AccountBalance account(@PathVariable String code) {
        return queries.balance(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No account " + code));
    }

    @GetMapping("/entries")
    @Operation(summary = "Journal entries for a payment")
    public List<EntryView> entries(@RequestParam String paymentId) {
        return queries.entriesForPayment(paymentId);
    }

    @GetMapping("/entries/recent")
    @Operation(summary = "Latest journal entries across all payments")
    public List<EntryView> recentEntries(@RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit) {
        return queries.recentEntries(limit);
    }

    @GetMapping("/integrity")
    @Operation(summary = "Proves the books balance: total debits == total credits, per entry and overall")
    public IntegrityReport integrity() {
        return queries.integrity();
    }
}
