package com.saumya.pos;
import com.saumya.pos.checkout.*;
import com.saumya.pos.checkout.internal.DraftRepository;
import com.saumya.pos.identity.CustomerAccess;
import com.saumya.pos.retail.PosService;
import java.lang.reflect.Proxy;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
class DraftRecoveryServiceTest {
 int reads;Object[] arguments;DraftRecoveryService service;
 @BeforeEach void setup(){
  var repo=(DraftRepository)Proxy.newProxyInstance(DraftRepository.class.getClassLoader(),new Class<?>[]{DraftRepository.class},(p,m,args)->{reads++;arguments=args;return Optional.empty();});
  var retail=new PosService(null,null,null,null){@Override public Scope scope(String username){assertThat(username).isEqualTo("cashier");return new Scope(7,9,username);}};
  var customers=new CustomerAccess(null){@Override public long resolve(String username){assertThat(username).isEqualTo("buyer");return 101;}};
  service=new DraftRecoveryService(repo,null,customers,retail,null,new DraftRecoveryPolicy(),Clock.systemUTC());
 }
 @Test void posReferenceUsesCanonicalTenantBranchCashier(){assertThatThrownBy(()->service.pos("cashier","known-id")).isInstanceOf(ResponseStatusException.class);assertThat(arguments).containsExactly(7L,9L,"cashier","known-id");}
 @Test void customerReferenceUsesVerifiedPrincipalOnly(){assertThatThrownBy(()->service.customer("buyer","known-id")).isInstanceOf(ResponseStatusException.class);assertThat(arguments).containsExactly(101L,"known-id");}
 @Test void malformedReferencesStopBeforePersistence(){for(String id:new String[]{null," ","x".repeat(37)})assertThatThrownBy(()->service.pos("cashier",id)).isInstanceOf(ResponseStatusException.class);assertThat(reads).isZero();}
}
