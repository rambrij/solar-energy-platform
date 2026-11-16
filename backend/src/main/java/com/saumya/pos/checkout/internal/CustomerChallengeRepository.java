package com.saumya.pos.checkout.internal;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
public interface CustomerChallengeRepository extends JpaRepository<CustomerChallenge,String>{
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select c from CustomerChallenge c where c.hash=:hash") Optional<CustomerChallenge> locked(String hash);
}
