package com.saumya.pos;
import com.saumya.pos.returns.RefundHistoryService;
import com.saumya.pos.returns.internal.*;
import com.saumya.pos.retail.PosService;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
class RefundHistoryServiceTest {
 int reads;Pageable pageable;boolean denied;RefundHistoryService service;
 @BeforeEach void setup(){
  var repo=(CashRefundRepository)Proxy.newProxyInstance(CashRefundRepository.class.getClassLoader(),new Class<?>[]{CashRefundRepository.class},(p,m,args)->{reads++;pageable=(Pageable)args[1];var row=new CashRefund("refund",7,9,"return","order",4,"payer","requester","session","key","hash","quote",new BigDecimal("110"),"Private reason",Instant.parse("2026-11-13T00:00:00Z"));return new PageImpl<>(List.of(row),pageable,1);});
  var retail=new PosService(null,null,null,null){@Override public Scope scope(String user){if(denied)throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);return new Scope(7,9,user);}};
  service=new RefundHistoryService(repo,retail,Clock.fixed(Instant.parse("2026-11-13T00:30:00Z"),ZoneOffset.ofHours(-8)));
 }
 @Test void usesUtcDefaultsBoundedPagesAndStableOrder(){var r=service.history("manager",null,null,null,null,0,24);assertThat(r.from()).isEqualTo(LocalDate.of(2026,11,7));assertThat(r.to()).isEqualTo(LocalDate.of(2026,11,13));assertThat(r.branchId()).isEqualTo(9);assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Order.desc("paid"),Sort.Order.desc("id")));assertThat(r.items().getFirst().originalReceipt()).isEqualTo("9/4");assertThat(r.items().getFirst().amount()).isEqualByComparingTo("110");}
 @Test void invalidFiltersNeverReadData(){for(String ref:List.of("%","_","x".repeat(37)))assertThatThrownBy(()->service.history("manager",null,null,ref,null,0,24)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->service.history("manager",null,null,"",null,0,49)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->service.history("manager",null,null,"",null,10001,24)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->service.history("manager",LocalDate.of(2020,1,1),LocalDate.of(2026,11,13),"",null,0,24)).isInstanceOf(ResponseStatusException.class);assertThat(reads).isZero();}
 @Test void inactiveMembershipStopsLookup(){denied=true;assertThatThrownBy(()->service.history("manager",null,null,"",null,0,24)).isInstanceOf(ResponseStatusException.class);assertThat(reads).isZero();}
}
