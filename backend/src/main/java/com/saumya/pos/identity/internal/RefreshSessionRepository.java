package com.saumya.pos.identity.internal;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface RefreshSessionRepository extends JpaRepository<RefreshSession,String>{
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @Query("select s from RefreshSession s where s.hash=:hash")
 Optional<RefreshSession> locked(String hash);
 @Modifying @Query("update RefreshSession s set s.revoked=true where s.family=:family")
 int revoke(String family);
 @Modifying @Query("update RefreshSession s set s.revoked=true where s.username=:username")
 int revokeByUsername(String username);
 boolean existsByFamilyAndRevokedFalseAndExpiresAfter(String family,Instant now);
 boolean existsByFamilyAndRevokedFalseAndExpiresAfterAndMfaVerifiedTrue(String family,Instant now);

 @Modifying
 @Query("update RefreshSession s set s.revoked=true where s.username=:username and s.family<>:currentFamily and s.revoked=false and s.used is null and s.expires>:now")
 int revokeOtherFamilies(@Param("username") String username,@Param("currentFamily") String currentFamily,@Param("now") Instant now);

 @Query("select s from RefreshSession s where s.family=:family and s.revoked=false and s.used is null and s.expires>:now")
 Optional<RefreshSession> activeForFamily(@Param("family") String family,@Param("now") Instant now);
}
