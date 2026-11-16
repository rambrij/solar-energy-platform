package com.saumya.pos.catalog.internal;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CatalogStockRepository extends JpaRepository<CatalogStock,CatalogStock.Key>{
 @Override @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 java.util.Optional<CatalogStock> findById(CatalogStock.Key key);
}
