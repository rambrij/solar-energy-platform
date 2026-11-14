package com.saumya.pos;

import com.saumya.pos.checkout.OrderHistoryService;
import com.saumya.pos.checkout.internal.*;
import com.saumya.pos.catalog.CatalogService;
import com.saumya.pos.identity.CustomerAccess;
import com.saumya.pos.retail.PosService;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

/** Lightweight repository doubles keep service units independent of Spring, a DB or bytecode agents. */
class OrderHistoryServiceTest {
    int reads, vendorReads, lineReads;String resolved;Pageable requestedPage;
    List<DraftOrder> results=List.of();boolean disabled;
    OrderHistoryService service;
    @BeforeEach void setup(){
        var orders=(DraftRepository)Proxy.newProxyInstance(DraftRepository.class.getClassLoader(),new Class<?>[]{DraftRepository.class},(proxy,method,args)->{
            reads++;
            if(method.getName().equals("findAll")){requestedPage=(Pageable)args[1];return new PageImpl<>(results,requestedPage,22);}
            if(method.getName().equals("findOne"))return Optional.empty();
            throw new AssertionError("Unexpected repository call: "+method.getName());
        });
        var lines=(DraftLineRepository)Proxy.newProxyInstance(DraftLineRepository.class.getClassLoader(),new Class<?>[]{DraftLineRepository.class},(proxy,method,args)->{lineReads++;throw new AssertionError("Unexpected line access");});
        var customers=new CustomerAccess(null){@Override public long resolve(String username){resolved=username;return 7;}};
        var retail=new PosService(null,null){@Override public Scope scope(String username){if(disabled)throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);return new Scope(1,2,username);}};
        var catalog=new CatalogService(null,null,null){@Override public VendorInfo vendor(long id){vendorReads++;return new VendorInfo(id,"vendor","Solar vendor",true);}};
        service=new OrderHistoryService(orders,lines,customers,retail,catalog);
    }
    @Test void rejectsInvalidFilterBoundsBeforeQuery(){
        for(String status:List.of("REFUNDED","unknown"))assertThatThrownBy(()->new OrderHistoryService.Filter(status,"",null,null,0,20)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->new OrderHistoryService.Filter("ALL","%",null,null,0,20)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->new OrderHistoryService.Filter("ALL","",LocalDate.now(),LocalDate.now().minusDays(1),0,20)).isInstanceOf(ResponseStatusException.class);
        for(int size:List.of(0,51))assertThatThrownBy(()->new OrderHistoryService.Filter("ALL","",null,null,0,size)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->new OrderHistoryService.Filter("ALL","",null,null,-1,20)).isInstanceOf(ResponseStatusException.class);
        assertThat(reads).isZero();
    }
    @Test void customerPageUsesBoundedStableSortingAndResolvesVendorOnce(){
        var draft=new DraftOrder("order",1,7L,null,null,null,"ONLINE","key","hash",BigDecimal.TEN,1,"owner",Instant.now(),false);results=List.of(draft,draft);
        var result=service.customer("buyer",new OrderHistoryService.Filter("draft","",null,null,2,10));
        assertThat(resolved).isEqualTo("buyer");assertThat(requestedPage.getPageSize()).isEqualTo(10);assertThat(requestedPage.getPageNumber()).isEqualTo(2);assertThat(requestedPage.getSort().toString()).isEqualTo("createdAt: DESC,id: DESC");
        assertThat(result.totalElements()).isEqualTo(22);assertThat(result.totalPages()).isEqualTo(3);assertThat(result.items()).extracting(OrderHistoryService.Order::vendorName).containsOnly("Solar vendor");assertThat(vendorReads).isEqualTo(1);assertThat(lineReads).isZero();
    }
    @Test void missingScopedOrderReturnsGeneric404WithoutReadingLines(){
        assertThatThrownBy(()->service.posDetail("cashier","foreign-order")).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(404));
        assertThat(reads).isEqualTo(1);assertThat(lineReads).isZero();assertThat(vendorReads).isZero();
    }
    @Test void unavailableEmployeeStopsBeforeOrderAccess(){
        disabled=true;
        assertThatThrownBy(()->service.pos("disabled",new OrderHistoryService.Filter("ALL","",null,null,0,20))).isInstanceOf(ResponseStatusException.class);
        assertThat(reads).isZero();assertThat(lineReads).isZero();
    }
}
