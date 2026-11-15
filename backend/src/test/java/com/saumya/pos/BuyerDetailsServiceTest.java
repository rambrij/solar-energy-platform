package com.saumya.pos;
import com.saumya.pos.checkout.BuyerDetailsService;
import com.saumya.pos.checkout.internal.DraftRepository;
import com.saumya.pos.retail.PosService;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class BuyerDetailsServiceTest {
 @Test void malformedReceiptReferenceCannotReachPrivateData(){var service=new BuyerDetailsService(null,null,null,null,Clock.systemUTC());assertThatThrownBy(()->service.forReceipt("alice","../private")).hasMessageContaining("Receipt unavailable");}
 @Test void foreignTenantReferenceCannotReadBuyerSnapshots(){var retail=new PosService(null,null){public Scope scope(String name){return new Scope(1,1,name);}};var orders=(DraftRepository)java.lang.reflect.Proxy.newProxyInstance(DraftRepository.class.getClassLoader(),new Class<?>[]{DraftRepository.class},(p,m,a)->{assertThat(m.getName()).isEqualTo("findByTenantIdAndId");assertThat(a).containsExactly(1L,"private-order");return Optional.empty();});var service=new BuyerDetailsService(null,orders,retail,null,Clock.systemUTC());assertThatThrownBy(()->service.forReceipt("alice","private-order")).hasMessageContaining("Receipt unavailable");}
 @Test void sameTenantOtherBranchCannotReadAPaidBuyerSnapshot(){var retail=new PosService(null,null){public Scope scope(String name){return new Scope(1,1,name);}};var order=new com.saumya.pos.checkout.internal.DraftOrder("private-order",1,null,2L,"other","shift","IN_STORE","key","hash",java.math.BigDecimal.TEN,2,"owner",java.time.Instant.now(),false);order.pay(java.math.BigDecimal.TEN,1,false,java.time.Instant.now());var orders=(DraftRepository)java.lang.reflect.Proxy.newProxyInstance(DraftRepository.class.getClassLoader(),new Class<?>[]{DraftRepository.class},(p,m,a)->Optional.of(order));assertThatThrownBy(()->new BuyerDetailsService(null,orders,retail,null,Clock.systemUTC()).forReceipt("alice","private-order")).hasMessageContaining("Receipt unavailable");}
}
