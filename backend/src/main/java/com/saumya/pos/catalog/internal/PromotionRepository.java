package com.saumya.pos.catalog.internal;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
public interface PromotionRepository extends JpaRepository<Promotion,Promotion.Key> {
 @Query("select p from Promotion p where p.id.tenantId=:tenant and p.id.productId=:product")
 Optional<Promotion> read(long tenant,long product);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from Promotion p where p.id.tenantId=:tenant and p.id.productId=:product")
 Optional<Promotion> locked(long tenant,long product);
}
