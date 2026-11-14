package com.saumya.pos.catalog.internal;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
public interface CatalogSequenceRepository extends JpaRepository<CatalogSequence,String>{@Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select s from CatalogSequence s where s.name=:name") Optional<CatalogSequence> locked(String name);}
