package com.saumya.pos.identity.internal;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.Repository;
public interface CustomerRepository extends Repository<CustomerProfile,Long> {
    @Query(value="SELECT c.id FROM customer_profiles c JOIN users u ON u.id=c.user_id WHERE u.username=:username AND u.enabled=TRUE",nativeQuery=true)
    Optional<Long> resolve(String username);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CustomerProfile c where c.id=:id")
    Optional<CustomerProfile> lock(Long id);
}
