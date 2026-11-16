package com.saumya.pos;
import com.saumya.pos.catalog.CategoryAdminService;
import com.saumya.pos.catalog.internal.*;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.test.util.ReflectionTestUtils;
import com.saumya.pos.security.AuthorizationContext;
class CategoryAdminServiceTest {
 private void authorize(CategoryAdminService service,String... permissions){var auth=mock(AuthorizationContext.class);when(auth.current()).thenReturn(new AuthorizationContext.Context("viewer",7L,9L,Set.of(permissions),false,false,true));ReflectionTestUtils.setField(service,"authorization",auth);}
 @Test void listingUsesTheAuthenticatedTenantAndBoundedPagination(){var repository=(CategoryRepository)Proxy.newProxyInstance(CategoryRepository.class.getClassLoader(),new Class<?>[]{CategoryRepository.class},(p,m,a)->{assertThat(a).containsExactly(7L,PageRequest.of(0,20));return new PageImpl<>(List.of(new Category(9,7,"Panels","panels")),PageRequest.of(0,20),1);});var service=new CategoryAdminService(repository,null,null,null,Clock.systemUTC());authorize(service,"PRODUCT_VIEW");var result=service.list(7,0,20);assertThat(result.items().getFirst().name()).isEqualTo("Panels");assertThat(result.totalElements()).isEqualTo(1);}
 @Test void invalidCategoryOrPaginationStopsBeforePersistence(){var service=new CategoryAdminService(null,null,null,null,Clock.systemUTC());authorize(service,"PRODUCT_CREATE","PRODUCT_VIEW","PRODUCT_UPDATE","PRODUCT_ARCHIVE");assertThatThrownBy(()->service.create(7,"actor",new CategoryAdminService.Input("Panels","bad slug",0,true,"Approved"))).hasMessageContaining("Valid category");assertThatThrownBy(()->service.list(7,-1,20)).hasMessageContaining("pagination");}
}
