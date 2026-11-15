package com.saumya.pos;
import com.saumya.pos.returns.ReturnQueueService;
import com.saumya.pos.returns.internal.*;
import com.saumya.pos.retail.PosService;
import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
class ReturnQueueServiceTest {
 int reads;Object[] arguments;ReturnQueueService service;
 @BeforeEach void setup(){
  var cases=(ReturnRepository)Proxy.newProxyInstance(ReturnRepository.class.getClassLoader(),new Class<?>[]{ReturnRepository.class},(p,m,args)->{reads++;arguments=args;return new PageImpl<>(List.of(new ReturnQueueRow("return","order","REQUESTED",Instant.EPOCH)),(Pageable)args[6],1);});
  var retail=new PosService(null,null){@Override public Scope scope(String user){return new Scope(7,9,user);}};
  service=new ReturnQueueService(cases,retail,Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"),ZoneOffset.UTC));
 }
 @Test void canonicalScopeAndDefaultUtcRangeAreUsed(){var r=service.queue("viewer","REQUESTED",null,null," ReTuRn ",0,12);assertThat(arguments).containsExactly(7L,9L,"REQUESTED",Instant.parse("2026-10-15T00:00:00Z"),Instant.parse("2026-11-14T00:00:00Z"),"return%",PageRequest.of(0,12));assertThat(r.branchId()).isEqualTo(9);assertThat(r.totalElements()).isEqualTo(1);assertThat(r.reference()).isEqualTo("ReTuRn");}
 @Test void invalidInputsStopBeforeRepositoryAccess(){
  for(String state:List.of("PAID","all",""))assertThatThrownBy(()->service.queue("viewer",state,null,null,"",0,12)).isInstanceOf(ResponseStatusException.class);
  for(String reference:List.of("%","_","x".repeat(37)))assertThatThrownBy(()->service.queue("viewer","ALL",null,null,reference,0,12)).isInstanceOf(ResponseStatusException.class);
  for(int page:List.of(-1,10001))assertThatThrownBy(()->service.queue("viewer","ALL",null,null,"",page,12)).isInstanceOf(ResponseStatusException.class);
  for(int size:List.of(0,49))assertThatThrownBy(()->service.queue("viewer","ALL",null,null,"",0,size)).isInstanceOf(ResponseStatusException.class);
  assertThatThrownBy(()->service.queue("viewer","ALL",LocalDate.of(2024,1,1),LocalDate.of(2026,1,1),"",0,12)).isInstanceOf(ResponseStatusException.class);
  assertThatThrownBy(()->service.queue("viewer","ALL",LocalDate.of(2026,1,2),LocalDate.of(2026,1,1),"",0,12)).isInstanceOf(ResponseStatusException.class);assertThat(reads).isZero();
 }
}
