package com.saumya.pos.catalog.internal;
import java.util.*;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
public interface CategoryRepository extends Repository<Category,Long> {
 @Query("select new com.saumya.pos.catalog.internal.CategoryRow(c.id,c.name,c.slug,count(p)) from Category c join ProductCategory a on a.id.tenantId=c.tenantId and a.categoryId=c.id join Product p on p.tenantId=a.id.tenantId and p.id=a.id.productId where c.tenantId=:tenant and p.published=true group by c.id,c.name,c.slug order by c.name,c.id")
 List<CategoryRow> published(long tenant);
 @Query("select new com.saumya.pos.catalog.internal.CategoryRow(c.id,c.name,c.slug,1L) from Category c join ProductCategory a on a.id.tenantId=c.tenantId and a.categoryId=c.id where c.tenantId=:tenant and a.id.productId=:product")
 Optional<CategoryRow> forProduct(long tenant,long product);
}
