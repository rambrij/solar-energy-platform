package com.saumya.pos.checkout.internal;

import java.math.BigDecimal;

public record VendorTenantOutletAggregate(
    String outlet, String channel, long orders, long paidOrders, BigDecimal paidSales,
    long unpaidOrders, BigDecimal unpaidAmount
) { }
