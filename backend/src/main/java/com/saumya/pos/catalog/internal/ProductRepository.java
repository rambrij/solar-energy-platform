package com.saumya.pos.catalog.internal;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.Repository;
public interface ProductRepository extends JpaRepository<Product,Long> {
    List<Product> findByTenantIdAndPublishedTrueOrderById(long tenant);
    org.springframework.data.domain.Page<Product> findByTenantIdOrderByNameAscIdAsc(long tenant,org.springframework.data.domain.Pageable page);
    @Query("""
        select p from Product p
        where p.tenantId=:tenant and (locate(:query,lower(p.name))>0
            or locate(:query,lower(p.sku))>0 or locate(:query,lower(p.barcode))>0)
        order by p.name asc,p.id asc
        """)
    org.springframework.data.domain.Page<Product> search(long tenant,String query,org.springframework.data.domain.Pageable page);
    boolean existsByTenantIdAndSkuAndIdNot(long tenant,String sku,long id);
    boolean existsByTenantIdAndBarcodeAndIdNot(long tenant,String barcode,long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from Product p where p.tenantId=:tenant and p.id=:id") Optional<Product> locked(long tenant,long id);

    Optional<Product> findByTenantIdAndId(long tenant,long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.tenantId=:tenant and p.id=:id and p.published=true")
    Optional<Product> lockPublished(long tenant,long id);
}
