package com.saumya.pos.marketplace.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotationLineRepository extends JpaRepository<QuotationLine, Long> {
    List<QuotationLine> findAllByTenantIdAndRevisionIdOrderByLineNumber(long tenantId, long revisionId);
}
