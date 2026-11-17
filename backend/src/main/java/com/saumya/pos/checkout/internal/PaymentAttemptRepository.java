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
    @org.springframework.data.jpa.repository.Query(value="""
        SELECT pa.*
        FROM vendor_payment_attempts pa
        JOIN orders o
          ON o.tenant_id=pa.tenant_id AND o.id=pa.order_id
         AND o.sales_channel='IN_STORE' AND o.status='DRAFT'
        JOIN pos_sessions s
          ON s.tenant_id=o.tenant_id AND s.branch_id=o.branch_id
         AND s.id=o.session_id AND s.cashier=o.cashier AND s.state='OPEN'
        JOIN employee_register_assignments ca
          ON ca.tenant_id=s.tenant_id AND ca.branch_id=s.branch_id
         AND ca.username=s.cashier AND ca.register_id=s.register_id AND ca.active=TRUE
        JOIN employee_register_assignments ra
          ON ra.tenant_id=s.tenant_id AND ra.branch_id=s.branch_id
         AND ra.username=?3 AND ra.register_id=s.register_id AND ra.active=TRUE
        JOIN registers r
          ON r.tenant_id=s.tenant_id AND r.branch_id=s.branch_id AND r.id=s.register_id
        JOIN pos_stock_locations l
          ON l.tenant_id=s.tenant_id AND l.branch_id=s.branch_id AND l.active=TRUE
        WHERE pa.tenant_id=?1 AND o.branch_id=?2
          AND pa.origin='POS_TERMINAL'
          AND pa.state IN ('PENDING_VERIFICATION','UNKNOWN_RECONCILIATION_REQUIRED')
        ORDER BY pa.created_at,pa.id
        """,nativeQuery=true)
    java.util.List<PaymentAttempt> awaitingReview(long tenant,long branch,String reviewer,
        org.springframework.data.domain.Pageable page);
}
