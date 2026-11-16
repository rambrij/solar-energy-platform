package com.saumya.pos.catalog.internal;
import java.util.*;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
public interface CategoryRepository extends org.springframework.data.jpa.repository.JpaRepository<Category,Long> {
 boolean existsByTenantIdAndSlugAndIdNot(long tenant,String slug,long id);
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select c from Category c where c.tenantId=:tenant and c.id=:id") Optional<Category> locked(long tenant,long id);
 org.springframework.data.domain.Page<Category> findByTenantIdOrderByNameAscIdAsc(long tenant,org.springframework.data.domain.Pageable page);
 @Query("select new com.saumya.pos.catalog.internal.CategoryRow(c.id,c.name,c.slug,count(p)) from Category c join ProductCategory a on a.id.tenantId=c.tenantId and a.categoryId=c.id join Product p on p.tenantId=a.id.tenantId and p.id=a.id.productId where c.tenantId=:tenant and c.active=true and p.published=true group by c.id,c.name,c.slug order by c.name,c.id")
 List<CategoryRow> published(long tenant);
 @Query("select new com.saumya.pos.catalog.internal.CategoryRow(c.id,c.name,c.slug,1L) from Category c join ProductCategory a on a.id.tenantId=c.tenantId and a.categoryId=c.id where c.tenantId=:tenant and c.active=true and a.id.productId=:product")
 Optional<CategoryRow> forProduct(long tenant,long product);
}
