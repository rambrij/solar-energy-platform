package com.saumya.pos.checkout.internal;
import java.math.BigDecimal;
public record CashierSalesLines(String cashier,BigDecimal tax,long units,BigDecimal discounts){}
