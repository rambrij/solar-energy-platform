package com.saumya.pos.catalog.internal;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface SolarPackageRepository extends JpaRepository<SolarPackage,Long> {
    Page<SolarPackage> findByTenantIdOrderByNameAscIdAsc(long tenantId,Pageable pageable);
    List<SolarPackage> findByTenantIdAndPublishedTrueOrderByNameAsc(long tenantId);
    Optional<SolarPackage> findByTenantIdAndId(long tenantId,long id);
    boolean existsByTenantIdAndSkuAndIdNot(long tenantId,String sku,long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SolarPackage p where p.tenantId=:tenant and p.id=:id")
    Optional<SolarPackage> locked(long tenant,long id);
}
