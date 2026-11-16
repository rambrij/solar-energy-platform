package com.saumya.pos;

import com.saumya.pos.catalog.VendorDirectoryService;
import com.saumya.pos.tenant.TenantLifecycleService;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class VendorDirectoryServiceTest {
    int reads;Object[] arguments;VendorDirectoryService service;
    @BeforeEach void setup(){
        var tenants=(TenantLifecycleService)Proxy.newProxyInstance(TenantLifecycleService.class.getClassLoader(),new Class<?>[]{TenantLifecycleService.class},(p,m,args)->{
            if(m.getName().equals("searchActiveVendors")){reads++;arguments=args;String query=args[0]==null?"":((String)args[0]).strip();return new TenantLifecycleService.VendorDirectoryPage(query,(int)args[1],(int)args[2],1,1,List.of(new TenantLifecycleService.VendorCard(7,"solar","Solar Store")));}
            throw new AssertionError(m.getName());
        });
        service=new VendorDirectoryService(tenants);
    }
    @Test void delegatesBoundedSearchAndReturnsOnlyPublicCards(){
        var result=service.search("  SUN%_!  ",0,12);
        assertThat(arguments).containsExactly("  SUN%_!  ",0,12);
        assertThat(result.query()).isEqualTo("SUN%_!");
        assertThat(result.items().getFirst().slug()).isEqualTo("solar");
        assertThat(result.totalElements()).isEqualTo(1);
    }
    @Test void defaultSearchIsBounded(){
        service.search(null,2,24);
        assertThat(arguments).containsExactly(null,2,24);
    }
}
