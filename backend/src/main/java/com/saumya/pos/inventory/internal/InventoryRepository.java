package com.saumya.pos.inventory.internal;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.Repository;
public interface InventoryRepository extends JpaRepository<InventoryBalance,InventoryBalance.Key> {
    @Query("select coalesce(sum(i.quantity-i.reserved),0) from InventoryBalance i where i.id.tenantId=:tenant and i.id.productId=:product")
    long available(long tenant,long product);
    @Query("select coalesce(sum(i.quantity-i.reserved),0) from InventoryBalance i where i.id.tenantId=:tenant and i.id.branchId=:branch and i.id.productId=:product")
    long atBranch(long tenant,long branch,long product);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryBalance i where i.id.tenantId=:tenant and i.id.branchId=:branch and i.id.productId=:product")
    Optional<InventoryBalance> lock(long tenant,long branch,long product);
}
