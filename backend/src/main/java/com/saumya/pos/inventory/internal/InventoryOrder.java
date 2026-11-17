package com.saumya.pos.inventory.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Read-only inventory projection of the checkout order ownership fields.
 * This keeps the inventory module on its JPA-owned data-access boundary.
 */
@Entity
@Table(name = "orders")
public class InventoryOrder {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private long tenantId;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "stock_branch_id")
    private Long stockBranchId;

    @Column(name = "cashier", length = 100)
    private String cashier;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "sales_channel", nullable = false, length = 16)
    private String channel;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    protected InventoryOrder() {}

    public String id() { return id; }
    public long tenantId() { return tenantId; }
    public Long branchId() { return branchId; }
    public Long stockBranchId() { return stockBranchId; }
    public String cashier() { return cashier; }
    public Long customerId() { return customerId; }
    public String channel() { return channel; }
    public String status() { return status; }
}
