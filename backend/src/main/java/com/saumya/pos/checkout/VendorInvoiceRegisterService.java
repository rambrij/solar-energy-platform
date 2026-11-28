package com.saumya.pos.checkout;

import com.saumya.pos.checkout.internal.VendorInvoiceRegisterRepository;
import com.saumya.pos.security.AuthorizationContext;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Read-only vendor finance view over immutable invoice snapshots and confirmed checkout tenders. */
@Service
@Transactional(readOnly = true)
public class VendorInvoiceRegisterService {
    private static final int MAX_RANGE_DAYS = 366;
    private final VendorInvoiceRegisterRepository invoices;
    private final AuthorizationContext authorization;
    private final Clock clock;

    public VendorInvoiceRegisterService(VendorInvoiceRegisterRepository invoices,
            AuthorizationContext authorization, Clock clock) {
        this.invoices = invoices;
        this.authorization = authorization;
        this.clock = clock;
    }

    public record Invoice(String orderId, String invoiceNumber, String sellerName, String outletName,
            BigDecimal total, Instant issuedAt, String channel, String paymentMethod,
            String paymentReference, BigDecimal confirmedPayments) { }

    public record Totals(long invoiceCount, BigDecimal invoicedAmount, BigDecimal confirmedPayments,
            long onlineInvoices, long inStoreInvoices) { }

    public record Page(List<Invoice> items, Totals totals, LocalDate from, LocalDate to,
            String channel, int page, int size, long totalElements, int totalPages,
            Instant generatedAt) { }

    public Page list(LocalDate requestedFrom, LocalDate requestedTo, String requestedChannel,
            int requestedPage, int requestedSize) {
        var actor = authorization.current();
        actor.requireVendorMembership();
        actor.requirePermission("INVOICE_VIEW");

        String channel = requestedChannel == null ? "ALL" : requestedChannel.strip().toUpperCase(Locale.ROOT);
        if (!List.of("ALL", "ONLINE", "IN_STORE").contains(channel))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Channel must be ALL, ONLINE or IN_STORE");
        if (requestedPage < 0 || requestedPage > 10000 || requestedSize < 1 || requestedSize > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be 0..10000 and size 1..100");

        LocalDate to = requestedTo == null ? LocalDate.now(clock) : requestedTo;
        LocalDate from = requestedFrom == null ? to.minusDays(29) : requestedFrom;
        long elapsedDays = ChronoUnit.DAYS.between(from, to);
        if (from.isAfter(to) || elapsedDays < 0 || elapsedDays >= MAX_RANGE_DAYS
                || from.getYear() < 1970 || to.getYear() > 9998)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Choose an inclusive UTC date range of 1 to 366 days");

        Timestamp fromAt = Timestamp.from(from.atStartOfDay(ZoneOffset.UTC).toInstant());
        Timestamp untilAt = Timestamp.from(to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        Object[] aggregate = invoices.totals(actor.tenantId(), fromAt, untilAt, channel);
        Totals totals = new Totals(number(aggregate, 0).longValue(), money(aggregate, 1),
            money(aggregate, 2), number(aggregate, 3).longValue(), number(aggregate, 4).longValue());

        long offsetLong = (long) requestedPage * requestedSize;
        if (offsetLong > Integer.MAX_VALUE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page offset is too large");
        @SuppressWarnings("unchecked")
        List<Object[]> pageRows = (List<Object[]>) invoices.page(actor.tenantId(), fromAt, untilAt,
            channel, requestedSize, (int) offsetLong);
        List<Invoice> rows = pageRows.stream().map(VendorInvoiceRegisterService::invoice).toList();
        long totalPageCount = totals.invoiceCount() == 0 ? 0
            : (totals.invoiceCount() + requestedSize - 1) / requestedSize;
        int totalPages = Math.toIntExact(totalPageCount);

        return new Page(rows, totals, from, to, channel, requestedPage, requestedSize,
            totals.invoiceCount(), totalPages, clock.instant());
    }

    private static Invoice invoice(Object[] row) {
        if (row == null || row.length < 10) throw new IllegalStateException("Invoice register row is incomplete");
        Object issuedAt = row[5];
        Instant issued = issuedAt instanceof Timestamp timestamp ? timestamp.toInstant()
            : issuedAt instanceof Instant instant ? instant
            : throwDateType(issuedAt);
        return new Invoice((String) row[0], (String) row[1], (String) row[2], (String) row[3],
            (BigDecimal) row[4], issued, (String) row[6], (String) row[7],
            (String) row[8], money(row, 9));
    }

    private static Instant throwDateType(Object value) {
        throw new IllegalStateException("Unsupported invoice issue timestamp type: "
            + (value == null ? "null" : value.getClass().getName()));
    }

    private static Number number(Object[] row, int index) {
        if (row == null || row.length <= index || !(row[index] instanceof Number value))
            throw new IllegalStateException("Invoice register aggregate is incomplete");
        return value;
    }

    private static BigDecimal money(Object[] row, int index) {
        if (row == null || row.length <= index || row[index] == null) return BigDecimal.ZERO;
        Object value = row[index];
        if (value instanceof BigDecimal amount) return amount;
        if (value instanceof Number number) return new BigDecimal(number.toString());
        throw new IllegalStateException("Unsupported invoice register amount type");
    }
}
