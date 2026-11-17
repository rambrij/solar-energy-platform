package com.saumya.pos;
import com.saumya.pos.customer.VendorCustomerService;
import com.saumya.pos.customer.internal.VendorCustomerRepository;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

class VendorCustomerServiceTest {
 @AfterEach void clearSecurity(){SecurityContextHolder.clearContext();}
 private void authorizeInternal(){
  var context=SecurityContextHolder.createEmptyContext();
  context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
      VendorCustomerService.APPROVAL_PRINCIPAL,null,
      List.of(new SimpleGrantedAuthority(VendorCustomerService.APPROVAL_AUTHORITY))));
  SecurityContextHolder.setContext(context);
 }
 @Test void associationWriteRejectsUntrustedCallersBeforeRepositoryAccess(){
  var calls=new AtomicInteger();
  var repository=(VendorCustomerRepository)Proxy.newProxyInstance(
      VendorCustomerRepository.class.getClassLoader(),new Class<?>[]{VendorCustomerRepository.class},
      (p,m,a)->{calls.incrementAndGet();throw new AssertionError("Repository reached before internal authorization");});
  var service=new VendorCustomerService(repository);
  var customer=SecurityContextHolder.createEmptyContext();
  customer.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
      "buyer",null,List.of(new SimpleGrantedAuthority("CUSTOMER"))));
  SecurityContextHolder.setContext(customer);
  assertThatThrownBy(()->service.associate(7,9,Instant.now()))
      .isInstanceOfSatisfying(ResponseStatusException.class,error->assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
  var forged=SecurityContextHolder.createEmptyContext();
  forged.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
      VendorCustomerService.APPROVAL_PRINCIPAL,null,List.of(new SimpleGrantedAuthority("CUSTOMER"))));
  SecurityContextHolder.setContext(forged);
  assertThatThrownBy(()->service.associate(7,9,Instant.now()))
      .isInstanceOfSatisfying(ResponseStatusException.class,error->assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
  assertThat(calls).hasValue(0);
 }
 @Test void repeatedVerifiedConsentDoesNotDuplicateOrQueryAnotherVendorRelationship(){
  var saved=new AtomicInteger();
  var repository=(VendorCustomerRepository)Proxy.newProxyInstance(
      VendorCustomerRepository.class.getClassLoader(),new Class<?>[]{VendorCustomerRepository.class},
      (p,m,a)->{
       if(m.getName().equals("existsByTenantAndCustomer")){assertThat(a).containsExactly(7L,9L);return saved.get()>0;}
       if(m.getName().equals("saveAndFlush")){saved.incrementAndGet();return a[0];}
       throw new AssertionError(m.getName());
      });
  var service=new VendorCustomerService(repository);
  authorizeInternal();
  service.associate(7,9,Instant.now());
  service.associate(7,9,Instant.now());
  assertThat(saved.get()).isEqualTo(1);
 }
}
