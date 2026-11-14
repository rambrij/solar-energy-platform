package com.saumya.pos.catalog.internal;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CatalogStockRepository extends JpaRepository<CatalogStock,CatalogStock.Key>{}
