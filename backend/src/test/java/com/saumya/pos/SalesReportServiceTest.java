package com.saumya.pos;
import com.saumya.pos.checkout.SalesReportService;
import com.saumya.pos.checkout.internal.*;
import com.saumya.pos.retail.PosService;
import java.lang.reflect.Proxy;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
class SalesReportServiceTest {
    final LocalDate today=LocalDate.of(2026,11,13);
    int reads;List<SalesAggregate> orders=List.of();List<SalesLineAggregate> lines=List.of();boolean forbidden;
    Object[] arguments;SalesReportService service;
    @BeforeEach void setup(){
        var repo=(SalesReportRepository)Proxy.newProxyInstance(SalesReportRepository.class.getClassLoader(),new Class<?>[]{SalesReportRepository.class},(proxy,method,args)->{reads++;arguments=args;return method.getName().equals("orders")?orders:lines;});
        var retail=new PosService(null,null,null,null){@Override public Scope scope(String username){assertThat(username).isEqualTo("cashier");if(forbidden)throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);return new Scope(7,9,username);}};
        service=new SalesReportService(repo,retail,Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"),ZoneOffset.UTC));
    }
    @Test void defaultsToSevenUtcDaysAndServerMembership(){
        var report=service.report("cashier",null,null,null);
        assertThat(report.days()).hasSize(7);assertThat(report.from()).isEqualTo(today.minusDays(6));assertThat(report.to()).isEqualTo(today);assertThat(report.branchId()).isEqualTo(9);
        assertThat(arguments).containsExactly(7L,9L,"ALL",Instant.parse("2026-11-07T00:00:00Z"),Instant.parse("2026-11-14T00:00:00Z"));assertThat(reads).isEqualTo(2);assertThat(report.totals().sales()).isEqualByComparingTo("0");
    }
    @Test void paidTotalsDoNotMultiplyByLinesOrIncludeReservations(){
        orders=List.of(new SalesAggregate(today,"IN_STORE","PAID",1,new BigDecimal("120"),new BigDecimal("150"),new BigDecimal("30"),1),new SalesAggregate(today,"ONLINE","DRAFT",2,new BigDecimal("900"),BigDecimal.ZERO,BigDecimal.ZERO,0),new SalesAggregate(today,"ONLINE","CANCELLED",3,new BigDecimal("700"),BigDecimal.ZERO,BigDecimal.ZERO,0),new SalesAggregate(today,"ONLINE","EXPIRED",4,new BigDecimal("600"),BigDecimal.ZERO,BigDecimal.ZERO,0));
        lines=List.of(new SalesLineAggregate(today,"IN_STORE",new BigDecimal("20"),3));
        var report=service.report("cashier",today,today,"all");var total=report.totals();
        assertThat(total.paidOrders()).isEqualTo(1);assertThat(total.sales()).isEqualByComparingTo("120");assertThat(total.tax()).isEqualByComparingTo("20");assertThat(total.beforeTax()).isEqualByComparingTo("100");assertThat(total.cashApplied()).isEqualByComparingTo("120");assertThat(total.units()).isEqualTo(3);assertThat(total.invoices()).isEqualTo(1);assertThat(total.unpaidOrders()).isEqualTo(2);assertThat(total.unpaidAmount()).isEqualByComparingTo("900");assertThat(total.cancelledOrders()).isEqualTo(3);assertThat(total.expiredOrders()).isEqualTo(4);
        assertThat(report.channels()).hasSize(2);assertThat(report.days().getFirst().totals()).isEqualTo(total);
    }
    @Test void invalidRangesAndChannelsDoNotReadData(){
        assertThatThrownBy(()->service.report("cashier",today,today.minusDays(1),"ALL")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.report("cashier",today.minusDays(366),today,"ALL")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.report("cashier",null,null,"FOREIGN")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.report("cashier",null,LocalDate.MIN,"ALL")).isInstanceOf(ResponseStatusException.class);assertThat(reads).isZero();
    }
    @Test void failedMembershipStopsBothAggregateQueries(){forbidden=true;assertThatThrownBy(()->service.report("cashier",null,null,"ALL")).isInstanceOf(ResponseStatusException.class);assertThat(reads).isZero();}
    @Test void oneChannelIsExplicitAndZeroDaysAreRetained(){var report=service.report("cashier",today.minusDays(2),today,"online");assertThat(report.channels()).extracting(SalesReportService.Channel::channel).containsExactly("ONLINE");assertThat(report.days()).hasSize(3);assertThat(report.days()).allSatisfy(day->assertThat(day.totals().paidOrders()).isZero());}
}
