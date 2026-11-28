package com.saumya.pos.returns.internal;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VendorTenantRefundHeaderAggregate(LocalDate day, long refunds, BigDecimal cash) { }
