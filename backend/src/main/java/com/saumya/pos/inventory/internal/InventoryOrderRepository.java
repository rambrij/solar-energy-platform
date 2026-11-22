package com.saumya.pos.inventory.internal;

import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Narrow, read-only order ownership lookup used before inventory reservation mutations. */
public interface InventoryOrderRepository extends Repository<InventoryOrder, String> {
    Optional<InventoryOrder> findByTenantIdAndId(long tenantId, String id);
}
