package com.saumya.pos;
import com.saumya.pos.catalog.DiscountPolicyService;
import com.saumya.pos.catalog.internal.*;
import com.saumya.pos.shared.BillPricing;
import com.saumya.pos.security.*;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiscountPolicyServiceTest {
 @AfterEach void clearSecurityContext(){SecurityContextHolder.clearContext();}
 @Test void absentPolicyDeniesDiscretionaryDiscountAndConfigurationValidatesLimit(){
  var stored=new AtomicReference<DiscountPolicy>();
  var repository=(DiscountPolicyRepository)Proxy.newProxyInstance(DiscountPolicyRepository.class.getClassLoader(),new Class<?>[]{DiscountPolicyRepository.class},(p,m,a)->{if(m.getName().equals("findById"))return Optional.ofNullable(stored.get());if(m.getName().equals("saveAndFlush")){stored.set((DiscountPolicy)a[0]);return a[0];}throw new AssertionError(m.getName());});
  VendorMembershipResolver membership=username->Optional.of(new VendorMembershipResolver.Membership(1,2,username,List.of("POS_VIEW","POS_DISCOUNT_OVERRIDE")));
  var auth=new AuthorizationContext(membership);
  SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("cashier",null,
   List.of(new SimpleGrantedAuthority("POS_VIEW"),new SimpleGrantedAuthority("POS_DISCOUNT_OVERRIDE"),new SimpleGrantedAuthority(AuthorizationContext.ACTOR_CONTEXT_VENDOR))));
  var service=new DiscountPolicyService(repository,new BillPricing(),auth);
  assertThat(service.maximum(1)).isZero();
  assertThatThrownBy(()->service.configure(1,new BigDecimal("101"))).isInstanceOf(IllegalArgumentException.class);
  service.configure(1,new BigDecimal("20"));
  assertThat(service.maximum(1)).isEqualByComparingTo("20");
 }

 @Test void directPolicyReadAndWriteRejectMissingPermissionBeforeRepositoryAccess(){
  var repository=mock(DiscountPolicyRepository.class);
  var auth=mock(AuthorizationContext.class);
  org.mockito.Mockito.when(auth.current()).thenReturn(new AuthorizationContext.Context(
   "cashier",7L,12L,"7:cashier",List.of("PRODUCT_VIEW"),false,false,true));
  var service=new DiscountPolicyService(repository,new BillPricing(),auth);
  assertThatThrownBy(()->service.maximum(7L)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThatThrownBy(()->service.configure(7L,new BigDecimal("10"))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  verifyNoInteractions(repository);
 }
}
