package com.saumya.pos.catalog.internal;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface ProductCategoryRepository extends JpaRepository<ProductCategory,ProductCategory.Key>{List<ProductCategory> findByIdTenantIdAndIdProductIdIn(long tenant,Collection<Long> products);
 @Modifying @Query("delete from ProductCategory a where a.id.tenantId=:tenant and a.categoryId=:category") void unassign(long tenant,long category);
}
