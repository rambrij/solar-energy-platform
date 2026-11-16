package com.saumya.pos.tenant.internal;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantRepository extends JpaRepository<TenantEntity,Long> {
    Optional<TenantEntity> findBySlug(String slug);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from TenantEntity t where t.id=:id")
    Optional<TenantEntity> lockById(@Param("id") long id);
    @Query("select coalesce(max(t.id),0)+1 from TenantEntity t") long nextId();
    /** status '' means any status; q is a pre-escaped lower-case LIKE pattern (escape character '!'). */
    @Query(value="select t from TenantEntity t where (:status='' or t.status=:status) and (lower(t.name) like :q escape '!' or lower(coalesce(t.slug,'')) like :q escape '!') order by t.id desc",
           countQuery="select count(t) from TenantEntity t where (:status='' or t.status=:status) and (lower(t.name) like :q escape '!' or lower(coalesce(t.slug,'')) like :q escape '!')")
    Page<TenantEntity> search(@Param("status") String status,@Param("q") String q,Pageable pageable);
    @Query("select t.status, count(t) from TenantEntity t group by t.status") List<Object[]> countByStatus();
}
