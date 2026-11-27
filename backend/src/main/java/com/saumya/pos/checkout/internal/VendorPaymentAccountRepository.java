package com.saumya.pos.checkout.internal;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VendorPaymentAccountRepository extends JpaRepository<VendorPaymentAccount,Long> {
    List<VendorPaymentAccount> findTop10ByTenantIdOrderByUpdatedAtDesc(long tenantId);
    Optional<VendorPaymentAccount> findByTenantIdAndProviderKey(long tenantId,String providerKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select a from VendorPaymentAccount a
        where a.tenantId=:tenant and a.providerKey=:provider
        """)
    Optional<VendorPaymentAccount> lockTenantProvider(@Param("tenant") long tenant,
        @Param("provider") String provider);
}
