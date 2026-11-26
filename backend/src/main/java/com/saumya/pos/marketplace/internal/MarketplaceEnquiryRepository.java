package com.saumya.pos.marketplace.internal;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceEnquiryRepository extends JpaRepository<MarketplaceEnquiry,Long> {
    long countByTenantIdAndRequesterHashAndCreatedAtAfter(long tenantId,String requesterHash,Instant after);
    Page<MarketplaceEnquiry> findAllByTenantIdOrderByCreatedAtDescIdDesc(long tenantId,Pageable pageable);
}
