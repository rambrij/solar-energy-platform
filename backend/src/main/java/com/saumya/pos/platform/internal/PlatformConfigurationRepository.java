package com.saumya.pos.platform.internal;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformConfigurationRepository extends JpaRepository<PlatformConfigurationEntry,String> {}
