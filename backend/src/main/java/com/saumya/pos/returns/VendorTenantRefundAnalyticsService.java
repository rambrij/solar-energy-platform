package com.saumya.pos.returns;

import com.saumya.pos.returns.internal.RefundReportRepository;
import com.saumya.pos.security.AuthorizationContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Tenant-wide cash refund summary; it reports only paid cash outflows recorded by the POS refund workflow. */
@Service
@Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class VendorTenantRefundAnalyticsService {
    private static final int MAX_RANGE_DAYS = 366;
    private final RefundReportRepository reports;
    private final AuthorizationContext authorization;
    private final Clock clock;

    public VendorTenantRefundAnalyticsService(RefundReportRepository reports,
            AuthorizationContext authorization, Clock clock) {
        this.reports = reports;
        this.authorization = authorization;
        this.clock = clock;
    }

    public record Totals(long refunds, long units, BigDecimal cash, BigDecimal beforeTax, BigDecimal tax) { }
    public record Day(LocalDate date, Totals totals) { }
    public record Report(LocalDate from, LocalDate to, Instant generatedAt, Totals totals, List<Day> days) { }

    private static final class Bucket {
        long refunds;
        long units;
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal beforeTax = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;

        void add(Bucket other) {
            refunds += other.refunds;
            units += other.units;
            cash = cash.add(other.cash);
            beforeTax = beforeTax.add(other.beforeTax);
            tax = tax.add(other.tax);
        }

        Totals view() { return new Totals(refunds, units, cash, beforeTax, tax); }
    }

    public Report report(LocalDate requestedFrom, LocalDate requestedTo) {
        var actor = authorization.current();
        actor.requireVendorMembership();
        actor.requirePermissions("POS_REPORT_VIEW", "POS_REFUND_VIEW");

        LocalDate to = requestedTo == null ? LocalDate.now(clock.withZone(ZoneOffset.UTC)) : requestedTo;
        LocalDate from = requestedFrom == null ? to.minusDays(6) : requestedFrom;
        long dayCount = ChronoUnit.DAYS.between(from, to);
        if (from.getYear() < 1970 || to.getYear() > 9998 || from.isAfter(to)
                || dayCount < 0 || dayCount >= MAX_RANGE_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Choose an inclusive UTC date range of 1 to 366 days");
        }

        Instant fromAt = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant untilAt = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Map<LocalDate, Bucket> daily = new LinkedHashMap<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) daily.put(day, new Bucket());
        Bucket total = new Bucket();

        for (var row : reports.headersForTenant(actor.tenantId(), fromAt, untilAt)) {
            Bucket bucket = daily.get(row.day());
            if (bucket == null) throw new IllegalStateException("Refund report row is outside the requested period");
            bucket.refunds += row.refunds();
            bucket.cash = bucket.cash.add(zero(row.cash()));
            total.refunds += row.refunds();
            total.cash = total.cash.add(zero(row.cash()));
        }
        for (var row : reports.linesForTenant(actor.tenantId(), fromAt, untilAt)) {
            Bucket bucket = daily.get(row.day());
            if (bucket == null) throw new IllegalStateException("Refund line is outside the requested period");
            bucket.units += row.units();
            bucket.beforeTax = bucket.beforeTax.add(zero(row.beforeTax()));
            bucket.tax = bucket.tax.add(zero(row.tax()));
            total.units += row.units();
            total.beforeTax = total.beforeTax.add(zero(row.beforeTax()));
            total.tax = total.tax.add(zero(row.tax()));
        }

        List<Day> days = new ArrayList<>();
        daily.forEach((date, bucket) -> days.add(new Day(date, bucket.view())));
        return new Report(from, to, clock.instant(), total.view(), days);
    }

    private static BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
