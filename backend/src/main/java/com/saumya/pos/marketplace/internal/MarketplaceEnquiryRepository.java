package com.saumya.pos.marketplace.internal;

import java.time.Instant;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketplaceEnquiryRepository extends JpaRepository<MarketplaceEnquiry,Long> {
    long countByTenantIdAndRequesterHashAndCreatedAtAfter(long tenantId,String requesterHash,Instant after);
    Page<MarketplaceEnquiry> findAllByTenantIdOrderByCreatedAtDescIdDesc(long tenantId,Pageable pageable);
    Page<MarketplaceEnquiry> findAllByTenantIdAndAssignedToOrderByCreatedAtDescIdDesc(
        long tenantId,String assignedTo,Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from MarketplaceEnquiry e where e.tenantId=:tenantId and e.id=:id")
    Optional<MarketplaceEnquiry> findByTenantIdAndIdForUpdate(
        @Param("tenantId") long tenantId,@Param("id") long id);
}
