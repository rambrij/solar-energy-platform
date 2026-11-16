package com.saumya.pos;
import com.saumya.pos.catalog.*;
import com.saumya.pos.catalog.internal.*;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
class PosCatalogServiceTest {
 Object[] arguments;int reads;List<PosCatalogRow> rows=List.of();PosCatalogService service;
 PosCatalogRow row(){return (PosCatalogRow)Proxy.newProxyInstance(PosCatalogRow.class.getClassLoader(),new Class<?>[]{PosCatalogRow.class},(p,m,a)->switch(m.getName()){case "getId"->5L;case "getAvailable"->3L;case "getPrice","getListPrice"->new BigDecimal("10");case "getTaxRate","getDiscountPercent"->BigDecimal.ZERO;case "getCategoryId"->null;default->"Panel";});}
 @BeforeEach void setup(){
  var repo=(PosCatalogRepository)Proxy.newProxyInstance(PosCatalogRepository.class.getClassLoader(),new Class<?>[]{PosCatalogRepository.class},(p,m,args)->{reads++;if(m.getName().startsWith("count"))return 2L;arguments=args;return new PageImpl<>(rows,(Pageable)args[9],rows.size());});
  var catalogue=new CatalogService(null,null,null){@Override public List<CategoryInfo> publishedCategories(long tenant){assertThat(tenant).isEqualTo(7);return List.of(new CategoryInfo(11,"Panels","panels",1));}};
  service=new PosCatalogService(repo,catalogue);
 }
 @Test void bindsAssignedScopeAndEscapesBarcodeSearch(){rows=List.of(row());var result=service.search(7,9," P%_! ","11","price-low",true,2,12);assertThat(arguments).containsExactly(7L,9L,"%p!%!_!!%","",false,11L,false,true,"price-low",PageRequest.of(2,12));assertThat(result.items().getFirst().quantity()).isEqualTo(3);assertThat(result.categories()).extracting(CatalogSearchService.Category::count).containsExactly(1L,1L);}
 @Test void exactScanIgnoresPageAndStockFilters(){rows=List.of(row());assertThat(service.scan(7,9," P1 ").id()).isEqualTo(5);assertThat(arguments).containsExactly(7L,9L,"","p1",true,0L,false,false,"name",PageRequest.of(0,2));}
 @Test void missingAndAmbiguousCodesFailClosed(){assertThatThrownBy(()->service.scan(7,9,"P1")).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(404));rows=List.of(row(),row());assertThatThrownBy(()->service.scan(7,9,"P1")).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(409));}
 @Test void invalidScopeAndFiltersNeverReadData(){assertThatThrownBy(()->service.search(0,9,"","all","name",false,0,24)).isInstanceOf(ResponseStatusException.class);for(String category:List.of("-1","0","9999999999999999999"))assertThatThrownBy(()->service.search(7,9,"",category,"name",false,0,24)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->service.search(7,9,"x".repeat(121),"all","name",false,0,24)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->service.search(7,9,"","all","name",false,0,49)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->service.scan(7,9," ")).isInstanceOf(ResponseStatusException.class);assertThat(reads).isZero();}
}
