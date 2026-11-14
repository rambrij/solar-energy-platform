package com.saumya.pos.identity.internal;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
public interface RefreshSessionRepository extends JpaRepository<RefreshSession,String>{
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select s from RefreshSession s where s.hash=:hash") Optional<RefreshSession> locked(String hash);
 @Modifying @Query("update RefreshSession s set s.revoked=true where s.family=:family") int revoke(String family);
 boolean existsByFamilyAndRevokedFalseAndExpiresAfter(String family,Instant now);
}
