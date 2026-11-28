package com.saumya.pos.checkout.internal;

import java.math.BigDecimal;

public record VendorTenantTenderAggregate(
    String channel, String method, String origin, long tenders, BigDecimal confirmedAmount
) { }
