package com.saumya.pos;
import com.saumya.pos.checkout.CashierSalesService;
import com.saumya.pos.checkout.internal.*;
import com.saumya.pos.retail.PosService;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
class CashierSalesServiceTest {
 int reads;Object[] headerArgs,lineArgs;boolean denied;
 List<CashierSalesHeader> headers=List.of();List<CashierSalesLines> lines=List.of();CashierSalesService service;
 final LocalDate day=LocalDate.of(2026,11,13);
 @BeforeEach void setup(){
  var repo=(CashierSalesRepository)Proxy.newProxyInstance(CashierSalesRepository.class.getClassLoader(),new Class<?>[]{CashierSalesRepository.class},(p,m,args)->{reads++;if(m.getName().equals("headers")){headerArgs=args;return new PageImpl<>(headers,(Pageable)args[4],headers.size());}lineArgs=args;return lines;});
  var retail=new PosService(null,null){@Override public Scope scope(String user){if(denied)throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);return new Scope(7,9,user);}};
  service=new CashierSalesService(repo,retail,Clock.fixed(Instant.parse("2026-11-13T00:30:00Z"),ZoneOffset.ofHours(-8)));
 }
 @Test void usesUtcDefaultsAndCanonicalScopeWithoutReadingEmptyLines(){var result=service.report("manager",null,null,0,12);assertThat(result.to()).isEqualTo(day);assertThat(result.from()).isEqualTo(day.minusDays(6));assertThat(headerArgs).containsExactly(7L,9L,Instant.parse("2026-11-07T00:00:00Z"),Instant.parse("2026-11-14T00:00:00Z"),PageRequest.of(0,12));assertThat(reads).isEqualTo(1);assertThat(result.pageTotals().sales()).isEqualByComparingTo("0");}
 @Test void aggregatesOnlySelectedCashiersWithoutMultiplyingHeaderValues(){headers=List.of(new CashierSalesHeader("alice",1,new BigDecimal("110"),new BigDecimal("120"),BigDecimal.TEN,1));lines=List.of(new CashierSalesLines("alice",BigDecimal.TEN,3,new BigDecimal("20")));var result=service.report("manager",day,day,0,1);assertThat(lineArgs).containsExactly(7L,9L,Instant.parse("2026-11-13T00:00:00Z"),Instant.parse("2026-11-14T00:00:00Z"),List.of("alice"));var t=result.pageTotals();assertThat(t.paidOrders()).isEqualTo(1);assertThat(t.units()).isEqualTo(3);assertThat(t.sales()).isEqualByComparingTo("110");assertThat(t.beforeTax()).isEqualByComparingTo("100");assertThat(t.cashApplied()).isEqualByComparingTo("110");assertThat(t.discounts()).isEqualByComparingTo("20");assertThat(result.items().getFirst().totals()).isEqualTo(t);}
 @Test void invalidRangesAndPageBoundsDoNotReadData(){for(int size:new int[]{0,49})assertThatThrownBy(()->service.report("manager",day,day,0,size)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->service.report("manager",day,day,10001,12)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->service.report("manager",day.minusDays(366),day,0,12)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->service.report("manager",day,day.minusDays(1),0,12)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->service.report("manager",null,LocalDate.MIN,0,12)).isInstanceOf(ResponseStatusException.class);assertThat(reads).isZero();}
 @Test void inactiveMembershipStopsQueries(){denied=true;assertThatThrownBy(()->service.report("manager",day,day,0,12)).isInstanceOf(ResponseStatusException.class);assertThat(reads).isZero();}
 @Test void unexpectedCashierLinesCannotLeakIntoPage(){headers=List.of(new CashierSalesHeader("alice",1,BigDecimal.ONE,BigDecimal.ONE,BigDecimal.ZERO,0));lines=List.of(new CashierSalesLines("foreign",BigDecimal.ZERO,1,BigDecimal.ZERO));assertThatThrownBy(()->service.report("manager",day,day,0,12)).isInstanceOf(IllegalStateException.class);}
}
