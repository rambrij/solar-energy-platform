package com.saumya.pos.catalog.internal;
import java.util.*;
import org.springframework.data.repository.Repository;
public interface VendorRepository extends Repository<Vendor,Long> {
    List<Vendor> findByActiveTrueAndSlugIsNotNullOrderByNameAscIdAsc();
    Optional<Vendor> findBySlugAndActiveTrue(String slug);
    Optional<Vendor> findById(Long id);
}
