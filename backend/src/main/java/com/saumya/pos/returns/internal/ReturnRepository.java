package com.saumya.pos.returns.internal;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
public interface ReturnRepository extends JpaRepository<ReturnCase,String> {
 Optional<ReturnCase> findByTenantAndBranchAndId(long tenant,long branch,String id);
 Optional<ReturnCase> findByTenantAndKey(long tenant,String key);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from ReturnCase r where r.tenant=:tenant and r.key=:key") Optional<ReturnCase> currentKey(long tenant,String key);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from ReturnCase r where r.tenant=:tenant and r.branch=:branch and r.id=:id") Optional<ReturnCase> locked(long tenant,long branch,String id);
 @Lock(LockModeType.PESSIMISTIC_READ) List<ReturnCase> findTop20ByTenantAndBranchAndStateOrderByCreatedDescIdDesc(long tenant,long branch,String state);
 @Lock(LockModeType.PESSIMISTIC_READ) List<ReturnCase> findTop20ByTenantAndBranchAndStateOrderByCreatedAscIdAsc(long tenant,long branch,String state);
}
