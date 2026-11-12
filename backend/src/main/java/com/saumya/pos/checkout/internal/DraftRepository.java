package com.saumya.pos.checkout.internal;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface DraftRepository extends JpaRepository<DraftOrder,String> {
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
}
