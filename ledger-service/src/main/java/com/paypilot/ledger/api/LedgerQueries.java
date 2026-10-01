package com.paypilot.ledger.api;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.paypilot.ledger.domain.AccountType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Read side of the ledger in plain SQL. Balances are computed from the lines on every read,
 * which is simple and always correct. At high volume you would add a snapshot table that is
 * updated in the same transaction as the lines.
 */
@Component
public class LedgerQueries {

    private static final String BALANCES_SQL = """
            SELECT a.code, a.type, a.currency,
                   COALESCE(SUM(CASE WHEN l.direction = 'DEBIT'  THEN l.amount_minor END), 0) AS debits,
                   COALESCE(SUM(CASE WHEN l.direction = 'CREDIT' THEN l.amount_minor END), 0) AS credits
            FROM accounts a
            LEFT JOIN ledger_lines l ON l.account_id = a.id
            %s
            GROUP BY a.id, a.code, a.type, a.currency
            ORDER BY a.code
            """;

    private final JdbcClient jdbc;

    public LedgerQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<AccountBalance> balances() {
        return jdbc.sql(BALANCES_SQL.formatted("")).query(LedgerQueries::toBalance).list();
    }

    public Optional<AccountBalance> balance(String code) {
        return jdbc.sql(BALANCES_SQL.formatted("WHERE a.code = :code"))
                .param("code", code)
                .query(LedgerQueries::toBalance)
                .optional();
    }

    public List<EntryView> entriesForPayment(String paymentId) {
        return group(jdbc.sql(ENTRY_LINES_SQL.formatted("WHERE e.payment_id = :paymentId", "e.id, l.id"))
                .param("paymentId", paymentId)
                .query(LedgerQueries::toRow)
                .list());
    }

    /** Newest entries first, with their lines. */
    public List<EntryView> recentEntries(int limit) {
        return group(jdbc.sql(ENTRY_LINES_SQL.formatted(
                        "WHERE e.id IN (SELECT id FROM (SELECT id FROM journal_entries ORDER BY id DESC LIMIT :limit) recent)",
                        "e.id DESC, l.id"))
                .param("limit", limit)
                .query(LedgerQueries::toRow)
                .list());
    }

    private static final String ENTRY_LINES_SQL = """
            SELECT e.id, e.event_id, e.payment_id, e.entry_type, e.description, e.created_at,
                   a.code AS account_code, l.direction, l.amount_minor
            FROM journal_entries e
            JOIN ledger_lines l ON l.entry_id = e.id
            JOIN accounts a ON a.id = l.account_id
            %s
            ORDER BY %s
            """;

    private static Row toRow(ResultSet rs, int rowNum) throws SQLException {
        return new Row(rs.getLong("id"), rs.getString("event_id"), rs.getString("payment_id"),
                rs.getString("entry_type"), rs.getString("description"), instant(rs, "created_at"),
                new LineView(rs.getString("account_code"), rs.getString("direction"), rs.getLong("amount_minor")));
    }

    private static List<EntryView> group(List<Row> rows) {
        Map<Long, EntryView> entries = new LinkedHashMap<>();
        for (Row row : rows) {
            entries.computeIfAbsent(row.id(), id -> new EntryView(row.id(), row.eventId(), row.paymentId(),
                    row.entryType(), row.description(), row.createdAt(), new ArrayList<>())).lines().add(row.line());
        }
        return List.copyOf(entries.values());
    }

    /** The whole ledger must balance, and so must every single entry. */
    public IntegrityReport integrity() {
        Map<String, Object> totals = jdbc.sql("""
                        SELECT COALESCE(SUM(CASE WHEN direction = 'DEBIT'  THEN amount_minor END), 0) AS debits,
                               COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amount_minor END), 0) AS credits,
                               (SELECT COUNT(*) FROM journal_entries) AS entries
                        FROM ledger_lines
                        """)
                .query().singleRow();
        List<Long> unbalanced = jdbc.sql("""
                        SELECT entry_id
                        FROM ledger_lines
                        GROUP BY entry_id
                        HAVING SUM(CASE WHEN direction = 'DEBIT' THEN amount_minor ELSE -amount_minor END) <> 0
                        """)
                .query(Long.class).list();
        long debits = ((Number) totals.get("debits")).longValue();
        long credits = ((Number) totals.get("credits")).longValue();
        long entryCount = ((Number) totals.get("entries")).longValue();
        return new IntegrityReport(debits == credits && unbalanced.isEmpty(), debits, credits, entryCount, unbalanced);
    }

    private static AccountBalance toBalance(ResultSet rs, int rowNum) throws SQLException {
        AccountType type = AccountType.valueOf(rs.getString("type"));
        long debits = rs.getLong("debits");
        long credits = rs.getLong("credits");
        return new AccountBalance(rs.getString("code"), type, rs.getString("currency"),
                debits, credits, type.balance(debits, credits));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private record Row(long id, String eventId, String paymentId, String entryType, String description,
                       Instant createdAt, LineView line) {
    }

    /** @param balance on the account's normal side: positive means the account "holds" money */
    public record AccountBalance(String code, AccountType type, String currency, long debits, long credits,
                                 long balance) {
    }

    public record EntryView(long id, String eventId, String paymentId, String entryType, String description,
                            Instant createdAt, List<LineView> lines) {
    }

    public record LineView(String account, String direction, long amount) {
    }

    public record IntegrityReport(boolean balanced, long totalDebits, long totalCredits, long entries,
                                  List<Long> unbalancedEntryIds) {
    }
}
