package com.saumya.pos.catalog.internal;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.Repository;
public interface ProductRepository extends Repository<Product,Long> {
    List<Product> findByTenantIdAndPublishedTrueOrderById(long tenant);
    Optional<Product> findByTenantIdAndId(long tenant,long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.tenantId=:tenant and p.id=:id and p.published=true")
    Optional<Product> lockPublished(long tenant,long id);
}
