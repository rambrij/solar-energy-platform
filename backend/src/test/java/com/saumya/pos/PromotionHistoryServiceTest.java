package com.saumya.pos;
import com.saumya.pos.catalog.PromotionHistoryService;
import com.saumya.pos.catalog.internal.*;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
class PromotionHistoryServiceTest {
 int reads;boolean missing;Object[] arguments;PromotionHistoryService service;
 @BeforeEach void setup() throws Exception {
  var constructor=Product.class.getDeclaredConstructor();constructor.setAccessible(true);var product=constructor.newInstance();for(var entry:Map.<String,Object>of("id",9L,"sku","P9","name","Current panel","published",false).entrySet())org.springframework.test.util.ReflectionTestUtils.setField(product,entry.getKey(),entry.getValue());
  var products=(ProductRepository)Proxy.newProxyInstance(ProductRepository.class.getClassLoader(),new Class<?>[]{ProductRepository.class},(p,m,args)->{assertThat(args).containsExactly(7L,9L);return missing?Optional.empty():Optional.of(product);});
  var audit=(PromotionAuditRepository)Proxy.newProxyInstance(PromotionAuditRepository.class.getClassLoader(),new Class<?>[]{PromotionAuditRepository.class},(p,m,args)->{reads++;arguments=args;return new PageImpl<>(List.of(new PromotionHistoryService.Change("event",BigDecimal.ZERO,BigDecimal.TEN,"approver","Approved offer",Instant.EPOCH)),(Pageable)args[2],1);});service=new PromotionHistoryService(audit,products);
 }
 @Test void scopesDataAndIncludesWithdrawnProductLabels(){var result=service.history(7,9,0,12);assertThat(arguments).containsExactly(7L,9L,PageRequest.of(0,12));assertThat(result.published()).isFalse();assertThat(result.currentName()).isEqualTo("Current panel");assertThat(result.items().getFirst().newPercent()).isEqualByComparingTo("10");}
 @Test void missingOrForeignProductStopsAuditQuery(){missing=true;assertThatThrownBy(()->service.history(7,9,0,12)).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(404));assertThat(reads).isZero();}
 @Test void invalidBoundsStopBeforeReads(){for(int page:List.of(-1,10001))assertThatThrownBy(()->service.history(7,9,page,12)).isInstanceOf(ResponseStatusException.class);for(int size:List.of(0,49))assertThatThrownBy(()->service.history(7,9,0,size)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->service.history(7,0,0,12)).isInstanceOf(ResponseStatusException.class);assertThat(reads).isZero();}
}
