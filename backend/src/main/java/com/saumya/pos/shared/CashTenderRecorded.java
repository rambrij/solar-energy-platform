package com.saumya.pos.shared;
import java.math.BigDecimal;
/** Immutable financial fact published atomically when cash is accepted. */
public record CashTenderRecorded(String reference,long tenant,long branch,String session,String order,BigDecimal received,BigDecimal change) {}
