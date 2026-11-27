package com.saumya.pos.marketplace.internal;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SiteSurveyRepository extends JpaRepository<SiteSurvey,Long> {
    List<SiteSurvey> findTop100ByTenantIdOrderByScheduledAtDescIdDesc(long tenantId);
    List<SiteSurvey> findTop100ByTenantIdAndAssignedToOrderByScheduledAtDescIdDesc(
        long tenantId,String assignedTo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SiteSurvey s where s.tenantId=:tenantId and s.id=:id")
    Optional<SiteSurvey> findByTenantIdAndIdForUpdate(
        @Param("tenantId") long tenantId,@Param("id") long id);
}
