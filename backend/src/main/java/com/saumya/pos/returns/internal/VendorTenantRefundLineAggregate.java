package com.saumya.pos.returns.internal;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VendorTenantRefundLineAggregate(LocalDate day, BigDecimal beforeTax, BigDecimal tax, long units) { }
