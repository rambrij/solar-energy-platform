package com.saumya.pos.platform.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformAuditRepository extends JpaRepository<PlatformAuditEvent,String> {
    /** action '' means any action; tenant 0 means any tenant (tenant ids start at 1). */
    @Query(value="select e from PlatformAuditEvent e where (:action='' or e.action=:action) and (:tenant=0 or e.tenantId=:tenant) order by e.occurredAt desc, e.id desc",
           countQuery="select count(e) from PlatformAuditEvent e where (:action='' or e.action=:action) and (:tenant=0 or e.tenantId=:tenant)")
    Page<PlatformAuditEvent> search(@Param("action") String action,@Param("tenant") long tenant,Pageable pageable);
}
