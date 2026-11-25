package com.saumya.pos.customer.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorCustomerCrmNoteRepository extends JpaRepository<VendorCustomerCrmNote,Long> {
    List<VendorCustomerCrmNote> findAllByTenantIdAndRelationshipIdOrderByCreatedAtDescIdDesc(
        long tenantId,String relationshipId);
    Optional<VendorCustomerCrmNote> findByTenantIdAndRelationshipIdAndId(
        long tenantId,String relationshipId,long id);
}
