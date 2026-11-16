package com.saumya.pos.platform;

import java.time.Instant;
import java.util.List;

public interface PlatformSupportService {
    record SupportAccess(long id,long tenantId,String actor,String purpose,String ticketReference,
                         Instant grantedAt,Instant expiresAt,Instant revokedAt,boolean active) {}
    SupportAccess grant(long tenantId,String actor,String purpose,String ticketReference,Instant expiresAt,String correlationId);
    SupportAccess revoke(long id,String actor,String reason,String correlationId);
    List<SupportAccess> recent();
}
