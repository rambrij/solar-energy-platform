package com.saumya.pos.shared;

/** Minimal audit-writing boundary for modules that emit platform audit events. */
public interface PlatformAuditPort {
    void record(String actor,String action,String resourceType,String resourceId,Long tenantId,String reason,String correlationId);
}
