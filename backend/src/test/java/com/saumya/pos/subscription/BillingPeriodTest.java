package com.saumya.pos.subscription;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

class BillingPeriodTest {
    @Test
    void calculatesCalendarIntervalsAndClampsDatesThatDoNotExistInTheTargetMonth() {
        assertThat(BillingPeriod.end(LocalDate.parse("2028-01-31"),null,true,"MONTHLY"))
            .isEqualTo(LocalDate.parse("2028-02-29"));
        assertThat(BillingPeriod.end(LocalDate.parse("2028-01-31"),null,true,"QUARTERLY"))
            .isEqualTo(LocalDate.parse("2028-04-30"));
        assertThat(BillingPeriod.end(LocalDate.parse("2028-02-29"),null,true,"YEARLY"))
            .isEqualTo(LocalDate.parse("2029-02-28"));
    }

    @Test
    void keepsExplicitEndDatesAndRejectsMissingOrConflictingDateInputs() {
        var start=LocalDate.parse("2028-01-31");
        var manualEnd=LocalDate.parse("2028-03-05");
        assertThat(BillingPeriod.end(start,manualEnd,false,"MONTHLY")).isEqualTo(manualEnd);
        assertThatThrownBy(()->BillingPeriod.end(start,null,false,"MONTHLY"))
            .isInstanceOf(ResponseStatusException.class).hasFieldOrPropertyWithValue("status",BAD_REQUEST);
        assertThatThrownBy(()->BillingPeriod.end(start,manualEnd,true,"MONTHLY"))
            .isInstanceOf(ResponseStatusException.class).hasFieldOrPropertyWithValue("status",BAD_REQUEST);
        assertThatThrownBy(()->BillingPeriod.end(LocalDate.parse("9999-12-01"),null,true,"MONTHLY"))
            .isInstanceOf(ResponseStatusException.class).hasFieldOrPropertyWithValue("status",BAD_REQUEST);
    }
}
