package com.saumya.pos.identity.internal;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
public interface GlobalUserRepository extends JpaRepository<GlobalUser,Long>{
    Optional<GlobalUser> findByUsername(String username);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from GlobalUser u where u.username=:username")
    Optional<GlobalUser> lockedByUsername(String username);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from GlobalUser u where u.id=:id")
    Optional<GlobalUser> lockedById(@org.springframework.data.repository.query.Param("id") long id);
}
