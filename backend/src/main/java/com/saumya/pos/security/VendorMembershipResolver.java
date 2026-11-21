package com.saumya.pos.security;

import java.util.List;
import java.util.Optional;

/** Security-facing contract for resolving the authenticated user's active vendor membership. */
public interface VendorMembershipResolver {
    Optional<Membership> resolve(String username);

    /**
     * The current employee schema identifies a membership by its composite natural key.
     * This canonical identifier is derived only from the server-resolved row.
     */
    record Membership(long tenant,long branch,String username,List<String> permissions) {
        public String id(){return tenant+":"+username;}
    }
}
