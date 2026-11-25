package com.saumya.pos.customer.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorCustomerCrmNoteRepository extends JpaRepository<VendorCustomerCrmNote,Long> {
    List<VendorCustomerCrmNote> findAllByTenantIdAndCustomerIdOrderByCreatedAtDescIdDesc(long tenantId,long customerId);
    Optional<VendorCustomerCrmNote> findByTenantIdAndCustomerIdAndId(long tenantId,long customerId,long id);
}
