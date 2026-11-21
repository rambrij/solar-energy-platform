package com.saumya.pos.membership;

import com.saumya.pos.membership.internal.EmployeeMembership;
import com.saumya.pos.membership.internal.EmployeeMembershipRepository;
import com.saumya.pos.tenant.TenantLifecycleService;
import com.saumya.pos.security.VendorMembershipAccess;
import com.saumya.pos.security.VendorMembershipResolver;
import com.saumya.pos.security.PlatformAuthorization;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service @Transactional(readOnly=true)
public class MembershipService implements VendorMembershipAccess {
    private final EmployeeMembershipRepository memberships;
    private final TenantLifecycleService tenants;
    private final JdbcTemplate jdbc;
    private final TenantEntitlementPolicy entitlements;
    public MembershipService(EmployeeMembershipRepository memberships,TenantLifecycleService tenants,JdbcTemplate jdbc,
        TenantEntitlementPolicy entitlements){
        this.memberships=Objects.requireNonNull(memberships);
        this.tenants=Objects.requireNonNull(tenants);
        this.jdbc=Objects.requireNonNull(jdbc);
        this.entitlements=Objects.requireNonNull(entitlements);
    }
    public record Access(long tenant,long branch,String username,List<String> permissions){}
    public record Provisioning(String username,long tenantId,long branchId,boolean created,int permissionCount){}
    @Override
    public Optional<VendorMembershipResolver.Membership> resolve(String username){
        return access(username).map(a->new VendorMembershipResolver.Membership(a.tenant(),a.branch(),a.username(),a.permissions()));
    }
    public Optional<Access> access(String username){
        return memberships.findByUsernameIgnoreCase(username).filter(EmployeeMembership::enabled)
            .filter(m->tenants.isActive(m.tenant()))
            .map(m->{
                List<String> effective=effectiveVendorPermissions(m.username(),m.tenant());
                effective=entitlements.permissions(m.tenant(),effective);
                return new Access(m.tenant(),m.branch(),m.username(),effective);
            });
    }
    private List<String> effectiveVendorPermissions(String username,long tenant){
        if(jdbc==null)return List.of();
        return jdbc.queryForList("""
            SELECT DISTINCT rp.permission
            FROM employee_role_assignments a
            JOIN vendor_roles r ON r.tenant_id=a.tenant_id AND r.id=a.role_id
            JOIN vendor_role_permissions rp ON rp.tenant_id=r.tenant_id AND rp.role_id=r.id
            WHERE a.tenant_id=? AND a.username=? AND a.active=TRUE AND r.active=TRUE
            ORDER BY rp.permission
            """,String.class,tenant,username).stream()
            .filter(VendorPermissionRegistry.all()::contains)
            .toList();
    }
    @Override public boolean hasMembership(String username){return memberships.findByUsernameIgnoreCase(username).isPresent();}
    public Access require(String username){return access(username).orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"Active vendor membership required"));}

    @Transactional
    public Provisioning provisionInitialVendorAdmin(long tenantId,long branchId,String username){
        PlatformAuthorization.require("PLATFORM_TENANT_CREATE",null);
        String actor=username==null?"":username.strip().toLowerCase(Locale.ROOT);
        if(actor.isBlank()||actor.length()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Valid Vendor Admin identity required");
        if(jdbc==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Role assignment provisioning is unavailable; initial Vendor Admin was not created");
        StandardRoleTemplates.lockTenant(jdbc,tenantId);
        var existing=memberships.findByUsernameIgnoreCase(actor);
        if(existing.isPresent()){
            var current=existing.get();
            if(current.tenant()!=tenantId||current.branch()!=branchId)
                throw new ResponseStatusException(HttpStatus.CONFLICT,"The identity is already assigned to a different vendor membership");
        }else{
            String status=tenants.get(tenantId).lifecycleStatus();
            if(!List.of("PENDING","UNDER_REVIEW","ACTIVE").contains(status))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Tenant cannot receive an initial Vendor Admin in its current state");
            memberships.saveAndFlush(new EmployeeMembership(actor,tenantId,branchId,true));
        }

        long roleId = StandardRoleTemplates.initialize(jdbc,tenantId,actor);
        if(existing.isEmpty()){
            jdbc.update("INSERT INTO employee_role_assignments(tenant_id,branch_id,username,role_id,active,assigned_by) VALUES(?,?,?,?,TRUE,?)",
                tenantId,branchId,actor,roleId,actor);
            jdbc.update("INSERT INTO vendor_access_audit_events(tenant_id,actor_username,subject_username,resource_type,resource_id,action,reason) VALUES(?,?,?,?,?,?,?)",
                tenantId,actor,actor,"ROLE",Long.toString(roleId),"INITIAL_VENDOR_ADMIN_ROLE_ASSIGNED","Initial Vendor Admin role assigned during tenant provisioning");
        }
        Integer permissionCount=jdbc.queryForObject(
            "SELECT COUNT(*) FROM vendor_role_permissions WHERE tenant_id=? AND role_id=?",Integer.class,tenantId,roleId);
        return new Provisioning(actor,tenantId,branchId,existing.isEmpty(),permissionCount==null?0:permissionCount);
    }

}
