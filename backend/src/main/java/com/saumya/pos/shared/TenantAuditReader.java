package com.saumya.pos.shared;

import java.time.Instant;
import java.util.List;

/**
 * Narrow, read-only port through which an audited support-access grant may read a tenant's own operational audit trail.
 * Implemented by the module that owns that data; the platform module depends only on this interface.
 */
public interface TenantAuditReader {
    record AuditEntry(String actor,String action,String resourceId,Instant occurredAt) {}
    List<AuditEntry> recentAudit(long tenantId,int limit);
}
