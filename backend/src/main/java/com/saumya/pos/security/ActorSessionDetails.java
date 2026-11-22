package com.saumya.pos.security;

/** Server-created metadata binding the authenticated principal to one persisted actor-session family. */
public record ActorSessionDetails(String actorType, Long tenantId, String membershipId, String family) {
    public ActorSessionDetails {
        if (actorType == null || !java.util.Set.of("PLATFORM", "VENDOR", "CUSTOMER").contains(actorType)) {
            throw new IllegalArgumentException("A recognized actor type is required");
        }
        if ("VENDOR".equals(actorType) != (tenantId != null && membershipId != null)) {
            throw new IllegalArgumentException("Vendor actor scope must include tenant and membership; commercial global actors must not");
        }
        if (family == null || family.isBlank()) throw new IllegalArgumentException("Refresh family is required");
    }
}
