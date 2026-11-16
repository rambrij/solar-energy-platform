package com.saumya.pos.identity.internal;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformMfaEnrollmentRepository extends JpaRepository<PlatformMfaEnrollmentToken,String> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from PlatformMfaEnrollmentToken e where e.tokenHash=:hash")
    Optional<PlatformMfaEnrollmentToken> locked(@Param("hash") String hash);
    void deleteByUsername(String username);
}
