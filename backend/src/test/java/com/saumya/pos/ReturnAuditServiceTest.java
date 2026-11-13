package com.saumya.pos;
import com.saumya.pos.returns.ReturnAuditService;
import com.saumya.pos.returns.internal.*;
import com.saumya.pos.retail.PosService;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
class ReturnAuditServiceTest {
 int eventReads,caseReads;boolean missing;Object[] arguments;ReturnAuditService service;
 @BeforeEach void setup(){
  var cases=(ReturnRepository)Proxy.newProxyInstance(ReturnRepository.class.getClassLoader(),new Class<?>[]{ReturnRepository.class},(p,m,args)->{caseReads++;assertThat(args).containsExactly(7L,9L,"return-id");return missing?Optional.empty():Optional.of(new ReturnCase("return-id",7,9,"order","actor","session","key","hash","Private reason","ORIGINAL_RECEIPT","Private proof",Instant.EPOCH));});
  var events=(ReturnEventRepository)Proxy.newProxyInstance(ReturnEventRepository.class.getClassLoader(),new Class<?>[]{ReturnEventRepository.class},(p,m,args)->{eventReads++;arguments=args;return new PageImpl<>(List.of(new ReturnAuditRow("event","reviewer","APPROVED",Instant.EPOCH)),(Pageable)args[3],1);});
  var retail=new PosService(null,null,null,null){@Override public Scope scope(String user){return new Scope(7,9,user);}};service=new ReturnAuditService(cases,events,retail);
 }
 @Test void bindsBranchOwnershipAndReturnsExplicitMetadata(){var r=service.history("viewer","return-id",0,12);assertThat(arguments).containsExactly(7L,9L,"return-id",PageRequest.of(0,12));assertThat(r.state()).isEqualTo("REQUESTED");assertThat(r.items().getFirst().actor()).isEqualTo("reviewer");assertThat(r.totalElements()).isEqualTo(1);}
 @Test void missingOrForeignCaseCannotQueryEvents(){missing=true;assertThatThrownBy(()->service.history("viewer","return-id",0,12)).isInstanceOf(ResponseStatusException.class);assertThat(eventReads).isZero();}
 @Test void invalidBoundsAndReferenceStopBeforeReads(){assertThatThrownBy(()->service.history("viewer"," ",0,12)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->service.history("viewer","return-id",-1,12)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->service.history("viewer","return-id",0,49)).isInstanceOf(ResponseStatusException.class);assertThat(caseReads).isZero();assertThat(eventReads).isZero();}
}
