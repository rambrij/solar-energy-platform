package com.saumya.pos.platform;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;

/** Pure subscription billing maths. Operates on the terms snapshotted on the subscription, never on live plan rows. */
public final class InvoiceMath {
    public enum Interval { MONTHLY(1),QUARTERLY(3),YEARLY(12); private final int months;Interval(int m){months=m;} public int months(){return months;} }
    public record Amounts(BigDecimal amount,BigDecimal tax,BigDecimal total) {}
    private static final BigDecimal HUNDRED=new BigDecimal("100");
    private InvoiceMath(){}

    /** Tax-exclusive: total = price + tax. Tax-inclusive: total = price and tax is the embedded portion. */
    public static Amounts amounts(BigDecimal price,BigDecimal taxRate,boolean taxInclusive){
        if(price==null||price.signum()<0||price.scale()>2)throw new IllegalArgumentException("Price must be a non-negative amount with at most two decimals");
        if(taxRate==null||taxRate.signum()<0||taxRate.compareTo(HUNDRED)>0)throw new IllegalArgumentException("Tax rate must be between 0 and 100");
        if(taxInclusive){
            BigDecimal tax=price.multiply(taxRate).divide(HUNDRED.add(taxRate),2,RoundingMode.HALF_UP);
            return new Amounts(price.subtract(tax).setScale(2),tax,price.setScale(2));
        }
        BigDecimal tax=price.multiply(taxRate).divide(HUNDRED,2,RoundingMode.HALF_UP);
        return new Amounts(price.setScale(2),tax,price.add(tax).setScale(2));
    }
    public static Instant periodEnd(Instant start,Interval interval){return start.atZone(ZoneOffset.UTC).plusMonths(interval.months()).toInstant();}
}
