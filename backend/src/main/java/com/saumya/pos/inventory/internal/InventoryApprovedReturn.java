package com.saumya.pos.inventory.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Read-only projection for verifying that an inspected return has been approved. */
@Entity
@Table(name = "pos_returns")
public class InventoryApprovedReturn {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private long tenantId;

    @Column(name = "branch_id", nullable = false)
    private long branchId;

    @Column(name = "state", nullable = false, length = 16)
    private String state;

    protected InventoryApprovedReturn() {}
}
