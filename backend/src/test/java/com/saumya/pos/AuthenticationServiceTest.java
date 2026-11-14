package com.saumya.pos;
import com.saumya.pos.identity.*;
import com.saumya.pos.identity.internal.*;
import com.saumya.pos.membership.MembershipService;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;
class AuthenticationServiceTest {
 @SuppressWarnings("unchecked") private <T>T repository(Class<T> type,java.lang.reflect.InvocationHandler handler){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);}
 @Test void validGlobalCustomerLoginNormalizesIdentityAndPersistsOnlyHashedRefreshMaterial(){var encoder=new BCryptPasswordEncoder(4);var user=new GlobalUser(7,"buyer@example.com",encoder.encode("PasswordForTests"));var saved=new ArrayList<RefreshSession>();var users=repository(GlobalUserRepository.class,(p,m,a)->{assertThat(a[0]).isEqualTo("buyer@example.com");return Optional.of(user);});var customers=repository(CustomerRepository.class,(p,m,a)->Optional.of(7L));var sessions=repository(RefreshSessionRepository.class,(p,m,a)->{saved.add((RefreshSession)a[0]);return a[0];});var membership=new MembershipService(null,null,null){public Optional<Access> access(String name){return Optional.empty();}};var environment=new MockEnvironment();environment.setActiveProfiles("test");var service=new AuthenticationService(users,customers,null,sessions,membership,encoder,new JwtService("",environment,Clock.systemUTC()),Clock.systemUTC());var result=service.login(" BUYER@EXAMPLE.COM ","PasswordForTests");assertThat(result.account().permissions()).containsExactly("CUSTOMER");assertThat(result.refreshToken()).hasSize(43);assertThat(saved).hasSize(1);assertThat(result.account().tenantId()).isNull();}
 @Test void malformedRegistrationAndRefreshStopBeforePersistence(){var service=new AuthenticationService(null,null,null,null,null,new BCryptPasswordEncoder(4),null,Clock.systemUTC());assertThatThrownBy(()->service.register("invalid","short")).hasMessageContaining("Valid email");assertThatThrownBy(()->service.refresh("not-a-token")).hasMessageContaining("expired");}
}
