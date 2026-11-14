package com.saumya.pos;
import com.saumya.pos.checkout.PaymentPolicy;
import org.springframework.mock.env.MockEnvironment;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class PaymentPolicyTest {
 @Test void simulationRequiresExplicitNonProductionEnvironment(){
  for(String[] profiles:new String[][]{{},{"production"},{"prod","local"},{"test","production"}}){var env=new MockEnvironment();env.setActiveProfiles(profiles);assertThatThrownBy(()->new PaymentPolicy(true,env)).isInstanceOf(IllegalStateException.class);}
  var env=new MockEnvironment();env.setActiveProfiles("local");var policy=new PaymentPolicy(true,env);assertThat(policy.enabled()).isTrue();policy.method("UPI");policy.method("CARD");assertThatThrownBy(()->policy.method("CASH"));
 }
 @Test void disabledAdapterCannotConfirmPayment(){var policy=new PaymentPolicy(false,new MockEnvironment());assertThat(policy.enabled()).isFalse();assertThatThrownBy(policy::requireEnabled).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);}
}
