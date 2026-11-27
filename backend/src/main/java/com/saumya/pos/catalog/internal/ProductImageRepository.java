package com.saumya.pos.catalog.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface ProductImageRepository extends JpaRepository<ProductImage,String> {
    List<ProductImage> findByTenantIdAndProductIdAndDeletedAtIsNullOrderByCreatedAtDesc(
        long tenantId,long productId);
    Optional<ProductImage> findByTenantIdAndProductIdAndIdAndDeletedAtIsNull(
        long tenantId,long productId,String id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ProductImage i where i.tenantId=:tenant and i.productId=:product and i.id=:id and i.deletedAt is null")
    Optional<ProductImage> locked(long tenant,long product,String id);
}
