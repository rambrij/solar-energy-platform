package com.saumya.pos;
import com.saumya.pos.catalog.ProductAdminService;
import com.saumya.pos.catalog.internal.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import static org.assertj.core.api.Assertions.*;
class ProductAdminServiceTest {
 @Test void listingUsesBoundedPaginationAndOnlyTheRequestedTenant(){
  var product=new Product(9,7);product.edit("SKU",null,"Panel",BigDecimal.TEN,BigDecimal.ZERO,false);
  var products=(ProductRepository)java.lang.reflect.Proxy.newProxyInstance(ProductRepository.class.getClassLoader(),new Class<?>[]{ProductRepository.class},(proxy,method,args)->{assertThat(method.getName()).isEqualTo("findByTenantIdOrderByNameAscIdAsc");assertThat(args).containsExactly(7L,PageRequest.of(0,20));return new PageImpl<>(List.of(product),PageRequest.of(0,20),1);});
  var assignments=(ProductCategoryRepository)java.lang.reflect.Proxy.newProxyInstance(ProductCategoryRepository.class.getClassLoader(),new Class<?>[]{ProductCategoryRepository.class},(proxy,method,args)->{assertThat(method.getName()).isEqualTo("findById");assertThat(args).containsExactly(new ProductCategory.Key(7,9));return java.util.Optional.empty();});
  var result=new ProductAdminService(products,null,assignments,null,null,Clock.systemUTC()).list(7,0,20);
  assertThat(result.items().getFirst().name()).isEqualTo("Panel");
 }
 @Test void invalidPricesTaxAndPaginationCannotReachPersistence(){
  var service=new ProductAdminService(null,null,null,null,null,Clock.systemUTC());
  assertThatThrownBy(()->service.create(7,"actor",new ProductAdminService.Input("SKU",null,"Panel",new BigDecimal("-1"),BigDecimal.ZERO,false,null,0,"Approved"))).hasMessageContaining("Valid product");
  assertThatThrownBy(()->service.create(7,"actor",new ProductAdminService.Input("SKU",null,"Panel",BigDecimal.ONE,new BigDecimal("100.01"),false,null,0,"Approved"))).hasMessageContaining("Valid product");
  assertThatThrownBy(()->service.list(7,0,101)).hasMessageContaining("pagination");
 }
}
