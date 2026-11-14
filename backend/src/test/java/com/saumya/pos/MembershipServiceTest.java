package com.saumya.pos;
import com.saumya.pos.membership.*;
import com.saumya.pos.membership.internal.*;
import com.saumya.pos.catalog.CatalogService;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class MembershipServiceTest {
 @SuppressWarnings("unchecked") private <T>T repository(Class<T> type,java.lang.reflect.InvocationHandler handler){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);}
 @Test void permissionsStayInsideActiveMembershipAndPlatformGrantsNeverEscape(){var memberships=repository(EmployeeMembershipRepository.class,(p,m,a)->Optional.of(new EmployeeMembership("cashier",1,2,true)));var grants=repository(PermissionGrantRepository.class,(p,m,a)->{assertThat(a).containsExactly("cashier",1L);return List.of(new PermissionGrant(1,"cashier","POS_VIEW"),new PermissionGrant(1,"cashier","PLATFORM_TENANT_CREATE"));});var catalog=new CatalogService(null,null,null){public VendorInfo vendor(long id){return new VendorInfo(id,"vendor","Vendor",true);}};var access=new MembershipService(memberships,grants,catalog).require("cashier");assertThat(access.branch()).isEqualTo(2);assertThat(access.permissions()).containsExactly("POS_VIEW");}
 @Test void disabledMembershipCannotReadPermissionsOrOtherTenantData(){var memberships=repository(EmployeeMembershipRepository.class,(p,m,a)->Optional.of(new EmployeeMembership("cashier",1,2,false)));assertThatThrownBy(()->new MembershipService(memberships,null,null).require("cashier")).hasMessageContaining("membership");}
}
