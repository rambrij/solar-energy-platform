package com.saumya.pos.identity;

import java.time.Instant;

public interface IdentityProvisioningService {
    record Invitation(String username,String token,Instant expiresAt,boolean accountAlreadyActive) {}
    Invitation issueInvitation(String username);
    void acceptInvitation(String token,String password);
}
