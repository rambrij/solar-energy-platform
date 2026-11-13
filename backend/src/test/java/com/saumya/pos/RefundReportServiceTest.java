package com.saumya.pos;
import com.saumya.pos.returns.RefundReportService;
import com.saumya.pos.returns.internal.*;
import com.saumya.pos.retail.PosService;
import java.lang.reflect.Proxy;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
class RefundReportServiceTest {
 final LocalDate today=LocalDate.of(2026,11,13);int reads;Object[] arguments;boolean denied;
 List<RefundHeaderAggregate> headers=List.of();List<RefundLineAggregate> lines=List.of();RefundReportService service;
 @BeforeEach void setup(){
  var repo=(RefundReportRepository)Proxy.newProxyInstance(RefundReportRepository.class.getClassLoader(),new Class<?>[]{RefundReportRepository.class},(p,m,args)->{reads++;arguments=args;return m.getName().equals("headers")?headers:lines;});
  var retail=new PosService(null,null){@Override public Scope scope(String user){if(denied)throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);return new Scope(7,9,user);}};
  service=new RefundReportService(repo,retail,Clock.fixed(Instant.parse("2026-11-13T00:30:00Z"),ZoneOffset.ofHours(-8)));
 }
 @Test void defaultsToSevenUtcDaysAndCanonicalScope(){var report=service.report("manager",null,null);assertThat(report.days()).hasSize(7);assertThat(report.to()).isEqualTo(today);assertThat(report.branchId()).isEqualTo(9);assertThat(arguments).containsExactly(7L,9L,Instant.parse("2026-11-07T00:00:00Z"),Instant.parse("2026-11-14T00:00:00Z"));assertThat(reads).isEqualTo(2);assertThat(report.totals().cash()).isEqualByComparingTo("0");}
 @Test void headersAreNotMultipliedByUnitsAndCashiersAreSorted(){
  headers=List.of(new RefundHeaderAggregate(today,"zoe",1,new BigDecimal("110")),new RefundHeaderAggregate(today,"amy",2,new BigDecimal("22")));
  lines=List.of(new RefundLineAggregate(today,"zoe",new BigDecimal("100"),new BigDecimal("10"),5),new RefundLineAggregate(today,"amy",new BigDecimal("20"),new BigDecimal("2"),2));
  var report=service.report("manager",today.minusDays(1),today);assertThat(report.totals().refunds()).isEqualTo(3);assertThat(report.totals().units()).isEqualTo(7);assertThat(report.totals().cash()).isEqualByComparingTo("132");assertThat(report.totals().beforeTax()).isEqualByComparingTo("120");assertThat(report.totals().tax()).isEqualByComparingTo("12");assertThat(report.cashiers()).extracting(RefundReportService.Cashier::cashier).containsExactly("amy","zoe");assertThat(report.days().getFirst().totals().refunds()).isZero();assertThat(report.days().getLast().totals()).isEqualTo(report.totals());
 }
 @Test void invalidRangesNeverReadPersistence(){for(LocalDate to:List.of(LocalDate.MIN,LocalDate.MAX,today.minusDays(1)))assertThatThrownBy(()->service.report("manager",today,to)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->service.report("manager",today.minusDays(366),today)).isInstanceOf(ResponseStatusException.class);assertThat(reads).isZero();}
 @Test void membershipFailureStopsQueries(){denied=true;assertThatThrownBy(()->service.report("manager",today,today)).isInstanceOf(ResponseStatusException.class);assertThat(reads).isZero();}
 @Test void maximumInclusiveRangePreservesAllZeroDays(){var report=service.report("manager",today.minusDays(365),today);assertThat(report.days()).hasSize(366);assertThat(report.cashiers()).isEmpty();}
}
