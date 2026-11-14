package com.saumya.pos.returns.internal;
import java.math.BigDecimal;
import java.time.LocalDate;
public record RefundLineAggregate(LocalDate day,String cashier,BigDecimal beforeTax,BigDecimal tax,long units) {}
