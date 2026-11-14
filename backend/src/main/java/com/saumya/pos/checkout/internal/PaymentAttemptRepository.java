package com.saumya.pos.checkout.internal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt,String> {
    // A current read is required after the order/outlet lock under MySQL REPEATABLE READ.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentAttempt> findByTenantAndOrder(long tenant,String order);
}
