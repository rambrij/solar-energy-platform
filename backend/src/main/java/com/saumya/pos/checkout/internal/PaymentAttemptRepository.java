package com.saumya.pos.checkout.internal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt,String> {
    // A current read is required after the order/outlet lock under MySQL REPEATABLE READ.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    java.util.List<PaymentAttempt> findAllByTenantAndOrderOrderByCreatedAscIdAsc(long tenant,String order);
    default Optional<PaymentAttempt> findByTenantAndOrder(long tenant,String order){return findAllByTenantAndOrderOrderByCreatedAscIdAsc(tenant,order).stream().findFirst();}
    @org.springframework.data.jpa.repository.Query("select a from PaymentAttempt a join DraftOrder d on a.order=d.id and a.tenant=d.tenantId where a.tenant=:tenant and d.branchId=:branch and a.origin='POS_TERMINAL' and a.state in ('PENDING_VERIFICATION','UNKNOWN_RECONCILIATION_REQUIRED') order by a.created,a.id")
    java.util.List<PaymentAttempt> awaitingReview(long tenant,long branch,org.springframework.data.domain.Pageable page);
}
