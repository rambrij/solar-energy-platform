package com.saumya.pos;

import com.saumya.pos.checkout.TenderPlan;
import com.saumya.pos.checkout.TenderPlan.Entry;
import com.saumya.pos.checkout.TenderPlan.Method;
import com.saumya.pos.checkout.TenderPlan.TenderException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TenderPlanTest {
    private static BigDecimal d(String v) { return new BigDecimal(v); }
    private static Entry e(Method m, String v) { return new Entry(m, d(v)); }
    private static String code(Runnable r) { return catchThrowableOfType(TenderException.class, r::run).code(); }

    @Test void cashOnlyWithChange() {
        var r = TenderPlan.settle(d("100.00"), List.of(e(Method.CASH, "150.00")));
        assertThat(r.cashReceived()).isEqualByComparingTo("150.00");
        assertThat(r.changeGiven()).isEqualByComparingTo("50.00");
        assertThat(r.cashApplied()).isEqualByComparingTo("100.00");
        assertThat(r.totalApplied()).isEqualByComparingTo("100.00");
        assertThat(r.isSplit()).isFalse();
    }
    @Test void cashPlusUpiSplitSettlesExactlyAndChangeComesFromCashOnly() {
        var r = TenderPlan.settle(d("100.00"), List.of(e(Method.UPI, "60.00"), e(Method.CASH, "50.00")));
        assertThat(r.cashApplied()).isEqualByComparingTo("40.00");
        assertThat(r.changeGiven()).isEqualByComparingTo("10.00");
        assertThat(r.applied().get(Method.UPI)).isEqualByComparingTo("60.00");
        assertThat(r.totalApplied()).isEqualByComparingTo("100.00");
        assertThat(r.isSplit()).isTrue();
    }
    @Test void threeWaySplitWithExactCash() {
        var r = TenderPlan.settle(d("100.01"), List.of(e(Method.CARD, "30.00"), e(Method.UPI, "30.01"), e(Method.CASH, "40.00")));
        assertThat(r.changeGiven()).isEqualByComparingTo("0.00");
        assertThat(r.totalApplied()).isEqualByComparingTo("100.01");
    }
    @Test void nonCashOnlyMustMatchExactly() {
        assertThat(TenderPlan.settle(d("80.00"), List.of(e(Method.UPI, "30.00"), e(Method.CARD, "50.00"))).totalApplied()).isEqualByComparingTo("80.00");
        assertThat(code(() -> TenderPlan.settle(d("80.00"), List.of(e(Method.UPI, "79.99"))))).isEqualTo("UNDER_TENDER");
    }
    @Test void underTenderedCashIsRejected() {
        assertThat(code(() -> TenderPlan.settle(d("100.00"), List.of(e(Method.UPI, "60.00"), e(Method.CASH, "39.99"))))).isEqualTo("UNDER_TENDER");
    }
    @Test void nonCashOverDueIsRejectedNotTurnedIntoChange() {
        assertThat(code(() -> TenderPlan.settle(d("100.00"), List.of(e(Method.CARD, "100.01"))))).isEqualTo("OVER_TENDER");
        assertThat(code(() -> TenderPlan.settle(d("100.00"), List.of(e(Method.UPI, "70.00"), e(Method.CARD, "40.00"))))).isEqualTo("OVER_TENDER");
    }
    @Test void cashTenderRejectedWhenNonCashAlreadySettles() {
        assertThat(code(() -> TenderPlan.settle(d("100.00"), List.of(e(Method.UPI, "100.00"), e(Method.CASH, "5.00"))))).isEqualTo("CASH_NOT_NEEDED");
    }
    @Test void duplicateMethodRejected() {
        assertThat(code(() -> TenderPlan.settle(d("100.00"), List.of(e(Method.UPI, "10.00"), e(Method.UPI, "90.00"))))).isEqualTo("DUPLICATE_METHOD");
    }
    @Test void invalidAmountsRejected() {
        assertThat(code(() -> TenderPlan.settle(d("100.00"), List.of(e(Method.CASH, "0.00"))))).isEqualTo("AMOUNT_INVALID");
        assertThat(code(() -> TenderPlan.settle(d("100.00"), List.of(e(Method.CASH, "-5.00"))))).isEqualTo("AMOUNT_INVALID");
        assertThat(code(() -> TenderPlan.settle(d("100.00"), List.of(e(Method.CASH, "100.001"))))).isEqualTo("AMOUNT_INVALID");
    }
    @Test void invalidDueAndEmptyAndNullRejected() {
        assertThat(code(() -> TenderPlan.settle(d("0.00"), List.of(e(Method.CASH, "1.00"))))).isEqualTo("DUE_INVALID");
        assertThat(code(() -> TenderPlan.settle(null, List.of(e(Method.CASH, "1.00"))))).isEqualTo("DUE_INVALID");
        assertThat(code(() -> TenderPlan.settle(d("10.00"), List.of()))).isEqualTo("NO_TENDER");
        assertThat(code(() -> TenderPlan.settle(d("10.00"), null))).isEqualTo("NO_TENDER");
        assertThat(code(() -> TenderPlan.settle(d("10.00"), java.util.Arrays.asList(new Entry[]{null})))).isEqualTo("ENTRY_INVALID");
    }
    @Test void moreThanFourTendersRejectedBeforeAnythingElse() {
        var five = List.of(e(Method.CASH, "1.00"), e(Method.UPI, "1.00"), e(Method.CARD, "1.00"), e(Method.UPI, "1.00"), e(Method.CARD, "1.00"));
        assertThat(code(() -> TenderPlan.settle(d("5.00"), five))).isEqualTo("TOO_MANY");
    }
    @Test void remainingNeverNegative() {
        assertThat(TenderPlan.remaining(d("100.00"), List.of(d("30.00"), d("20.00")))).isEqualByComparingTo("50.00");
        assertThat(TenderPlan.remaining(d("100.00"), List.of(d("100.00")))).isEqualByComparingTo("0.00");
        assertThat(TenderPlan.remaining(d("100.00"), List.of(d("120.00")))).isEqualByComparingTo("0.00");
    }
}
