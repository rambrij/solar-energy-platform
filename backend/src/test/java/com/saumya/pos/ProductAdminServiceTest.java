package com.saumya.pos;
import com.saumya.pos.catalog.ProductAdminService;
import com.saumya.pos.catalog.internal.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.*;
import static org.assertj.core.api.Assertions.*;
class ProductAdminServiceTest {
 @AfterEach void clearSecurityContext(){SecurityContextHolder.clearContext();}
 private void authenticate(String... permissions){var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("viewer","test",java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));SecurityContextHolder.setContext(context);}
 private ProductAdminService serviceFor(Product product){var products=(ProductRepository)java.lang.reflect.Proxy.newProxyInstance(ProductRepository.class.getClassLoader(),new Class<?>[]{ProductRepository.class},(proxy,method,args)->{if(method.getName().equals("findByTenantIdOrderByNameAscIdAsc"))return new PageImpl<>(List.of(product),(Pageable)args[1],1);throw new UnsupportedOperationException(method.getName());});var assignments=(ProductCategoryRepository)java.lang.reflect.Proxy.newProxyInstance(ProductCategoryRepository.class.getClassLoader(),new Class<?>[]{ProductCategoryRepository.class},(proxy,method,args)->java.util.Optional.empty());return new ProductAdminService(products,null,assignments,null,null,Clock.systemUTC());}
 @Test void productViewRedactsPriceAndTaxUnlessPriceViewIsGranted(){var product=new Product(9,7);product.edit("SKU",null,"Panel",new BigDecimal("10.00"),new BigDecimal("18.00"),false);var service=serviceFor(product);authenticate("PRODUCT_VIEW");var hidden=service.list(7,0,20).items().getFirst();assertThat(hidden.price()).isNull();assertThat(hidden.taxRate()).isNull();SecurityContextHolder.clearContext();authenticate("PRODUCT_VIEW","PRICE_VIEW");var visible=service.list(7,0,20).items().getFirst();assertThat(visible.price()).isEqualByComparingTo("10.00");assertThat(visible.taxRate()).isEqualByComparingTo("18.00");}

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
