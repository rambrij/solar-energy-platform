package com.saumya.pos.inventory.internal;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
/** Read-only approval and owner-session lookup scoped by tenant, branch and return identifier. */
public interface InventoryApprovedReturnRepository extends Repository<InventoryApprovedReturn, String> {
    interface OwnerSession {
        String getRequester();
        String getSessionId();
    }
    boolean existsByTenantIdAndBranchIdAndIdAndState(long tenantId,long branchId,String id,String state);
    @Query(value="SELECT requester AS requester, session_id AS sessionId FROM pos_returns WHERE tenant_id=?1 AND branch_id=?2 AND id=?3 AND state='APPROVED'",nativeQuery=true)
    Optional<OwnerSession> approvedOwnerSession(long tenantId,long branchId,String id);
}
