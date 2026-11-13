package com.saumya.pos.checkout.internal;
import java.time.LocalDate;
import java.math.BigDecimal;
public record SalesLineAggregate(LocalDate day,String channel,BigDecimal tax,long units){}
