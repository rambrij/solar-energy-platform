package com.saumya.pos.tenant;

import com.saumya.pos.tenant.internal.TenantRepository;
import java.lang.reflect.Proxy;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;

class TenantServiceDirectoryValidationTest {
    @org.junit.jupiter.api.AfterEach void clearSecurity(){SecurityContextHolder.clearContext();}

    @Test void directTenantCreationRequiresPlatformGrantBeforeSequenceOrRepositoryAccess(){
        var repository=mock(TenantRepository.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("vendor-operator","n/a",java.util.List.of(new SimpleGrantedAuthority("POS_VIEW"))));
        var service=new TenantService(repository,null,Clock.systemUTC());
        assertThatThrownBy(()->service.create(null)).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("PLATFORM_TENANT_CREATE");
        verifyNoInteractions(repository);
    }

    @Test void invalidPublicDirectoryInputsAreRejectedBeforeTenantRepositoryReads(){
        int[] reads={0};
        var repository=(TenantRepository)Proxy.newProxyInstance(
            TenantRepository.class.getClassLoader(),
            new Class<?>[]{TenantRepository.class},
            (proxy,method,args)->{reads[0]++;throw new AssertionError("Repository must not be read for invalid input: "+method.getName());}
        );
        var service=new TenantService(repository,null,Clock.systemUTC());

        for(String query:new String[]{"x".repeat(121),"bad\nquery"}) {
            assertThatThrownBy(()->service.searchActiveVendors(query,0,12))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Search");
        }
        for(int page:new int[]{-1,10001}) {
            assertThatThrownBy(()->service.searchActiveVendors("",page,12))
                .isInstanceOf(ResponseStatusException.class);
        }
        for(int size:new int[]{0,49}) {
            assertThatThrownBy(()->service.searchActiveVendors("",0,size))
                .isInstanceOf(ResponseStatusException.class);
        }
        assertThat(reads[0]).isZero();
    }
}
