package com.saumya.pos.platform.internal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SupportGrantRepository extends JpaRepository<SupportGrant,String> { List<SupportGrant> findByTenantOrderByGrantedAtDescIdDesc(long tenant); }
