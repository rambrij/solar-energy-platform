package com.saumya.pos.marketplace.internal;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuotationRevisionRepository extends JpaRepository<QuotationRevision, Long> {
    Optional<QuotationRevision> findByTenantIdAndQuotationIdAndRevisionNumber(
        long tenantId, long quotationId, int revisionNumber);

    Optional<QuotationRevision> findTopByTenantIdAndQuotationIdOrderByRevisionNumberDesc(
        long tenantId, long quotationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select r from QuotationRevision r
        where r.tenantId=:tenant and r.quotationId=:quotation and r.revisionNumber=:revision
        """)
    Optional<QuotationRevision> lockRevision(@Param("tenant") long tenant,
        @Param("quotation") long quotation, @Param("revision") int revision);
}
