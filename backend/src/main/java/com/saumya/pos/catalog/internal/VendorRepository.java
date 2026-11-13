package com.saumya.pos.catalog.internal;
import java.util.*;
import org.springframework.data.repository.Repository;
public interface VendorRepository extends Repository<Vendor,Long> {
    @org.springframework.data.jpa.repository.Query(value="select new com.saumya.pos.catalog.VendorDirectoryService$VendorCard(v.id,v.slug,v.name) from Vendor v where v.active=true and v.slug is not null and trim(v.slug)<>'' and (lower(v.name) like :term escape '!' or lower(v.slug) like :term escape '!') order by lower(v.name),v.id",
    countQuery="select count(v) from Vendor v where v.active=true and v.slug is not null and trim(v.slug)<>'' and (lower(v.name) like :term escape '!' or lower(v.slug) like :term escape '!')")
    org.springframework.data.domain.Page<com.saumya.pos.catalog.VendorDirectoryService.VendorCard> directory(String term,org.springframework.data.domain.Pageable page);
    List<Vendor> findByActiveTrueAndSlugIsNotNullOrderByNameAscIdAsc();
    Optional<Vendor> findBySlugAndActiveTrue(String slug);
    Optional<Vendor> findById(Long id);
}
