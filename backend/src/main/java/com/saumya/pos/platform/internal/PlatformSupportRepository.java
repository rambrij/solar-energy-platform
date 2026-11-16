package com.saumya.pos.platform.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSupportRepository extends JpaRepository<PlatformSupportGrant,Long> {
    List<PlatformSupportGrant> findTop100ByOrderByGrantedAtDesc();
}
