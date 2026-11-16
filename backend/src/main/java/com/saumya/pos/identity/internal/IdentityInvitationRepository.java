package com.saumya.pos.identity.internal;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdentityInvitationRepository extends JpaRepository<IdentityInvitation,String> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from IdentityInvitation i where i.tokenHash=:hash")
    Optional<IdentityInvitation> locked(@Param("hash") String hash);
    void deleteByUsername(String username);
}
