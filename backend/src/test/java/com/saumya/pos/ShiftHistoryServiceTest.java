package com.saumya.pos;
import com.saumya.pos.retail.*;
import com.saumya.pos.retail.internal.ClosedShiftRepository;
import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
class ShiftHistoryServiceTest {
 int reads;Object[] arguments;ShiftHistoryService service;
 @BeforeEach void setup(){
  var repository=(ClosedShiftRepository)Proxy.newProxyInstance(ClosedShiftRepository.class.getClassLoader(),new Class<?>[]{ClosedShiftRepository.class},(p,m,args)->{reads++;arguments=args;return new PageImpl<>(List.of(),(Pageable)args[6],0);});
  var retail=new PosService(null,null,null,null){@Override public Scope scope(String user){return new Scope(7,9,user);}};
  service=new ShiftHistoryService(repository,retail,Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"),ZoneOffset.UTC));
 }
 @Test void queriesOnlyCanonicalCashierAndResolvedInclusiveUtcDates(){var r=service.history("cashier",null,null," ReF ",0,12);assertThat(arguments).containsExactly(7L,9L,"cashier",Instant.parse("2026-10-15T00:00:00Z"),Instant.parse("2026-11-14T00:00:00Z"),"ref%",PageRequest.of(0,12));assertThat(r.branchId()).isEqualTo(9);assertThat(r.reference()).isEqualTo("ReF");}
 @Test void invalidInputsStopBeforeReads(){
  for(String reference:List.of("%","_","x".repeat(37)))assertThatThrownBy(()->service.history("cashier",null,null,reference,0,12)).isInstanceOf(ResponseStatusException.class);
  for(int page:List.of(-1,10001))assertThatThrownBy(()->service.history("cashier",null,null,"",page,12)).isInstanceOf(ResponseStatusException.class);
  for(int size:List.of(0,49))assertThatThrownBy(()->service.history("cashier",null,null,"",0,size)).isInstanceOf(ResponseStatusException.class);
  for(LocalDate from:List.of(LocalDate.of(2020,1,1),LocalDate.of(2027,1,1)))assertThatThrownBy(()->service.history("cashier",from,LocalDate.of(2026,11,13),"",0,12)).isInstanceOf(ResponseStatusException.class);
  assertThatThrownBy(()->service.history("cashier",null,LocalDate.MIN,"",0,12)).isInstanceOf(ResponseStatusException.class);assertThat(reads).isZero();
 }
}
