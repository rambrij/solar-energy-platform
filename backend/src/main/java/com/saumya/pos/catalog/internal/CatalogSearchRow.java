package com.saumya.pos.catalog.internal;
import java.math.BigDecimal;
public interface CatalogSearchRow {
 long getId();String getSku();String getName();BigDecimal getPrice();BigDecimal getTaxRate();BigDecimal getListPrice();BigDecimal getDiscountPercent();Long getCategoryId();String getCategoryName();long getAvailable();
}
