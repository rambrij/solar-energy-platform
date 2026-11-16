package com.saumya.pos.checkout.internal;
import java.math.BigDecimal;
public record CashierSalesHeader(String cashier,long paidOrders,BigDecimal sales,BigDecimal cashReceived,BigDecimal changeGiven,long invoices){}
