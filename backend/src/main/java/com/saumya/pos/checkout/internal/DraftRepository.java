package com.saumya.pos.checkout.internal;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface DraftRepository extends JpaRepository<DraftOrder,String>, JpaSpecificationExecutor<DraftOrder> {
    Optional<DraftOrder> findByTenantIdAndKey(long tenant,String key);
    Optional<DraftOrder> findByCustomerIdAndKey(long customer,String key);
    boolean existsByTenantIdAndDraftOwner(long tenant,String owner);
    List<DraftOrder> findTop20ByCustomerIdAndChannelOrderByExpiresAtDesc(long customer,String channel);
    List<DraftOrder> findTop20ByTenantIdAndBranchIdAndCashierAndSessionIdAndExpiresAtIsNotNullOrderByExpiresAtDesc(long tenant,long branch,String cashier,String session);
    List<DraftOrder> findTop100ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(String status,Instant time);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select d from DraftOrder d where d.tenantId=:tenant and d.id=:id")
    Optional<DraftOrder> lock(long tenant,String id);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select d from DraftOrder d where d.customerId=:customer and d.id=:id and d.channel='ONLINE'")
    Optional<DraftOrder> lockCustomer(long customer,String id);
    @Lock(LockModeType.PESSIMISTIC_READ) @Query("select d from DraftOrder d where d.tenantId=:tenant and d.branchId=:branch and d.cashier=:cashier and d.channel='IN_STORE' and d.expiresAt is not null and d.id=:id")
    Optional<DraftOrder> recoverPos(long tenant,long branch,String cashier,String id);
    @Lock(LockModeType.PESSIMISTIC_READ) @Query("select d from DraftOrder d where d.customerId=:customer and d.channel='ONLINE' and d.expiresAt is not null and d.id=:id")
    Optional<DraftOrder> recoverCustomer(long customer,String id);
}
