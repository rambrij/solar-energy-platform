package com.saumya.pos.platform.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAuditRepository extends JpaRepository<PlatformAuditEvent,Long> {
    Page<PlatformAuditEvent> findAllByOrderByOccurredAtDescIdDesc(Pageable pageable);
    Page<PlatformAuditEvent> findByTenantIdOrderByOccurredAtDescIdDesc(Long tenantId,Pageable pageable);
}
