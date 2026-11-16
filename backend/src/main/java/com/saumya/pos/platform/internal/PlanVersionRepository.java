package com.saumya.pos.platform.internal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PlanVersionRepository extends JpaRepository<PlanVersion,String> {
    List<PlanVersion> findByPlanCodeOrderByVersionDesc(String planCode);
    Optional<PlanVersion> findByPlanCodeAndVersion(String planCode,int version);
}
