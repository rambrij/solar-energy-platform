package com.saumya.pos.subscription;

import java.time.LocalDate;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

final class BillingPeriod {
    private BillingPeriod() {}

    static LocalDate end(LocalDate start, LocalDate suppliedEnd, boolean usePlanInterval, String interval) {
        if (!supportedDate(start))
            throw new ResponseStatusException(BAD_REQUEST, "A valid billing period start is required");
        if (usePlanInterval && suppliedEnd != null)
            throw new ResponseStatusException(BAD_REQUEST, "Omit the period end when using the plan interval");

        LocalDate end = usePlanInterval ? switch (interval) {
            case "MONTHLY" -> start.plusMonths(1);
            case "QUARTERLY" -> start.plusMonths(3);
            case "YEARLY" -> start.plusYears(1);
            default -> throw new IllegalStateException("Stored plan billing interval is unsupported");
        } : suppliedEnd;

        if (!supportedDate(end) || !end.isAfter(start))
            throw new ResponseStatusException(BAD_REQUEST, "Subscription period end must be after its start");
        return end;
    }

    private static boolean supportedDate(LocalDate date) {
        return date != null && date.getYear() >= 1000 && date.getYear() <= 9999;
    }
}
