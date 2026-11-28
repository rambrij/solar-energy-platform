package com.saumya.pos.checkout;

import com.saumya.pos.checkout.internal.SalesAggregate;
import com.saumya.pos.checkout.internal.SalesLineAggregate;
import com.saumya.pos.checkout.internal.VendorTenantAnalyticsRepository;
import com.saumya.pos.checkout.internal.VendorTenantOutletAggregate;
import com.saumya.pos.checkout.internal.VendorTenantProductAggregate;
import com.saumya.pos.checkout.internal.VendorTenantTenderAggregate;
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
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Tenant-wide vendor reporting. It never consumes a client-supplied tenant or branch identifier. */
@Service
@Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class VendorTenantReportService {
    private static final int MAX_RANGE_DAYS = 366;
    private final VendorTenantAnalyticsRepository reports;
    private final AuthorizationContext authorization;
    private final Clock clock;

    public VendorTenantReportService(VendorTenantAnalyticsRepository reports,
            AuthorizationContext authorization, Clock clock) {
        this.reports = reports;
        this.authorization = authorization;
        this.clock = clock;
    }

    public record Totals(long paidOrders, BigDecimal sales, BigDecimal tax, BigDecimal beforeTax,
            BigDecimal cashReceived, BigDecimal changeGiven, BigDecimal cashApplied,
            long units, long invoices, long distinctCustomers, long unpaidOrders,
            BigDecimal unpaidAmount, long cancelledOrders, long expiredOrders, BigDecimal discounts) { }
    public record Channel(String channel, Totals totals) { }
    public record Day(LocalDate date, Totals totals) { }
    public record Outlet(String outlet, String channel, long orders, long paidOrders,
            BigDecimal paidSales, long unpaidOrders, BigDecimal unpaidAmount) { }
    public record Product(long productId, String productName, long units, BigDecimal sales, BigDecimal discounts) { }
    public record Tender(String channel, String method, String origin, long tenders, BigDecimal confirmedAmount) { }
    public record Report(LocalDate from, LocalDate to, Instant generatedAt, Totals totals,
            List<Channel> channels, List<Day> days, List<Outlet> outlets,
            List<Product> topProducts, List<Tender> confirmedTenders) { }

    private static final class Bucket {
        long paidOrders, units, invoices, unpaidOrders, cancelledOrders, expiredOrders;
        BigDecimal sales = BigDecimal.ZERO, tax = BigDecimal.ZERO, cash = BigDecimal.ZERO,
            change = BigDecimal.ZERO, unpaidAmount = BigDecimal.ZERO, discounts = BigDecimal.ZERO;

        void add(Bucket other) {
            paidOrders += other.paidOrders;
            units += other.units;
            invoices += other.invoices;
            unpaidOrders += other.unpaidOrders;
            cancelledOrders += other.cancelledOrders;
            expiredOrders += other.expiredOrders;
            sales = sales.add(other.sales);
            tax = tax.add(other.tax);
            cash = cash.add(other.cash);
            change = change.add(other.change);
            unpaidAmount = unpaidAmount.add(other.unpaidAmount);
            discounts = discounts.add(other.discounts);
        }

        Totals view(long distinctCustomers) {
            return new Totals(paidOrders, sales, tax, sales.subtract(tax), cash, change,
                cash.subtract(change), units, invoices, distinctCustomers, unpaidOrders,
                unpaidAmount, cancelledOrders, expiredOrders, discounts);
        }
    }

    public Report report(LocalDate requestedFrom, LocalDate requestedTo) {
        var actor = authorization.current();
        actor.requireVendorMembership();
        actor.requirePermission("POS_REPORT_VIEW");

        LocalDate to = requestedTo == null ? LocalDate.now(clock.withZone(ZoneOffset.UTC)) : requestedTo;
        LocalDate from = requestedFrom == null ? to.minusDays(6) : requestedFrom;
        long days = ChronoUnit.DAYS.between(from, to);
        if (from.getYear() < 1970 || to.getYear() > 9998 || from.isAfter(to)
                || days < 0 || days >= MAX_RANGE_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Choose an inclusive UTC date range of 1 to 366 days");
        }

        Instant fromAt = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant untilAt = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        long tenant = actor.tenantId();

        Map<LocalDate, Map<String, Bucket>> daily = new LinkedHashMap<>();
        Map<String, Bucket> byChannel = new LinkedHashMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            daily.put(date, Map.of("ONLINE", new Bucket(), "IN_STORE", new Bucket()));
        }
        byChannel.put("ONLINE", new Bucket());
        byChannel.put("IN_STORE", new Bucket());
        Bucket total = new Bucket();

        List<SalesAggregate> orders = reports.ordersForTenant(tenant, fromAt, untilAt);
        for (SalesAggregate row : orders) {
            Bucket bucket = daily.get(row.day()).get(row.channel());
            applyOrder(bucket, row);
        }
        List<SalesLineAggregate> lines = reports.linesForTenant(tenant, fromAt, untilAt);
        for (SalesLineAggregate row : lines) {
            Bucket bucket = daily.get(row.day()).get(row.channel());
            bucket.tax = bucket.tax.add(zero(row.tax()));
            bucket.units += row.units();
            bucket.discounts = bucket.discounts.add(zero(row.discounts()));
        }

        List<Day> dayViews = new ArrayList<>();
        for (var entry : daily.entrySet()) {
            Bucket dailyTotal = new Bucket();
            for (String channel : List.of("ONLINE", "IN_STORE")) {
                Bucket bucket = entry.getValue().get(channel);
                dailyTotal.add(bucket);
                byChannel.get(channel).add(bucket);
            }
            total.add(dailyTotal);
            dayViews.add(new Day(entry.getKey(), dailyTotal.view(0)));
        }

        List<Channel> channelViews = byChannel.entrySet().stream()
            .map(entry -> new Channel(entry.getKey(), entry.getValue().view(0))).toList();
        long distinctCustomers = reports.distinctPaidCustomersForTenant(tenant, fromAt, untilAt);
        Totals totals = total.view(distinctCustomers);

        List<Outlet> outlets = reports.outletsForTenant(tenant, fromAt, untilAt).stream()
            .map(VendorTenantReportService::outlet).toList();
        List<Product> topProducts = reports.topProductsForTenant(tenant, fromAt, untilAt, PageRequest.of(0, 10))
            .stream().map(VendorTenantReportService::product).toList();
        List<Tender> tenders = reports.confirmedTendersForTenant(tenant, fromAt, untilAt).stream()
            .map(VendorTenantReportService::tender).toList();

        return new Report(from, to, clock.instant(), totals, channelViews, dayViews,
            outlets, topProducts, tenders);
    }

    private static void applyOrder(Bucket bucket, SalesAggregate row) {
        switch (row.status()) {
            case "PAID" -> {
                bucket.paidOrders += row.orders();
                bucket.sales = bucket.sales.add(zero(row.gross()));
                bucket.cash = bucket.cash.add(zero(row.cash()));
                bucket.change = bucket.change.add(zero(row.change()));
                bucket.invoices += row.invoices();
            }
            case "DRAFT" -> {
                bucket.unpaidOrders += row.orders();
                bucket.unpaidAmount = bucket.unpaidAmount.add(zero(row.gross()));
            }
            case "CANCELLED" -> bucket.cancelledOrders += row.orders();
            case "EXPIRED" -> bucket.expiredOrders += row.orders();
            default -> throw new IllegalStateException("Unsupported recorded order state");
        }
    }

    private static Outlet outlet(VendorTenantOutletAggregate row) {
        return new Outlet(row.outlet(), row.channel(), row.orders(), row.paidOrders(),
            zero(row.paidSales()), row.unpaidOrders(), zero(row.unpaidAmount()));
    }

    private static Product product(VendorTenantProductAggregate row) {
        return new Product(row.productId(), row.productName(), row.units(), zero(row.sales()), zero(row.discounts()));
    }

    private static Tender tender(VendorTenantTenderAggregate row) {
        return new Tender(row.channel(), row.method(), row.origin(), row.tenders(), zero(row.confirmedAmount()));
    }

    private static BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
