package com.saumya.pos.checkout.internal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt,String> {
    Optional<PaymentAttempt> findByTenantAndOrder(long tenant,String order);
}
