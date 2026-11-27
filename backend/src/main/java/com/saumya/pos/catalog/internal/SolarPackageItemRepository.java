package com.saumya.pos.catalog.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SolarPackageItemRepository extends JpaRepository<SolarPackageItem,SolarPackageItem.Key> {
    List<SolarPackageItem> findByIdTenantIdAndIdPackageIdOrderByIdProductId(long tenantId,long packageId);
    @Modifying
    @Query("delete from SolarPackageItem i where i.id.tenantId=:tenant and i.id.packageId=:packageId")
    int deleteForPackage(@Param("tenant") long tenant,@Param("packageId") long packageId);
}
