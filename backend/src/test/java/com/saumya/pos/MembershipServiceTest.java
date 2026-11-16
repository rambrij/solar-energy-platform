package com.saumya.pos;

import com.saumya.pos.membership.*;
import com.saumya.pos.membership.internal.*;
import com.saumya.pos.tenant.TenantLifecycleService;
import java.lang.reflect.Proxy;
import java.util.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MembershipServiceTest {
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
        var service=new MembershipService(memberships,grants,tenants());
        var created=service.provisionInitialVendorAdmin(7,12,"admin@example.com");
        var retried=service.provisionInitialVendorAdmin(7,12,"admin@example.com");
        assertThat(created.created()).isTrue();assertThat(retried.created()).isFalse();
        assertThat(rowsGrants).isNotEmpty().allSatisfy(g->assertThat(g.permission()).doesNotStartWith("PLATFORM_"));
    }
}
