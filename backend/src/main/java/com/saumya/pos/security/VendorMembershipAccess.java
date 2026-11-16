package com.saumya.pos.security;

/** Read-only membership query contract consumed by authentication and authorization. */
public interface VendorMembershipAccess extends VendorMembershipResolver {
    boolean hasMembership(String username);
}
