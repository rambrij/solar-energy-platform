package com.saumya.pos.identity.internal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface IdentityEmailVerificationTokenRepository extends JpaRepository<IdentityEmailVerificationToken,String> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from IdentityEmailVerificationToken t where t.tokenHash=:hash")
 Optional<IdentityEmailVerificationToken> lockedByHash(@Param("hash") String hash);
 Optional<IdentityEmailVerificationToken> findByTokenHash(String tokenHash);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 Optional<IdentityEmailVerificationToken> findFirstByUserIdOrderByCreatedAtDesc(long userId);
}
