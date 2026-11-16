package com.saumya.pos.security;

import java.util.List;
import java.util.Optional;

/** Security-facing contract for resolving the authenticated user's active vendor membership. */
public interface VendorMembershipResolver {
    Optional<Membership> resolve(String username);

    record Membership(long tenant, long branch, String username, List<String> permissions) {}
}
