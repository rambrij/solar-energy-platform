package com.saumya.pos;
import com.saumya.pos.checkout.*;
import com.saumya.pos.checkout.internal.*;
import com.saumya.pos.identity.CustomerAccess;
import com.saumya.pos.catalog.CatalogService;
import com.saumya.pos.inventory.InventoryService;
import com.saumya.pos.retail.PosService;
import com.saumya.pos.membership.TenantEntitlementPolicy;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
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
    
    @Test
    void directOnlinePaymentWithoutCustomerIdentityIsDeniedBeforeOrderOrProviderAccess() {
        var orders=mock(DraftRepository.class);
        var attempts=mock(PaymentAttemptRepository.class);
        var adapter=mock(VendorPaymentAdapter.class);
        var customers=mock(CustomerAccess.class);
        var retail=new PosService(null,null,null) {
            @Override public void authorizeCustomer(String actor) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
            }
        };
        var service=new PaymentCheckoutService(orders,null,attempts,policy(),customers,
            mock(CatalogService.class),mock(InventoryService.class),retail,Clock.systemUTC());
        ReflectionTestUtils.setField(service,"adapter",adapter);
        assertThatThrownBy(()->service.start("attacker","foreign-order","UPI"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(orders,attempts,adapter,customers);
    }

    @Test
    void rejectsCrossCashierBeforeReadingAttempts() {
        var retail = new PosService(null, null, null) {
            public Scope scope() {
                return new Scope(1, 1, "name");
            }
            
            public void lockOutlet(Scope scope) {
            }
            @Override public void authorize(Scope scope,String actor,String... permissions) {}
            @Override public void authorizeCustomer(String actor) {}
        };
        var order = new DraftOrder("sale", 1, null, 1L, "other-cashier", "session", "IN_STORE", "key", "hash", BigDecimal.TEN, 1, "owner", Instant.now().plusSeconds(600), false);
        var service = new PaymentCheckoutService(orders(order), null, null, policy(), null, null, null, retail, Clock.systemUTC());
        assertThatThrownBy(() -> service.tender("alice", "sale", "tender-test", new PaymentCheckoutService.TenderRequest("UPI", BigDecimal.TEN, null))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
 @Test void retryReturnsExistingAttemptAndCannotChangeMethod(){
  var customer=new CustomerAccess(null){public long resolve(String name){return 7;}public void lock(long id){}};
  var order=new DraftOrder("sale",1,7L,null,null,null,"ONLINE","key","hash",BigDecimal.TEN,1,"owner",Instant.now().plusSeconds(600),false);
  var attempt=new PaymentAttempt(1,"sale","UPI",BigDecimal.TEN,Instant.now());
     var attempts = (PaymentAttemptRepository) Proxy.newProxyInstance(
             PaymentAttemptRepository.class.getClassLoader(), new Class<?>[] { PaymentAttemptRepository.class
             }, (p, m, a) -> {
                 if (m.getName().equals("findByTenantAndOrder")) return Optional.of(attempt);
                 throw new AssertionError("Retry must not write: " + m.getName());
             }
     );
     var retail = new PosService(null, null, null) {
         public void lockOutlet(Scope scope) {
         }
         @Override public void authorize(Scope scope,String actor,String... permissions) {}
         @Override public void authorizeCustomer(String actor) {}
     };
     var service = new PaymentCheckoutService(orders(order), null, attempts, policy(), customer, null, null, retail, Clock.systemUTC());
     ReflectionTestUtils.setField(service,"entitlements",mock(TenantEntitlementPolicy.class));
  assertThat(service.start("buyer","sale","UPI").attemptId()).isEqualTo(attempt.id());assertThatThrownBy(()->service.start("buyer","sale","CARD")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }
 @Test void orderStatusTimeNeverRoundsIntoFuture(){
  var order=new DraftOrder("sale",1,null,1L,"cashier","session","IN_STORE","key","hash",BigDecimal.TEN,1,"owner",Instant.now(),false);var now=Instant.parse("2026-11-14T00:00:00.123456789Z");order.finish("PAID",now);assertThat(order.changed()).isEqualTo(Instant.parse("2026-11-14T00:00:00.123456Z"));assertThat(order.changed()).isBefore(now);
 }

 @Test
 void everyPublicPaymentEntryPointRejectsMissingActorBeforePersistenceOrProviderAccess() {
  var orders=mock(DraftRepository.class);
  var lines=mock(DraftLineRepository.class);
  var attempts=mock(PaymentAttemptRepository.class);
  var customers=mock(CustomerAccess.class);
  var catalogue=mock(CatalogService.class);
  var stock=mock(InventoryService.class);
  var adapter=mock(VendorPaymentAdapter.class);
  var retail=new PosService(null,null,null) {
   @Override public Scope scope(){return new Scope(7,9,"attacker");}
   @Override public void authorize(Scope scope,String actor,String... permissions){
    throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"No payment actor");
   }
   @Override public void authorizeCustomer(String actor){
    throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"No customer actor");
   }
  };
  var service=new PaymentCheckoutService(orders,lines,attempts,policy(),customers,catalogue,stock,retail,Clock.systemUTC());
  ReflectionTestUtils.setField(service,"adapter",adapter);
  ReflectionTestUtils.setField(service,"terminalRules",new TerminalTenderRules());
  var review=new PaymentCheckoutService.Review("attempt-1","CONFIRM","opaque-reference","evidence-ref","Verified test evidence");

  assertDenied(()->service.tenderStatus("attacker","order-1"));
  assertDenied(()->service.tender("attacker","order-1","idempotency-key",
      new PaymentCheckoutService.TenderRequest("UPI",BigDecimal.TEN,null)));
  assertDenied(()->service.finalizeTenders("attacker","order-1"));
  assertDenied(()->service.status("attacker","order-1"));
  assertDenied(()->service.start("attacker","order-1","UPI"));
  assertDenied(()->service.confirm("attacker","order-1","attempt-1"));
  assertDenied(()->service.unknown("attacker","order-1","attempt-1"));
  assertDenied(()->service.reviewStatus("attacker","order-1"));
  assertDenied(()->service.awaitingReview("attacker"));
  assertDenied(()->service.finalizeVerified("attacker","order-1","attempt-1"));
  assertDenied(()->service.verifyEvidence("attacker","order-1",review));

  verifyNoInteractions(orders,lines,attempts,customers,catalogue,stock,adapter);
 }

 private static void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
  assertThatThrownBy(call)
      .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
      .hasFieldOrPropertyWithValue("status",org.springframework.http.HttpStatus.FORBIDDEN);
 }

}
