package com.saumya.pos.tenant.internal;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantProfileAuditRepository extends JpaRepository<TenantProfileAuditEvent,Long> {}
