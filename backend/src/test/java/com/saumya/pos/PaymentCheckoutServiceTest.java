package com.saumya.pos;
import com.saumya.pos.checkout.*;
import com.saumya.pos.checkout.internal.*;
import com.saumya.pos.identity.CustomerAccess;
import com.saumya.pos.retail.PosService;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
class PaymentCheckoutServiceTest {
 private PaymentPolicy policy(){var env=new MockEnvironment();env.setActiveProfiles("test");return new PaymentPolicy(true,env);}
 private DraftRepository orders(DraftOrder order){return (DraftRepository)Proxy.newProxyInstance(DraftRepository.class.getClassLoader(),new Class<?>[]{DraftRepository.class},(p,m,a)->{if(m.getName().startsWith("lock"))return Optional.of(order);throw new AssertionError(m.getName());});}
 @Test void rejectsCrossCashierBeforeReadingAttempts(){
  var retail=new PosService(null,null){public Scope scope(String name){return new Scope(1,1,name);}public void lockOutlet(Scope scope){}};
  var order=new DraftOrder("sale",1,null,1L,"other-cashier","session","IN_STORE","key","hash",BigDecimal.TEN,1,"owner",Instant.now().plusSeconds(600),false);
  var service=new PaymentCheckoutService(orders(order),null,null,policy(),null,null,null,retail,Clock.systemUTC());
  assertThatThrownBy(()->service.start("alice","sale",false,"UPI")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }
 @Test void retryReturnsExistingAttemptAndCannotChangeMethod(){
  var customer=new CustomerAccess(null){public long resolve(String name){return 7;}public void lock(long id){}};
  var order=new DraftOrder("sale",1,7L,null,null,null,"ONLINE","key","hash",BigDecimal.TEN,1,"owner",Instant.now().plusSeconds(600),false);
  var attempt=new PaymentAttempt(1,"sale","UPI",BigDecimal.TEN,Instant.now());
  var attempts=(PaymentAttemptRepository)Proxy.newProxyInstance(PaymentAttemptRepository.class.getClassLoader(),new Class<?>[]{PaymentAttemptRepository.class},(p,m,a)->{if(m.getName().equals("findByTenantAndOrder"))return Optional.of(attempt);throw new AssertionError("Retry must not write: "+m.getName());});
  var retail=new PosService(null,null){public void lockOutlet(Scope scope){}};
  var service=new PaymentCheckoutService(orders(order),null,attempts,policy(),customer,null,null,retail,Clock.systemUTC());
  assertThat(service.start("buyer","sale",true,"UPI").attemptId()).isEqualTo(attempt.id());assertThatThrownBy(()->service.start("buyer","sale",true,"CARD")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }
 @Test void orderStatusTimeNeverRoundsIntoFuture(){
  var order=new DraftOrder("sale",1,null,1L,"cashier","session","IN_STORE","key","hash",BigDecimal.TEN,1,"owner",Instant.now(),false);var now=Instant.parse("2026-11-14T00:00:00.123456789Z");order.finish("PAID",now);assertThat(order.changed()).isEqualTo(Instant.parse("2026-11-14T00:00:00.123456Z"));assertThat(order.changed()).isBefore(now);
 }
}
