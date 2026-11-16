package com.saumya.pos.returns.internal;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
public interface ReturnRepository extends JpaRepository<ReturnCase,String> {
 @Query(value="select new com.saumya.pos.returns.internal.ReturnQueueRow(r.id,r.orderId,r.state,r.created) from ReturnCase r where r.tenant=:tenant and r.branch=:branch and (:state='ALL' or r.state=:state) and r.created>=:start and r.created<:until and (lower(r.id) like :prefix or lower(r.orderId) like :prefix) order by r.created asc,r.id asc",
 countQuery="select count(r) from ReturnCase r where r.tenant=:tenant and r.branch=:branch and (:state='ALL' or r.state=:state) and r.created>=:start and r.created<:until and (lower(r.id) like :prefix or lower(r.orderId) like :prefix)")
 org.springframework.data.domain.Page<ReturnQueueRow> queue(long tenant,long branch,String state,java.time.Instant start,java.time.Instant until,String prefix,org.springframework.data.domain.Pageable pageable);
 Optional<ReturnCase> findByTenantAndBranchAndId(long tenant,long branch,String id);
 Optional<ReturnCase> findByTenantAndKey(long tenant,String key);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from ReturnCase r where r.tenant=:tenant and r.key=:key") Optional<ReturnCase> currentKey(long tenant,String key);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from ReturnCase r where r.tenant=:tenant and r.branch=:branch and r.id=:id") Optional<ReturnCase> locked(long tenant,long branch,String id);
 @Lock(LockModeType.PESSIMISTIC_READ) List<ReturnCase> findTop20ByTenantAndBranchAndStateOrderByCreatedDescIdDesc(long tenant,long branch,String state);
 @Lock(LockModeType.PESSIMISTIC_READ) List<ReturnCase> findTop20ByTenantAndBranchAndStateOrderByCreatedAscIdAsc(long tenant,long branch,String state);
}
