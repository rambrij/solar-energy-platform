package com.saumya.pos.marketplace.internal;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Quotation q where q.tenantId=:tenant and q.id=:id")
    Optional<Quotation> lockTenant(@Param("tenant") long tenant, @Param("id") long id);

    @Query("""
        select q from Quotation q, MarketplaceEnquiry e
        where q.tenantId=:tenant and e.tenantId=q.tenantId and e.id=q.enquiryId
          and (:allLeads=true or e.assignedTo=:username)
        order by q.createdAt desc, q.id desc
        """)
    List<Quotation> visibleToVendor(@Param("tenant") long tenant, @Param("allLeads") boolean allLeads,
        @Param("username") String username, Pageable pageable);

    List<Quotation> findTop100ByCustomerIdOrderByCreatedAtDescIdDesc(long customerId);
}
