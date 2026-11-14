package com.saumya.pos.checkout;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Pure split-tender settlement rules (SRS POS-013). No persistence, no Spring.
 *
 * <ul>
 *   <li>1..{@link #MAX_ENTRIES} entries, at most one per method, each amount positive with at most two decimals.</li>
 *   <li>UPI/CARD entries are exact applied amounts and can never exceed the amount due.</li>
 *   <li>A CASH entry is the cash handed over. It may exceed what it settles; the excess is change, modelled
 *       separately from the applied amount.</li>
 *   <li>The applied amounts of all tenders must equal the amount due exactly before an order may become PAID.</li>
 * </ul>
 */
public final class TenderPlan {
    public static final int MAX_ENTRIES = 4;

    public enum Method { CASH, UPI, CARD }

    public record Entry(Method method, BigDecimal amount) {}

    /** {@code applied} holds what each method settles; for CASH that is received minus change. */
    public record Result(BigDecimal cashReceived, BigDecimal changeGiven, BigDecimal cashApplied,
                         Map<Method, BigDecimal> applied) {
        public BigDecimal totalApplied() {
            return applied.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        public boolean isSplit() { return applied.size() > 1; }
    }

    public static final class TenderException extends RuntimeException {
        private final String code;
        public TenderException(String code, String message) { super(message); this.code = code; }
        public String code() { return code; }
    }

    private TenderPlan() {}

    public static Result settle(BigDecimal due, List<Entry> entries) {
        if (due == null || due.signum() <= 0) fail("DUE_INVALID", "Amount due must be positive");
        if (due.scale() > 2) fail("DUE_INVALID", "Amount due must have at most two decimals");
        if (entries == null || entries.isEmpty()) fail("NO_TENDER", "At least one tender is required");
        if (entries.size() > MAX_ENTRIES) fail("TOO_MANY", "At most " + MAX_ENTRIES + " tenders are allowed");

        var seen = new EnumMap<Method, BigDecimal>(Method.class);
        for (var entry : entries) {
            if (entry == null || entry.method() == null || entry.amount() == null) fail("ENTRY_INVALID", "Tender method and amount are required");
            if (entry.amount().signum() <= 0) fail("AMOUNT_INVALID", "Tender amounts must be positive");
            if (entry.amount().scale() > 2) fail("AMOUNT_INVALID", "Tender amounts must have at most two decimals");
            if (seen.put(entry.method(), entry.amount()) != null) fail("DUPLICATE_METHOD", "Use each tender method at most once");
        }

        BigDecimal nonCash = BigDecimal.ZERO;
        for (var e : seen.entrySet()) if (e.getKey() != Method.CASH) nonCash = nonCash.add(e.getValue());
        if (nonCash.compareTo(due) > 0) fail("OVER_TENDER", "Non-cash tenders exceed the amount due");

        BigDecimal remaining = due.subtract(nonCash);
        BigDecimal cash = seen.get(Method.CASH);
        var applied = new EnumMap<Method, BigDecimal>(Method.class);
        for (var e : seen.entrySet()) if (e.getKey() != Method.CASH) applied.put(e.getKey(), scale(e.getValue()));

        if (cash == null) {
            if (remaining.signum() != 0) fail("UNDER_TENDER", "Tenders must settle the full amount due; " + scale(remaining).toPlainString() + " remains");
            return new Result(BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), applied);
        }
        if (remaining.signum() == 0) fail("CASH_NOT_NEEDED", "Non-cash tenders already settle the amount due; remove the cash tender");
        if (cash.compareTo(remaining) < 0) fail("UNDER_TENDER", "Cash received is below the remaining amount due of " + scale(remaining).toPlainString());
        BigDecimal change = cash.subtract(remaining);
        applied.put(Method.CASH, scale(remaining));
        return new Result(scale(cash), scale(change), scale(remaining), applied);
    }

    /** Remaining amount due after the given confirmed non-cash applied amounts; never negative. */
    public static BigDecimal remaining(BigDecimal due, List<BigDecimal> appliedSoFar) {
        BigDecimal paid = new ArrayList<>(appliedSoFar).stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal left = due.subtract(paid);
        return left.signum() < 0 ? BigDecimal.ZERO.setScale(2) : scale(left);
    }

    private static BigDecimal scale(BigDecimal v) { return v.setScale(2, java.math.RoundingMode.UNNECESSARY); }
    private static void fail(String code, String message) { throw new TenderException(code, message); }
}
