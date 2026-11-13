package com.saumya.pos.checkout.internal;
import java.time.LocalDate;
import java.math.BigDecimal;
public record SalesAggregate(LocalDate day,String channel,String status,long orders,BigDecimal gross,BigDecimal cash,BigDecimal change,long invoices){}
