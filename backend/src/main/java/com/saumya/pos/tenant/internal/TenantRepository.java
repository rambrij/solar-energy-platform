package com.saumya.pos.tenant.internal;

import com.saumya.pos.tenant.TenantStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantRepository extends JpaRepository<Tenant,Long> {
    Optional<Tenant> findBySlug(String slug);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tenant t where t.id=:id")
    Optional<Tenant> lockById(@Param("id") long id);
    Optional<Tenant> findBySlugAndLifecycleStatus(String slug,TenantStatus status);
    List<Tenant> findByLifecycleStatusAndSlugIsNotNullOrderByNameAscIdAsc(TenantStatus status);
    long countByLifecycleStatus(TenantStatus status);
    @Query("""
        select t from Tenant t
        where (:status is null or t.lifecycleStatus = :status)
          and (:term = '' or lower(t.name) like lower(concat('%', :term, '%')) escape '!'
               or lower(t.slug) like lower(concat('%', :term, '%')) escape '!'
               or lower(t.legalName) like lower(concat('%', :term, '%')) escape '!')
        order by t.createdAt desc, t.id desc
        """)
    Page<Tenant> search(@Param("term") String term,@Param("status") TenantStatus status,Pageable pageable);
    @Query("""
        select t from Tenant t
        where t.lifecycleStatus = :status and t.slug is not null and trim(t.slug) <> ''
          and (:term = '' or lower(t.name) like lower(concat('%', :term, '%')) escape '!'
               or lower(t.slug) like lower(concat('%', :term, '%')) escape '!'
               or lower(t.legalName) like lower(concat('%', :term, '%')) escape '!')
        order by lower(t.name),t.id
        """)
    Page<Tenant> searchActiveVendors(@Param("term") String term,@Param("status") TenantStatus status,Pageable pageable);
}
