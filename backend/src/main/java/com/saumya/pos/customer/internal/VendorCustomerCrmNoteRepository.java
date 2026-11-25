package com.saumya.pos.customer.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorCustomerCrmNoteRepository extends JpaRepository<VendorCustomerCrmNote,Long> {
    List<VendorCustomerCrmNote> findAllByTenantIdAndTenantCustomerIdOrderByCreatedAtDescIdDesc(long tenantId,String relationshipId);
    Optional<VendorCustomerCrmNote> findByTenantIdAndTenantCustomerIdAndId(long tenantId,String relationshipId,long id);
}
