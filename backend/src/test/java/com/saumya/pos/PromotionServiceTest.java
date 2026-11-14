package com.saumya.pos;
import com.saumya.pos.catalog.PromotionService;
import com.saumya.pos.catalog.internal.PromotionRepository;
import com.saumya.pos.shared.PromotionPricing;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
class PromotionServiceTest {
 @Test void invalidCommandsStopBeforeDatabaseAccess(){var service=new PromotionService(null,null,null,new PromotionPricing(),Clock.systemUTC());for(var request:new PromotionService.Change[]{new PromotionService.Change(new BigDecimal("101"),"Approved"),new PromotionService.Change(BigDecimal.TEN," "),new PromotionService.Change(new BigDecimal("1.111"),"Approved")})assertThatThrownBy(()->service.approve(1,"manager",1,request)).isInstanceOf(ResponseStatusException.class);}
 @Test void noRulePreservesTheTenantScopedListPrice(){var repository=(PromotionRepository)Proxy.newProxyInstance(PromotionRepository.class.getClassLoader(),new Class<?>[]{PromotionRepository.class},(proxy,method,args)->{assertThat(method.getName()).isEqualTo("read");assertThat(args).containsExactly(7L,9L);return Optional.empty();});var service=new PromotionService(repository,null,null,new PromotionPricing(),Clock.systemUTC());var result=service.price(7,9,new BigDecimal("100"),false);assertThat(result.price()).isEqualByComparingTo("100");assertThat(result.percent()).isEqualByComparingTo("0");}
}
