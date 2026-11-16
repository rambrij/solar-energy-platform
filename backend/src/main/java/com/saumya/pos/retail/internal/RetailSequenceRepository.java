package com.saumya.pos.retail.internal;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
public interface RetailSequenceRepository extends JpaRepository<RetailSequence,String>{
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RetailSequence s where s.name='REGISTER'") Optional<RetailSequence> register();
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RetailSequence s where s.name='BRANCH'") Optional<RetailSequence> branch();
}
