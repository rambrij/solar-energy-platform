package com.saumya.pos.platform;

import java.time.Instant;
import java.util.List;

public interface PlatformAuditService extends com.saumya.pos.shared.PlatformAuditPort {
    record AuditEntry(long id,String actor,String action,String resourceType,String resourceId,Long tenantId,
                      String reason,String outcome,String correlationId,Instant occurredAt) {}
    record AuditPage(int page,int size,long totalElements,int totalPages,List<AuditEntry> items) {}

    AuditPage search(int page,int size,Long tenantId);
}
