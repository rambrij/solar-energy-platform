package com.saumya.pos.returns.internal;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
public interface CashRefundRepository extends JpaRepository<CashRefund,String> {
 Optional<CashRefund> findByTenantAndKey(long tenant,String key);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from CashRefund r where r.tenant=:tenant and r.key=:key") Optional<CashRefund> currentKey(long tenant,String key);
 @Lock(LockModeType.PESSIMISTIC_READ) Optional<CashRefund> findByTenantAndBranchAndId(long tenant,long branch,String id);
 @Lock(LockModeType.PESSIMISTIC_READ) @Query("select r from CashRefund r where r.tenant=:tenant and r.returnId=:returned") Optional<CashRefund> forReturn(long tenant,String returned);
 @Lock(LockModeType.PESSIMISTIC_READ) List<CashRefund> findTop20ByTenantAndBranchOrderByPaidDescIdDesc(long tenant,long branch);
}
