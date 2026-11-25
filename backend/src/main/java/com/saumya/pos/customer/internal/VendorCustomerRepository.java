package com.saumya.pos.customer.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VendorCustomerRepository extends JpaRepository<VendorCustomer,String> {
    boolean existsByTenantAndCustomer(long tenant,long customer);
    boolean existsByTenantAndId(long tenant,String relationshipId);

    @Query("""
        select new com.saumya.pos.customer.internal.VendorCustomerRelationshipRow(
            tc.id, tc.customer,
            coalesce(nullif(cp.displayName, ''), concat('Customer ', tc.customer)),
            tc.consent,
            tc.provenance
        )
        from VendorCustomer tc, CustomerProfile cp
        where cp.id=tc.customer and tc.tenant=:tenant
        order by tc.consent desc, tc.customer desc
        """)
    List<VendorCustomerRelationshipRow> findCustomerRelationships(@Param("tenant") long tenant);
}
