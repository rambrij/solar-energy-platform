package com.saumya.pos;
import com.saumya.pos.catalog.*;
import com.saumya.pos.catalog.internal.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
class CatalogSearchServiceTest {
 int reads;Object[] arguments;boolean inactive;CatalogSearchService service;
 @BeforeEach void setup(){
  var repo=(CatalogSearchRepository)Proxy.newProxyInstance(CatalogSearchRepository.class.getClassLoader(),new Class<?>[]{CatalogSearchRepository.class},(p,m,args)->{reads++;if(m.getName().startsWith("count"))return 5L;arguments=args;return new PageImpl<CatalogSearchRow>(List.of(),(Pageable)args[6],0);});
  var catalogue=new CatalogService(null,null,null){@Override public VendorInfo activeVendor(String slug){if(inactive)throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);return new VendorInfo(7,slug,"Vendor",true);}@Override public List<CategoryInfo> categories(String slug){return List.of(new CategoryInfo(11,"Panels","panels",2));}};
  service=new CatalogSearchService(repo,catalogue);
 }
 @Test void defaultsAndScopeAreServerBound(){var result=service.search("solar",null,null,null,false,0,24);assertThat(arguments[0]).isEqualTo(7L);assertThat(arguments[1]).isEqualTo("%%");assertThat(arguments[5]).isEqualTo("name");assertThat(result.categories()).extracting(CatalogSearchService.Category::count).containsExactly(2L,3L);assertThat(result.catalogueTotal()).isEqualTo(5);assertThat(result.vendorSlug()).isEqualTo("solar");assertThat(result.vendorName()).isEqualTo("Vendor");}
 @Test void escapesLiteralWildcardsAndUsesBoundedPage(){service.search("solar","  PANEL%_! ","11","price-low",true,2,12);assertThat(arguments).containsExactly(7L,"%panel!%!_!!%",11L,false,true,"price-low",PageRequest.of(2,12));}
 @Test void otherCategoryIsDistinctFromNumericIds(){service.search("solar","","other","name",false,0,1);assertThat(arguments[2]).isEqualTo(0L);assertThat(arguments[3]).isEqualTo(true);}
 @Test void invalidFiltersDoNotReadRepositories(){for(String category:List.of("-1","0","1 OR 1=1","9999999999999999999"))assertThatThrownBy(()->service.search("solar","",category,"name",false,0,24)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->service.search("solar","x".repeat(121),"all","name",false,0,24)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->service.search("solar","","all","unknown",false,0,24)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->service.search("solar","","all","name",false,0,49)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->service.search("solar","","all","name",false,10001,24)).isInstanceOf(ResponseStatusException.class);assertThat(reads).isZero();}
 @Test void inactiveVendorStopsSearch(){inactive=true;assertThatThrownBy(()->service.search("closed","","all","name",false,0,24)).isInstanceOf(ResponseStatusException.class);assertThat(reads).isZero();}
}
