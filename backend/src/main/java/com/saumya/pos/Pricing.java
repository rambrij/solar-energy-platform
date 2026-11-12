package com.saumya.pos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/** Shared, tax-exclusive line calculation for both marketplace and POS. */
@Component
public class Pricing {
    public record Amounts(BigDecimal net,BigDecimal tax,BigDecimal total) {}
    public Amounts line(BigDecimal price,BigDecimal taxRate,int quantity) {
        BigDecimal net=price.multiply(BigDecimal.valueOf(quantity));
        BigDecimal tax=net.multiply(taxRate).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);
        return new Amounts(net,tax,net.add(tax));
    }
}
