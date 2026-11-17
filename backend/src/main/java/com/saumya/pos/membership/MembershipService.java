package com.saumya.pos.membership;

import com.saumya.pos.membership.internal.*;
import com.saumya.pos.tenant.TenantLifecycleService;
import com.saumya.pos.security.VendorMembershipAccess;
import com.saumya.pos.security.VendorMembershipResolver;
import com.saumya.pos.security.PlatformAuthorization;
import java.util.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service @Transactional(readOnly=true)
public class MembershipService implements VendorMembershipAccess {
    static final List<String> VENDOR_ADMIN_PERMISSIONS=List.of(
        "TENANT_PROFILE_VIEW","TENANT_PROFILE_UPDATE","USER_VIEW","USER_CREATE","USER_UPDATE","USER_DISABLE",
        "ROLE_VIEW","ROLE_CREATE","ROLE_UPDATE","ROLE_ASSIGN","CUSTOMER_RELATION_VIEW","CUSTOMER_RELATION_CREATE",
        "CUSTOMER_RELATION_UPDATE","CRM_NOTE_VIEW","CRM_NOTE_CREATE","CRM_NOTE_UPDATE",
        "PRODUCT_VIEW","PRODUCT_CREATE","PRODUCT_UPDATE","PRICE_VIEW","PRICE_UPDATE",
        "LEAD_VIEW","LEAD_CREATE","LEAD_UPDATE","LEAD_ASSIGN","QUOTATION_VIEW","QUOTATION_CREATE",
        "QUOTATION_UPDATE","QUOTATION_SEND","QUOTATION_APPROVE","QUOTATION_CONVERT",
        "ORDER_VIEW","ORDER_CREATE","ORDER_UPDATE","ORDER_CANCEL","INVENTORY_VIEW","INVENTORY_RECEIVE",
        "INVENTORY_RESERVE","INVENTORY_TRANSFER","INVENTORY_ADJUST","INVOICE_VIEW","INVOICE_CREATE",
        "INVOICE_SEND","VENDOR_PAYMENT_VIEW","VENDOR_PAYMENT_RECONCILE","VENDOR_PAYMENT_REFUND",
        "SHIPMENT_VIEW","SHIPMENT_UPDATE","RETURN_VIEW","RETURN_APPROVE","RETURN_RECEIVE",
        "REPORT_VIEW","REPORT_EXPORT","VENDOR_AUDIT_VIEW",
        "POS_VIEW","POS_SESSION_OPEN","POS_SESSION_CLOSE","POS_SALE_CREATE","POS_SALE_CANCEL",
        "POS_DISCOUNT_APPLY","POS_DISCOUNT_OVERRIDE","POS_DISCOUNT_APPROVE","CATALOG_PROMOTION_MANAGE","POS_PRICE_OVERRIDE","POS_TENDER_CASH_RECORD",
        "POS_TENDER_EXTERNAL_CONFIRM","POS_RECEIPT_VIEW","POS_RECEIPT_REPRINT","POS_RETURN_CREATE",
        "POS_REFUND_APPROVE","POS_CASH_IN","POS_CASH_OUT","POS_SAFE_DROP","POS_SESSION_REOPEN",
        "POS_SHIFT_VARIANCE_APPROVE","POS_REPORT_VIEW","POS_TENDER_MANUAL_VERIFY","POS_CASHIER_REPORT_VIEW",
        "POS_RETURN_VIEW","POS_RETURN_APPROVE","POS_REFUND_VIEW","POS_REPORT_EXPORT","POS_SETTINGS_MANAGE","POS_TENDER_EXTERNAL_RECORD",
        "POS_REFUND_CASH_RECORD","POS_REFUND_PRINT","POS_RETURN_INSPECT","POS_RETURN_AUDIT_VIEW","POS_TENDER_VIEW"
    );
    private final EmployeeMembershipRepository memberships;
    private final PermissionGrantRepository grants;
    private final TenantLifecycleService tenants;
    private final JdbcTemplate jdbc;
    private final TenantEntitlementPolicy entitlements;
    private final boolean legacyFallbackEnabled;
    private final Counter legacyFallbackUsage;
    private static final Logger log=LoggerFactory.getLogger(MembershipService.class);
    public MembershipService(EmployeeMembershipRepository memberships,PermissionGrantRepository grants,TenantLifecycleService tenants){
        this(memberships,grants,tenants,null,null,true,null);
    }
    public MembershipService(EmployeeMembershipRepository memberships,PermissionGrantRepository grants,TenantLifecycleService tenants,
        JdbcTemplate jdbc,TenantEntitlementPolicy entitlements){
        this(memberships,grants,tenants,jdbc,entitlements,true,null);
    }
    @Autowired
    public MembershipService(EmployeeMembershipRepository memberships,PermissionGrantRepository grants,TenantLifecycleService tenants,
        JdbcTemplate jdbc,TenantEntitlementPolicy entitlements,
        @Value("${auth.legacy-permission-fallback-enabled:true}") boolean legacyFallbackEnabled,
        MeterRegistry meterRegistry){
        this.memberships=memberships;this.grants=grants;this.tenants=tenants;this.jdbc=jdbc;this.entitlements=entitlements;
        this.legacyFallbackEnabled=legacyFallbackEnabled;
        this.legacyFallbackUsage=meterRegistry==null?null:Counter.builder("authorization.legacy.permission.fallback.usage")
            .description("Number of effective-permission resolutions that used legacy employee_permissions fallback")
            .register(meterRegistry);
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
                if(entitlements!=null)effective=entitlements.permissions(m.tenant(),effective);
                return new Access(m.tenant(),m.branch(),m.username(),effective);
            });
    }
    private List<String> effectiveVendorPermissions(String username,long tenant){
        // Any persisted assignment row (including revoked rows) is an explicit
        // role-management decision. Never resurrect employee_permissions after
        // revocation, role deactivation, or replacement with an empty role set.
        Integer assignmentCount=jdbc==null?null:jdbc.queryForObject(
            "SELECT COUNT(*) FROM employee_role_assignments WHERE tenant_id=? AND username=?",
            Integer.class,tenant,username);
        if(assignmentCount!=null&&assignmentCount>0){
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
        if(!legacyFallbackEnabled)return List.of();
        if(jdbc!=null){
            if(legacyFallbackUsage!=null)legacyFallbackUsage.increment();
            // Deliberately omit identity, tenant, permission and grant values.
            // This event is safe for operational log aggregation.
            log.warn("authorization_legacy_permission_fallback_used");
        }
        return grants.findByIdUsernameAndTenant(username,tenant).stream()
            .map(PermissionGrant::permission)
            .filter(Objects::nonNull)
            .filter(VendorPermissionRegistry.all()::contains)
            .sorted()
            .toList();
    }
    @Override public boolean hasMembership(String username){return memberships.findByUsernameIgnoreCase(username).isPresent();}
    public Access require(String username){return access(username).orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"Active vendor membership required"));}

    @Transactional
    public Provisioning provisionInitialVendorAdmin(long tenantId,long branchId,String username){
        PlatformAuthorization.require("PLATFORM_TENANT_CREATE",null);
        String actor=username==null?"":username.strip().toLowerCase(Locale.ROOT);
        if(actor.isBlank()||actor.length()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Valid Vendor Admin identity required");
        var existing=memberships.findByUsernameIgnoreCase(actor);
        if(existing.isPresent()){
            var current=existing.get();
            if(current.tenant()!=tenantId||current.branch()!=branchId)
                throw new ResponseStatusException(HttpStatus.CONFLICT,"The identity is already assigned to a different vendor membership");
            var existingGrants=grants.findByIdUsernameAndTenant(current.username(),tenantId);
            return new Provisioning(current.username(),tenantId,branchId,false,existingGrants.size());
        }
        if(!tenants.get(tenantId).lifecycleStatus().equals("PENDING") &&
           !tenants.get(tenantId).lifecycleStatus().equals("UNDER_REVIEW") &&
           !tenants.get(tenantId).lifecycleStatus().equals("ACTIVE"))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Tenant cannot receive an initial Vendor Admin in its current state");
        memberships.saveAndFlush(new EmployeeMembership(actor,tenantId,branchId,true));
        for(String permission:VENDOR_ADMIN_PERMISSIONS){
            if(!permission.startsWith("PLATFORM_"))grants.save(new PermissionGrant(tenantId,actor,permission));
        }
        grants.flush();
        if(jdbc!=null){
            jdbc.update("""
                INSERT INTO vendor_roles(tenant_id,role_key,display_name,active,created_by,updated_by)
                VALUES(?,?,?,?,?,?)
                """,tenantId,"vendor-admin-initial","Vendor Admin",true,actor,actor);
            Long roleId=jdbc.queryForObject("SELECT id FROM vendor_roles WHERE tenant_id=? AND role_key=?",Long.class,tenantId,"vendor-admin-initial");
            jdbc.batchUpdate("INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by) VALUES(?,?,?,?)",
                VENDOR_ADMIN_PERMISSIONS.stream().filter(p->!p.startsWith("PLATFORM_")).map(p->new Object[]{tenantId,roleId,p,actor}).toList());
            jdbc.update("INSERT INTO employee_role_assignments(tenant_id,branch_id,username,role_id,active,assigned_by) VALUES(?,?,?,?,TRUE,?)",
                tenantId,branchId,actor,roleId,actor);
            jdbc.update("INSERT INTO vendor_access_audit_events(tenant_id,actor_username,subject_username,resource_type,resource_id,action,reason) VALUES(?,?,?,?,?,?,?)",
                tenantId,actor,actor,"ROLE",roleId.toString(),"INITIAL_VENDOR_ADMIN_ROLE_ASSIGNED","Initial Vendor Admin grants migrated to role assignment");
        }
        return new Provisioning(actor,tenantId,branchId,true,VENDOR_ADMIN_PERMISSIONS.size());
    }
}
