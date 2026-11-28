package com.saumya.pos.checkout.internal;

import java.math.BigDecimal;

public record VendorTenantProductAggregate(
    long productId, String productName, long units, BigDecimal sales, BigDecimal discounts
) { }
