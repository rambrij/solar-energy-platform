package com.saumya.pos.platform.internal;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ConfigRepository extends JpaRepository<ConfigEntry,String> {}
