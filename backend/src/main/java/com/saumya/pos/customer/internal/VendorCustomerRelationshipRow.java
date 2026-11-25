package com.saumya.pos.customer.internal;

import java.time.Instant;

public record VendorCustomerRelationshipRow(long customerId, String displayName, Instant consentAt, String provenance) {}
