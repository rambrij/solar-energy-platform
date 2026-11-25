package com.saumya.pos.customer.internal;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorAccessAuditRepository extends JpaRepository<VendorAccessAuditEvent,Long> {}
