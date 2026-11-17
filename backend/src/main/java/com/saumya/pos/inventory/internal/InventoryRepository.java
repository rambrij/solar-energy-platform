package com.saumya.pos.inventory.internal;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
public interface InventoryRepository extends JpaRepository<InventoryBalance,InventoryBalance.Key> {
    @Query("select coalesce(sum(i.quantity-i.reserved),0) from InventoryBalance i where i.id.tenantId=:tenant and i.id.productId=:product")
    long available(long tenant,long product);
    @Query("select coalesce(sum(i.quantity-i.reserved),0) from InventoryBalance i where i.id.tenantId=:tenant and i.id.branchId=:branch and i.id.productId=:product")
    long atBranch(long tenant,long branch,long product);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryBalance i where i.id.tenantId=:tenant and i.id.branchId=:branch and i.id.productId=:product")
    Optional<InventoryBalance> lock(long tenant,long branch,long product);

    @Query(value="SELECT stock_location_id FROM pos_stock_locations WHERE tenant_id=?1 AND branch_id=?2 AND active=TRUE",nativeQuery=true)
    List<Long> activePosStockLocations(long tenant,long branch);
    @Query(value="SELECT COUNT(*) FROM employee_register_assignments WHERE tenant_id=?1 AND branch_id=?2 AND username=?3 AND active=TRUE",nativeQuery=true)
    long activeBranchRegisterAssignmentCount(long tenant,long branch,String username);
    @Query(value="SELECT COUNT(*) FROM employee_register_assignments WHERE tenant_id=?1 AND branch_id=?2 AND username=?3 AND register_id=?4 AND active=TRUE",nativeQuery=true)
    long activeRegisterAssignmentCount(long tenant,long branch,String username,long register);

    @Query(value="""
        SELECT p.register_id FROM pos_sessions p
        JOIN employee_register_assignments a
          ON a.tenant_id=p.tenant_id AND a.branch_id=p.branch_id
         AND a.username=p.cashier AND a.register_id=p.register_id AND a.active=TRUE
        JOIN registers r ON r.tenant_id=p.tenant_id AND r.branch_id=p.branch_id AND r.id=p.register_id
        WHERE p.tenant_id=?1 AND p.branch_id=?2 AND p.cashier=?3 AND p.id=?4 AND p.state='OPEN'
        FOR UPDATE
        """,nativeQuery=true)
    List<Long> lockOwnerOpenSessionRegisterIds(long tenant,long branch,String owner,String sessionId);

    @Query(value="""
        SELECT p.id FROM pos_sessions p
        JOIN employee_register_assignments a
          ON a.tenant_id=p.tenant_id AND a.branch_id=p.branch_id
         AND a.username=p.cashier AND a.register_id=p.register_id AND a.active=TRUE
        JOIN registers r ON r.tenant_id=p.tenant_id AND r.branch_id=p.branch_id AND r.id=p.register_id
        WHERE p.tenant_id=?1 AND p.branch_id=?2 AND p.cashier=?3 AND p.state='OPEN'
        """,nativeQuery=true)
    List<String> assignedOpenSessions(long tenant,long branch,String cashier);

    @Query(value="""
        SELECT p.id FROM pos_sessions p
        JOIN employee_register_assignments a
          ON a.tenant_id=p.tenant_id AND a.branch_id=p.branch_id
         AND a.username=p.cashier AND a.register_id=p.register_id AND a.active=TRUE
        JOIN registers r ON r.tenant_id=p.tenant_id AND r.branch_id=p.branch_id AND r.id=p.register_id
        WHERE p.tenant_id=?1 AND p.branch_id=?2 AND p.cashier=?3 AND p.state='OPEN'
        FOR UPDATE
        """,nativeQuery=true)
    List<String> lockAssignedOpenSessions(long tenant,long branch,String cashier);

    @Query(value="""
        SELECT COALESCE(SUM(i.quantity-i.reserved),0) FROM inventory i
        JOIN pos_stock_locations l ON l.tenant_id=i.tenant_id AND l.branch_id=i.branch_id AND l.active=TRUE
        WHERE i.tenant_id=?1 AND i.branch_id=?2 AND i.product_id=?3
        """,nativeQuery=true)
    long activeAvailableAtBranch(long tenant,long branch,long product);
    @Query(value="""
        SELECT COALESCE(SUM(i.quantity-i.reserved),0) FROM inventory i
        JOIN pos_stock_locations l ON l.tenant_id=i.tenant_id AND l.branch_id=i.branch_id AND l.active=TRUE
        WHERE i.tenant_id=?1 AND i.product_id=?2
        """,nativeQuery=true)
    long activeAvailableForTenant(long tenant,long product);
}
