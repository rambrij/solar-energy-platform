package com.saumya.pos;
import com.saumya.pos.checkout.CustomerAssociationService;
import com.saumya.pos.identity.CustomerAccess;
import com.saumya.pos.retail.PosService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CustomerAssociationServiceTest {
 @Test void customerRequestAndConsentAreRequiredBeforePersistence(){var service=new CustomerAssociationService(null,null,null,null,null,null,Clock.systemUTC());assertThatThrownBy(()->service.create("cashier","order",false)).hasMessageContaining("request");assertThatThrownBy(()->service.approve("buyer","invalid",false)).hasMessageContaining("consent");}
 @Test void invalidBearerCodesNeverQueryChallengeOrOrderData(){var customers=new CustomerAccess(null){public long resolve(String name){return 1;}};var retail=new PosService(null,null,null){@Override public void authorizeCustomer(String actor){}};var service=new CustomerAssociationService(null,null,customers,null,null,retail,Clock.systemUTC());for(String token:new String[]{"", "../order", "x".repeat(42),"+".repeat(43)})assertThatThrownBy(()->service.preview("buyer",token)).hasMessageContaining("unavailable");}
}
