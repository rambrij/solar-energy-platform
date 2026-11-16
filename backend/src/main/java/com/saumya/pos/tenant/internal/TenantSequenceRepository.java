package com.saumya.pos.tenant.internal;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface TenantSequenceRepository extends JpaRepository<TenantSequence, Integer> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TenantSequence s where s.id = 1")
    Optional<TenantSequence> locked();
}
