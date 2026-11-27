package com.saumya.pos;

import com.saumya.pos.membership.*;
import com.saumya.pos.membership.internal.*;
import com.saumya.pos.tenant.TenantLifecycleService;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MembershipServiceTest {
    @AfterEach void clearSecurity(){SecurityContextHolder.clearContext();}

    @SuppressWarnings("unchecked")
    private <T>T proxy(Class<T> type,java.lang.reflect.InvocationHandler handler){
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);
    }
    private EmployeeMembershipRepository memberships(EmployeeMembership membership){
        return proxy(EmployeeMembershipRepository.class,(p,m,a)->m.getName().equals("findByUsernameIgnoreCase")
            ?Optional.ofNullable(membership):null);
    }
    private TenantEntitlementPolicy passThroughEntitlements(){
        var policy=mock(TenantEntitlementPolicy.class);
        when(policy.permissions(anyLong(),anyList())).thenAnswer(call->call.getArgument(1));
        return policy;
    }
    private TenantLifecycleService activeTenant(){
        return proxy(TenantLifecycleService.class,(p,m,a)->switch(m.getName()){
            case "isActive" -> true;
            case "get" -> new TenantLifecycleService.TenantSummary(7,"vendor-7","Vendor 7","Vendor 7",null,null,null,null,"PENDING",12L,"admin@example.com",Instant.EPOCH,Instant.EPOCH,0L,null);
            default -> null;
        });
    }

    @Test void activeRoleAssignmentsAreTheOnlySourceAndUnionDistinctVendorPermissions(){
        var db=mock(JdbcTemplate.class);
        when(db.queryForList(anyString(),eq(String.class),eq(7L),eq("cashier")))
            .thenReturn(List.of("POS_VIEW","PRODUCT_VIEW","INVENTORY_RECEIVE"));
        var service=new MembershipService(memberships(new EmployeeMembership("cashier",7,12,true)),activeTenant(),db,passThroughEntitlements());
        assertThat(service.access("cashier")).get().extracting(MembershipService.Access::permissions)
            .isEqualTo(List.of("POS_VIEW","PRODUCT_VIEW","INVENTORY_RECEIVE"));
        verify(db).queryForList(argThat(sql->sql.contains("a.active=TRUE")&&sql.contains("r.active=TRUE")
            &&sql.contains("SELECT DISTINCT")),eq(String.class),eq(7L),eq("cashier"));
    }

    @Test void persistedRoleUnionIsFilteredThroughTheMandatoryTenantEntitlementPolicy(){
        var db=mock(JdbcTemplate.class);
        when(db.queryForList(anyString(),eq(String.class),eq(7L),eq("cashier")))
            .thenReturn(List.of("POS_VIEW","PRODUCT_VIEW","INVENTORY_RECEIVE"));
        var entitlements=mock(TenantEntitlementPolicy.class);
        when(entitlements.permissions(7L,List.of("POS_VIEW","PRODUCT_VIEW","INVENTORY_RECEIVE")))
            .thenReturn(List.of("POS_VIEW"));
        var service=new MembershipService(
            memberships(new EmployeeMembership("cashier",7,12,true)),activeTenant(),db,entitlements);

        assertThat(service.access("cashier")).get()
            .extracting(MembershipService.Access::permissions).isEqualTo(List.of("POS_VIEW"));
        verify(entitlements).permissions(7L,List.of("POS_VIEW","PRODUCT_VIEW","INVENTORY_RECEIVE"));
    }

    @Test void noRoleAssignmentFailsClosedWithoutReadingAnyOtherPermissionSource(){
        var db=mock(JdbcTemplate.class);
        when(db.queryForList(anyString(),eq(String.class),eq(7L),eq("cashier"))).thenReturn(List.of());
        var service=new MembershipService(memberships(new EmployeeMembership("cashier",7,12,true)),activeTenant(),db,passThroughEntitlements());
        assertThat(service.access("cashier")).get().extracting(MembershipService.Access::permissions).isEqualTo(List.of());
        verify(db).queryForList(anyString(),eq(String.class),eq(7L),eq("cashier"));
        verifyNoMoreInteractions(db);
    }

    @Test void revokingEveryRoleLeavesTheActiveEmployeeWithZeroPermissions(){
        var db=mock(JdbcTemplate.class);
        when(db.queryForList(anyString(),eq(String.class),eq(7L),eq("cashier"))).thenReturn(List.of());
        var service=new MembershipService(memberships(new EmployeeMembership("cashier",7,12,true)),activeTenant(),db,passThroughEntitlements());

        assertThat(service.access("cashier")).get().extracting(MembershipService.Access::permissions).isEqualTo(List.of());
        verify(db).queryForList(argThat(sql->sql.contains("a.active=TRUE")&&sql.contains("r.active=TRUE")),eq(String.class),eq(7L),eq("cashier"));
    }

    @Test void inactiveAndDeactivatedRolesGrantNoPermissions(){
        var db=mock(JdbcTemplate.class);
        when(db.queryForList(anyString(),eq(String.class),eq(7L),eq("cashier"))).thenReturn(List.of());
        var service=new MembershipService(memberships(new EmployeeMembership("cashier",7,12,true)),activeTenant(),db,passThroughEntitlements());
        assertThat(service.access("cashier")).get().extracting(MembershipService.Access::permissions).isEqualTo(List.of());
    }

    @Test void unknownAndPlatformPermissionsAreNeverReturnedFromRoleRows(){
        var db=mock(JdbcTemplate.class);
        when(db.queryForList(anyString(),eq(String.class),eq(7L),eq("cashier")))
            .thenReturn(List.of("POS_VIEW","PLATFORM_TENANT_CREATE","UNKNOWN_PERMISSION"));
        var service=new MembershipService(memberships(new EmployeeMembership("cashier",7,12,true)),activeTenant(),db,passThroughEntitlements());
        assertThat(service.access("cashier")).get().extracting(MembershipService.Access::permissions)
            .isEqualTo(List.of("POS_VIEW"));
    }

    @Test void disabledEmployeeAndSuspendedTenantResolveNoMembership(){
        var disabled=new MembershipService(memberships(new EmployeeMembership("cashier",7,12,false)),activeTenant(),mock(JdbcTemplate.class),passThroughEntitlements());
        assertThat(disabled.access("cashier")).isEmpty();
        var suspended=proxy(TenantLifecycleService.class,(p,m,a)->m.getName().equals("isActive")?false:null);
        var inactiveTenant=new MembershipService(memberships(new EmployeeMembership("cashier",7,12,true)),suspended,mock(JdbcTemplate.class),passThroughEntitlements());
        assertThat(inactiveTenant.access("cashier")).isEmpty();
    }

    @Test void initialVendorAdminIsProvisionedWithExplicitRoleAndNoLegacyGrantRepository(){
        var rows=new HashMap<String,EmployeeMembership>();
        var membershipRepo=proxy(EmployeeMembershipRepository.class,(p,m,a)->{
            if(m.getName().equals("findByUsernameIgnoreCase"))return Optional.ofNullable(rows.get(((String)a[0]).toLowerCase(Locale.ROOT)));
            if(m.getName().equals("saveAndFlush")){var employee=(EmployeeMembership)a[0];rows.put(employee.username(),employee);return employee;}
            return null;
        });
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "platform-admin","n/a",List.of(new SimpleGrantedAuthority("PLATFORM_TENANT_CREATE"),new SimpleGrantedAuthority(com.saumya.pos.security.AuthorizationContext.ACTOR_CONTEXT_PLATFORM))));
        var db=mock(JdbcTemplate.class);
        when(db.queryForObject(startsWith("SELECT id FROM vendor_roles"),eq(Long.class),eq(7L),anyString())).thenReturn(99L);
        when(db.queryForObject(startsWith("SELECT COUNT(*) FROM employee_role_assignments"),eq(Integer.class),eq(7L),eq("admin@example.com"))).thenReturn(0);
        when(db.queryForObject(startsWith("SELECT COUNT(*) FROM vendor_role_permissions"),eq(Integer.class),eq(7L),eq(99L))).thenReturn(77);
        var service=new MembershipService(membershipRepo,activeTenant(),db,passThroughEntitlements());
        var result=service.provisionInitialVendorAdmin(7,12,"admin@example.com");
        assertThat(result.created()).isTrue();
        assertThat(result.permissionCount()).isEqualTo(77);
        verify(db,times(6)).batchUpdate(contains("INSERT INTO vendor_role_permissions"),anyList());
        verify(db).update(contains("INSERT INTO employee_role_assignments"),eq(7L),eq(12L),eq("admin@example.com"),eq(99L),eq("admin@example.com"));
    }
}
