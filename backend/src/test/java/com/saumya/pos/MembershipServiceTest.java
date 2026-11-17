package com.saumya.pos;

import com.saumya.pos.membership.*;
import com.saumya.pos.membership.internal.*;
import com.saumya.pos.tenant.TenantLifecycleService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.mockito.Mockito.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MembershipServiceTest {
    @org.junit.jupiter.api.AfterEach void clearSecurity(){SecurityContextHolder.clearContext();}

    @SuppressWarnings("unchecked")
    private <T>T repository(Class<T> type,java.lang.reflect.InvocationHandler handler){
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);
    }
    private TenantLifecycleService tenants(){
        return repository(TenantLifecycleService.class,(p,m,a)->switch(m.getName()){
            case "isActive" -> true;
            case "get" -> new TenantLifecycleService.TenantSummary(7,"vendor-7","Vendor 7","Vendor 7",null,null,null,null,"PENDING",12L,"admin@example.com",Instant.EPOCH,Instant.EPOCH,0L);
            default -> null;
        });
    }
    @Test void permissionsStayInsideActiveMembershipAndPlatformGrantsNeverEscape(){
        var memberships=repository(EmployeeMembershipRepository.class,(p,m,a)->Optional.of(new EmployeeMembership("cashier",1,2,true)));
        var grants=repository(PermissionGrantRepository.class,(p,m,a)->{assertThat(a).containsExactly("cashier",1L);return List.of(new PermissionGrant(1,"cashier","POS_VIEW"),new PermissionGrant(1,"cashier","PLATFORM_TENANT_CREATE"));});
        var access=new MembershipService(memberships,grants,tenants()).require("cashier");
        assertThat(access.branch()).isEqualTo(2);assertThat(access.permissions()).containsExactly("POS_VIEW");
    }
    @Test void disabledMembershipCannotReadPermissionsOrOtherTenantData(){
        var memberships=repository(EmployeeMembershipRepository.class,(p,m,a)->Optional.of(new EmployeeMembership("cashier",1,2,false)));
        assertThatThrownBy(()->new MembershipService(memberships,null,tenants()).require("cashier")).hasMessageContaining("membership");
    }
    @Test void callerCaseNeverReplacesTheStoredActorOrPermissionOwner(){
        var memberships=repository(EmployeeMembershipRepository.class,(p,m,a)->{assertThat(m.getName()).isEqualTo("findByUsernameIgnoreCase");assertThat(a).containsExactly("CASHIER");return Optional.of(new EmployeeMembership("cashier",1,2,true));});
        var grants=repository(PermissionGrantRepository.class,(p,m,a)->{assertThat(a).containsExactly("cashier",1L);return List.of(new PermissionGrant(1,"cashier","POS_VIEW"));});
        var access=new MembershipService(memberships,grants,tenants()).require("CASHIER");
        assertThat(access.username()).isEqualTo("cashier");assertThat(access.permissions()).containsExactly("POS_VIEW");
    }
    @Test void vendorActorCannotInvokePlatformInitialMembershipProvisioning(){
        var memberships=org.mockito.Mockito.mock(EmployeeMembershipRepository.class);
        var grants=org.mockito.Mockito.mock(PermissionGrantRepository.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("vendor-admin","n/a",List.of(new SimpleGrantedAuthority("USER_CREATE"))));
        var service=new MembershipService(memberships,grants,tenants(),null,null);
        assertThatThrownBy(()->service.provisionInitialVendorAdmin(7,12,"new-admin@example.com"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        org.mockito.Mockito.verifyNoInteractions(memberships,grants);
    }

    @Test void firstVendorAdminProvisioningIsIdempotentAndNeverGrantsPlatformPermissions(){
        var rows=new HashMap<String,EmployeeMembership>();var rowsGrants=new ArrayList<PermissionGrant>();
        var memberships=repository(EmployeeMembershipRepository.class,(p,m,a)->{
            if(m.getName().equals("findByUsernameIgnoreCase"))return Optional.ofNullable(rows.get(((String)a[0]).toLowerCase()));
            if(m.getName().equals("saveAndFlush")){var item=(EmployeeMembership)a[0];rows.put(item.username(),item);return item;}
            return null;
        });
        var grants=repository(PermissionGrantRepository.class,(p,m,a)->{
            if(m.getName().equals("findByIdUsernameAndTenant"))return rowsGrants.stream().filter(g->g.permission()!=null).toList();
            if(m.getName().equals("save")){rowsGrants.add((PermissionGrant)a[0]);return a[0];}
            if(m.getName().equals("flush"))return null;
            return null;
        });
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("platform-admin","n/a",List.of(new SimpleGrantedAuthority("PLATFORM_TENANT_CREATE"))));
        var jdbc=org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(Long.class),eq(7L),eq("vendor-admin-initial"))).thenReturn(99L);
        var service=new MembershipService(memberships,grants,tenants(),jdbc,null,true,null);
        var created=service.provisionInitialVendorAdmin(7,12,"admin@example.com");
        var retried=service.provisionInitialVendorAdmin(7,12,"admin@example.com");
        assertThat(created.created()).isTrue();assertThat(retried.created()).isFalse();
        assertThat(rowsGrants).isNotEmpty().allSatisfy(g->assertThat(g.permission()).doesNotStartWith("PLATFORM_"));
        verify(jdbc).update(contains("INSERT INTO employee_role_assignments"),eq(7L),eq(12L),eq("admin@example.com"),eq(99L),eq("admin@example.com"));
        SecurityContextHolder.clearContext();
    }

    @Test void disabledLegacyFallbackFailsClosedForEmployeeWithoutRoleAssignments(){
        var memberships=repository(EmployeeMembershipRepository.class,(p,m,a)->Optional.of(new EmployeeMembership("cashier",1,2,true)));
        var grants=repository(PermissionGrantRepository.class,(p,m,a)->List.of(new PermissionGrant(1,"cashier","POS_VIEW")));
        var service=new MembershipService(memberships,grants,tenants(),null,null,false,null);
        assertThat(service.access("cashier")).get().extracting(MembershipService.Access::permissions).isEqualTo(List.of());
    }

    @Test void legacyFallbackAcceptsOnlyKnownVendorPermissions(){
        var memberships=repository(EmployeeMembershipRepository.class,(p,m,a)->Optional.of(new EmployeeMembership("cashier",1,2,true)));
        var grants=repository(PermissionGrantRepository.class,(p,m,a)->List.of(
            new PermissionGrant(1,"cashier","POS_VIEW"),
            new PermissionGrant(1,"cashier","PLATFORM_TENANT_CREATE"),
            new PermissionGrant(1,"cashier","MADE_UP_PERMISSION")));
        var service=new MembershipService(memberships,grants,tenants(),null,null,true,null);
        assertThat(service.access("cashier")).get().extracting(MembershipService.Access::permissions)
            .isEqualTo(List.of("POS_VIEW"));
    }

    @Test void explicitRoleAssignmentsOverrideLegacyGrantsAndRevocationNeverFallsBack(){
        var memberships=repository(EmployeeMembershipRepository.class,(p,m,a)->Optional.of(new EmployeeMembership("cashier",1,2,true)));
        var grants=repository(PermissionGrantRepository.class,(p,m,a)->{throw new AssertionError("legacy grants must not be read when assignments exist");});
        var jdbc=org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(1L),eq("cashier"))).thenReturn(1);
        when(jdbc.queryForList(anyString(),eq(String.class),eq(1L),eq("cashier"))).thenReturn(List.of());
        var service=new MembershipService(memberships,grants,tenants(),jdbc,null,true,null);
        assertThat(service.access("cashier")).get().extracting(MembershipService.Access::permissions).isEqualTo(List.of());
    }

    @Test void activeAssignedRolePermissionsAreFilteredToVendorRegistry(){
        var memberships=repository(EmployeeMembershipRepository.class,(p,m,a)->Optional.of(new EmployeeMembership("cashier",1,2,true)));
        var grants=repository(PermissionGrantRepository.class,(p,m,a)->{throw new AssertionError("legacy grants must not be read");});
        var jdbc=org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(1L),eq("cashier"))).thenReturn(1);
        when(jdbc.queryForList(anyString(),eq(String.class),eq(1L),eq("cashier")))
            .thenReturn(List.of("POS_VIEW","PLATFORM_TENANT_CREATE","UNKNOWN_PERMISSION"));
        var service=new MembershipService(memberships,grants,tenants(),jdbc,null,true,null);
        assertThat(service.access("cashier")).get().extracting(MembershipService.Access::permissions)
            .isEqualTo(List.of("POS_VIEW"));
    }

    @Test void permissionsAreUnionedAcrossMultipleActiveRoles(){
        var memberships=repository(EmployeeMembershipRepository.class,(p,m,a)->Optional.of(new EmployeeMembership("cashier",1,2,true)));
        var grants=repository(PermissionGrantRepository.class,(p,m,a)->{throw new AssertionError("role assignments are authoritative");});
        var jdbc=org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(1L),eq("cashier"))).thenReturn(3);
        when(jdbc.queryForList(anyString(),eq(String.class),eq(1L),eq("cashier")))
            .thenReturn(List.of("ORDER_VIEW","POS_VIEW","PRODUCT_VIEW"));
        var service=new MembershipService(memberships,grants,tenants(),jdbc,null,true,null);
        assertThat(service.access("cashier")).get().extracting(MembershipService.Access::permissions)
            .isEqualTo(List.of("ORDER_VIEW","POS_VIEW","PRODUCT_VIEW"));
    }

    @Test void suspendedTenantDoesNotResolveMembershipOrPermissions(){
        var memberships=repository(EmployeeMembershipRepository.class,(p,m,a)->Optional.of(new EmployeeMembership("cashier",1,2,true)));
        var grants=repository(PermissionGrantRepository.class,(p,m,a)->{throw new AssertionError("suspended tenant must be rejected first");});
        var suspended=repository(TenantLifecycleService.class,(p,m,a)->switch(m.getName()){
            case "isActive" -> false;
            default -> null;
        });
        var service=new MembershipService(memberships,grants,suspended,null,null,false,null);
        assertThat(service.access("cashier")).isEmpty();
    }
}
