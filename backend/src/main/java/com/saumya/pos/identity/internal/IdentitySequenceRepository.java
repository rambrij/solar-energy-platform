package com.saumya.pos.identity.internal;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
public interface IdentitySequenceRepository extends JpaRepository<IdentitySequence,Integer>{
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select s from IdentitySequence s where s.id=1") Optional<IdentitySequence> locked();
}
