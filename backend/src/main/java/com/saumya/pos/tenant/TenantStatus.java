package com.saumya.pos.tenant;

/** Vendor tenant lifecycle (SRS TEN-003). Separate from the subscription state machine. */
public enum TenantStatus { PENDING, UNDER_REVIEW, ACTIVE, SUSPENDED, CLOSED }
