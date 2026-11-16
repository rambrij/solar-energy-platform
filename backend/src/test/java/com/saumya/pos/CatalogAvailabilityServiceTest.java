package com.saumya.pos;
import com.saumya.pos.catalog.CatalogAvailabilityService;
import com.saumya.pos.catalog.internal.*;
import com.saumya.pos.inventory.StockAvailabilityChanged;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CatalogAvailabilityServiceTest {
 @Test void publicInventoryEventUpdatesOnlyTheCatalogProjection(){var saved=new ArrayList<CatalogStock>();var repository=(CatalogStockRepository)Proxy.newProxyInstance(CatalogStockRepository.class.getClassLoader(),new Class<?>[]{CatalogStockRepository.class},(p,m,a)->{if(m.getName().equals("findById")){assertThat(a[0]).isEqualTo(new CatalogStock.Key(7,9,11));return Optional.empty();}if(m.getName().equals("saveAndFlush")){saved.add((CatalogStock)a[0]);return a[0];}throw new AssertionError(m.getName());});new CatalogAvailabilityService(repository).changed(new StockAvailabilityChanged(7,9,11,3));assertThat(saved.getFirst().available()).isEqualTo(3);assertThatThrownBy(()->new StockAvailabilityChanged(7,9,11,-1)).isInstanceOf(IllegalArgumentException.class);}
}
