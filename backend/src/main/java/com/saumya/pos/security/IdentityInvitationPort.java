package com.saumya.pos.security;

import java.time.Instant;

/** Identity invitation boundary consumed by tenant employee administration. */
public interface IdentityInvitationPort {
    record Invitation(String username,String token,Instant expiresAt,boolean accountAlreadyActive) {}
    void ensureVendorEligible(String username);
    Invitation issueInvitation(String username);
    void acceptInvitation(String token,String password);
}
