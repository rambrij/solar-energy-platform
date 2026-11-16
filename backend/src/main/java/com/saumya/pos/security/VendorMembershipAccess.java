package com.saumya.pos.security;

import java.util.Optional;

/** Read-only membership query contract consumed by authentication and authorization. */
public interface VendorMembershipAccess extends VendorMembershipResolver {
    Optional<Access> access(String username);
    boolean hasMembership(String username);
    record Access(long tenant,long branch,String username,java.util.List<String> permissions) {}
}
