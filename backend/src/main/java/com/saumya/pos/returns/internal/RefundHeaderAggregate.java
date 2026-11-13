package com.saumya.pos.returns.internal;
import java.math.BigDecimal;
import java.time.LocalDate;
public record RefundHeaderAggregate(LocalDate day,String cashier,long refunds,BigDecimal cash) {}
