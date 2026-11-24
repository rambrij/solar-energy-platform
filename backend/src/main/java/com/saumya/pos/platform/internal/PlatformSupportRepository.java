package com.saumya.pos.platform.internal;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PlatformSupportRepository extends JpaRepository<PlatformSupportGrant,Long> {
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PlatformSupportGrant> findById(Long id);

    List<PlatformSupportGrant> findTop100ByOrderByGrantedAtDesc();
}
