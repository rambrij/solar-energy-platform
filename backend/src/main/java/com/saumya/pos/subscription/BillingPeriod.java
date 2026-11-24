package com.saumya.pos.subscription;

import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

final class BillingPeriod {
    private BillingPeriod() {}

    static LocalDate end(LocalDate start, LocalDate suppliedEnd, boolean usePlanInterval, String interval) {
        if (!supportedDate(start))
            throw new ResponseStatusException(BAD_REQUEST, "A valid billing period start is required");
        if (usePlanInterval && suppliedEnd != null)
            throw new ResponseStatusException(BAD_REQUEST, "Omit the period end when using the plan interval");

        LocalDate end = usePlanInterval ? renewalEnd(start,start,interval) : suppliedEnd;

        if (!supportedDate(end) || !end.isAfter(start))
            throw new ResponseStatusException(BAD_REQUEST, "Subscription period end must be after its start");
        return end;
    }

    static LocalDate renewalEnd(LocalDate start,LocalDate anchor,String interval){
        int months=switch(interval){
            case "MONTHLY" -> 1;
            case "QUARTERLY" -> 3;
            case "YEARLY" -> 12;
            default -> throw new IllegalStateException("Stored plan billing interval is unsupported");
        };
        YearMonth month=YearMonth.from(start).plusMonths(months);
        return month.atDay(Math.min(anchor.getDayOfMonth(),month.lengthOfMonth()));
    }

    private static boolean supportedDate(LocalDate date) {
        return date != null && date.getYear() >= 1000 && date.getYear() <= 9999;
    }
}
