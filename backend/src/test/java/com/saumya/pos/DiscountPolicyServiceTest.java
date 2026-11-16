package com.saumya.pos;
import com.saumya.pos.catalog.DiscountPolicyService;
import com.saumya.pos.catalog.internal.*;
import com.saumya.pos.shared.BillPricing;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class DiscountPolicyServiceTest {
 @Test void absentPolicyDeniesDiscretionaryDiscountAndConfigurationValidatesLimit(){
  var stored=new AtomicReference<DiscountPolicy>();
  var repository=(DiscountPolicyRepository)Proxy.newProxyInstance(DiscountPolicyRepository.class.getClassLoader(),new Class<?>[]{DiscountPolicyRepository.class},(p,m,a)->{if(m.getName().equals("findById"))return Optional.ofNullable(stored.get());if(m.getName().equals("saveAndFlush")){stored.set((DiscountPolicy)a[0]);return a[0];}throw new AssertionError(m.getName());});
  var retail=new com.saumya.pos.retail.PosService(null,null,null){
   @Override public Scope scope(){return new Scope(1,2,"cashier");}
   @Override public void authorize(Scope scope,String actor,String... permissions){assertThat(scope.cashier()).isEqualTo(actor);}
  };
  var service=new DiscountPolicyService(repository,new BillPricing(),retail);assertThat(service.maximum(1)).isZero();assertThatThrownBy(()->service.configure(1,new BigDecimal("101"))).isInstanceOf(IllegalArgumentException.class);service.configure(1,new BigDecimal("20"));assertThat(service.maximum(1)).isEqualByComparingTo("20");
 }
}
