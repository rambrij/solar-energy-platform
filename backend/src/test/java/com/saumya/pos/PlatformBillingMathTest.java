package com.saumya.pos;

import com.saumya.pos.platform.InvoiceMath;
import com.saumya.pos.platform.InvoiceMath.Interval;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlatformBillingMathTest {
    private static BigDecimal d(String v){return new BigDecimal(v);}
    @Test void taxExclusivePriceAddsTax(){
        var a=InvoiceMath.amounts(d("1000.00"),d("18.00"),false);
        assertEquals(d("1000.00"),a.amount());assertEquals(d("180.00"),a.tax());assertEquals(d("1180.00"),a.total());
    }
    @Test void taxInclusivePriceEmbedsTax(){
        var a=InvoiceMath.amounts(d("1180.00"),d("18.00"),true);
        assertEquals(d("1000.00"),a.amount());assertEquals(d("180.00"),a.tax());assertEquals(d("1180.00"),a.total());
    }
    @Test void roundingIsHalfUpAndTotalsAlwaysReconcile(){
        var ex=InvoiceMath.amounts(d("99.99"),d("5.00"),false);assertEquals(d("5.00"),ex.tax());assertEquals(d("104.99"),ex.total());
        var in=InvoiceMath.amounts(d("100.00"),d("7.50"),true);assertEquals(in.total(),in.amount().add(in.tax()));
        assertEquals(d("0.00"),InvoiceMath.amounts(d("0.00"),d("18.00"),false).total());
    }
    @Test void invalidTermsAreRejected(){
        assertThrows(IllegalArgumentException.class,()->InvoiceMath.amounts(d("-1.00"),d("18.00"),false));
        assertThrows(IllegalArgumentException.class,()->InvoiceMath.amounts(d("1.001"),d("18.00"),false));
        assertThrows(IllegalArgumentException.class,()->InvoiceMath.amounts(d("1.00"),d("100.01"),false));
        assertThrows(IllegalArgumentException.class,()->InvoiceMath.amounts(d("1.00"),d("-0.01"),true));
        assertThrows(IllegalArgumentException.class,()->InvoiceMath.amounts(null,d("1"),true));
    }
    @Test void periodEndFollowsTheBillingInterval(){
        Instant start=Instant.parse("2026-01-31T00:00:00Z");
        assertEquals(Instant.parse("2026-02-28T00:00:00Z"),InvoiceMath.periodEnd(start,Interval.MONTHLY));
        assertEquals(Instant.parse("2026-04-30T00:00:00Z"),InvoiceMath.periodEnd(start,Interval.QUARTERLY));
        assertEquals(Instant.parse("2027-01-31T00:00:00Z"),InvoiceMath.periodEnd(start,Interval.YEARLY));
    }
}
