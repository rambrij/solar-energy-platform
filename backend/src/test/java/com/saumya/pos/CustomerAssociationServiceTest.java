package com.saumya.pos;
import com.saumya.pos.checkout.CustomerAssociationService;
import com.saumya.pos.identity.CustomerAccess;
import com.saumya.pos.customer.VendorCustomerService;
import com.saumya.pos.checkout.internal.CustomerChallengeRepository;
import com.saumya.pos.checkout.internal.DraftRepository;
import com.saumya.pos.catalog.CatalogService;
import com.saumya.pos.security.AuthorizationContext;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import com.saumya.pos.retail.PosService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CustomerAssociationServiceTest {
 @Test void customerRequestAndConsentAreRequiredBeforePersistence(){var service=new CustomerAssociationService(null,null,null,null,null,null,Clock.systemUTC());assertThatThrownBy(()->service.create("cashier","order",false)).hasMessageContaining("request");assertThatThrownBy(()->service.approve("buyer","invalid",false)).hasMessageContaining("consent");}
 @Test void invalidBearerCodesNeverQueryChallengeOrOrderData(){var customers=new CustomerAccess(null){public long resolve(String name){return 1;}};var retail=new PosService(null,null,null){@Override public void authorizeCustomer(String actor){}};var service=new CustomerAssociationService(null,null,customers,null,null,retail,Clock.systemUTC());for(String token:new String[]{"", "../order", "x".repeat(42),"+".repeat(43)})assertThatThrownBy(()->service.preview("buyer",token)).hasMessageContaining("unavailable");}

 @Test void vendorActorsCannotInvokeCustomerChallengeBoundariesBeforeAnyRepository(){
  var authorization=mock(AuthorizationContext.class);
  when(authorization.current()).thenReturn(new AuthorizationContext.Context("cashier", 7L, 12L, String.valueOf(7L) + ":" + ("cashier"), Set.of("POS_VIEW"), false, false, true));
  var challenges=mock(CustomerChallengeRepository.class);
  var orders=mock(DraftRepository.class);
  var customers=mock(CustomerAccess.class);
  var relationships=mock(VendorCustomerService.class);
  var catalog=mock(CatalogService.class);
  var service=new CustomerAssociationService(challenges,orders,customers,relationships,catalog,
      new PosService(null,null,authorization,customers),Clock.systemUTC());
  String validToken="A".repeat(43);
  assertDenied(()->service.create("cashier","order-1",true));
  assertDenied(()->service.preview("cashier",validToken));
  assertDenied(()->service.approve("cashier",validToken,true));
  verifyNoInteractions(challenges,orders,customers,relationships,catalog);
 }
 private static void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation){
  assertThatThrownBy(operation).isInstanceOfSatisfying(ResponseStatusException.class,error->
      assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
 }

}