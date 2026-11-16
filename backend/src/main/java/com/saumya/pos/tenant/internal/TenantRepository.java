package com.saumya.pos.tenant.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.saumya.pos.tenant.TenantStatus;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findBySlug(String slug);
    Optional<Tenant> findBySlugAndLifecycleStatus(String slug, TenantStatus status);
    List<Tenant> findByLifecycleStatusAndSlugIsNotNullOrderByNameAscIdAsc(TenantStatus status);
    long countByLifecycleStatus(TenantStatus status);

    @Query("""
        select t from Tenant t
        where (:status is null or t.lifecycleStatus = :status)
          and (:term = '' or lower(t.name) like lower(concat('%', :term, '%'))
               or lower(t.slug) like lower(concat('%', :term, '%'))
               or lower(t.legalName) like lower(concat('%', :term, '%')))
        order by t.createdAt desc, t.id desc
        """)
    Page<Tenant> search(@Param("term") String term, @Param("status") TenantStatus status, Pageable pageable);
}
