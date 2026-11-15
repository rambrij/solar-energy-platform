package com.saumya.pos;
import com.saumya.pos.retail.*;
import com.saumya.pos.retail.internal.*;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class BranchPolicyServiceTest {
 @Test void settingsReadIsDerivedFromValidatedMembershipAndNeverAnUnscopedId(){var repository=(BranchConfigurationRepository)Proxy.newProxyInstance(BranchConfigurationRepository.class.getClassLoader(),new Class<?>[]{BranchConfigurationRepository.class},(p,m,a)->{assertThat(m.getName()).isEqualTo("findByTenantAndId");assertThat(a).containsExactly(7L,9L);return Optional.of(new BranchConfiguration(9,7,"Outlet"));});var retail=new PosService(null,null){public Scope scope(String username){return new Scope(7,9,username);}};var result=new BranchPolicyService(repository,retail,null,null,null,Clock.systemUTC()).current("cashier");assertThat(result.branch()).isEqualTo(9);assertThat(result.currency()).isEqualTo("INR");assertThat(result.enabled()).isFalse();}
 @Test void registerCreationRequiresAnAuditReasonBeforeAnyPersistence(){var service=new BranchPolicyService(null,null,null,null,null,Clock.systemUTC());assertThatThrownBy(()->service.createRegister("cashier","")).hasMessageContaining("reason");}
}
