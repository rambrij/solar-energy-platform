package com.saumya.pos.inventory.internal;

import org.springframework.data.repository.Repository;

/** Read-only approval lookup scoped by tenant, branch and return identifier. */
public interface InventoryApprovedReturnRepository extends Repository<InventoryApprovedReturn, String> {
    boolean existsByTenantIdAndBranchIdAndIdAndState(long tenantId, long branchId, String id, String state);
}
