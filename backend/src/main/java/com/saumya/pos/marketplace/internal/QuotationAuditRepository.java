package com.saumya.pos.marketplace.internal;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotationAuditRepository extends JpaRepository<QuotationAuditEvent, Long> {}
