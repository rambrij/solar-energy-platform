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
    static final List<String> VENDOR_ADMIN_PERMISSIONS=List.of(
        "TENANT_PROFILE_VIEW","TENANT_PROFILE_UPDATE","USER_VIEW","USER_CREATE","USER_UPDATE","USER_DISABLE",
        "ROLE_VIEW","ROLE_CREATE","ROLE_UPDATE","ROLE_ASSIGN","CUSTOMER_RELATION_VIEW","CUSTOMER_RELATION_CREATE",
        "CUSTOMER_RELATION_UPDATE","CRM_NOTE_VIEW","CRM_NOTE_CREATE","CRM_NOTE_UPDATE",
        "PRODUCT_VIEW","PRODUCT_CREATE","PRODUCT_UPDATE","PRODUCT_ARCHIVE","PRICE_VIEW","PRICE_UPDATE",
        "LEAD_VIEW","LEAD_CREATE","LEAD_UPDATE","LEAD_ASSIGN","QUOTATION_VIEW","QUOTATION_CREATE",
        "QUOTATION_UPDATE","QUOTATION_SEND","QUOTATION_APPROVE","QUOTATION_CONVERT",
        "ORDER_VIEW","ORDER_CREATE","ORDER_UPDATE","ORDER_CANCEL","INVENTORY_VIEW","INVENTORY_RECEIVE",
        "INVENTORY_RESERVE","INVENTORY_TRANSFER","INVENTORY_ADJUST","INVOICE_VIEW","INVOICE_CREATE",
        "INVOICE_SEND","VENDOR_PAYMENT_VIEW","VENDOR_PAYMENT_RECONCILE","VENDOR_PAYMENT_REFUND",
        "SHIPMENT_VIEW","SHIPMENT_UPDATE","RETURN_VIEW","RETURN_APPROVE","RETURN_RECEIVE",
        "REPORT_VIEW","REPORT_EXPORT","VENDOR_AUDIT_VIEW","INVOICE_VOID",
        "INSTALLATION_VIEW","INSTALLATION_ASSIGN","INSTALLATION_UPDATE","INSTALLATION_COMMISSION",
        "SERVICE_VIEW","SERVICE_CREATE","SERVICE_ASSIGN","SERVICE_UPDATE","SERVICE_RESOLVE",
        "POS_VIEW","POS_SESSION_OPEN","POS_SESSION_CLOSE","POS_SALE_CREATE","POS_SALE_CANCEL",
        "POS_DISCOUNT_APPLY","POS_DISCOUNT_OVERRIDE","POS_DISCOUNT_APPROVE","CATALOG_PROMOTION_MANAGE","POS_PRICE_OVERRIDE","POS_TENDER_CASH_RECORD",
        "POS_TENDER_EXTERNAL_CONFIRM","POS_RECEIPT_VIEW","POS_RECEIPT_REPRINT","POS_RETURN_CREATE",
        "POS_REFUND_APPROVE","POS_CASH_IN","POS_CASH_OUT","POS_SAFE_DROP","POS_SESSION_REOPEN",
        "POS_SHIFT_VARIANCE_APPROVE","POS_REPORT_VIEW","POS_TENDER_MANUAL_VERIFY","POS_CASHIER_REPORT_VIEW",
        "POS_RETURN_VIEW","POS_RETURN_APPROVE","POS_REFUND_VIEW","POS_REPORT_EXPORT","POS_SETTINGS_MANAGE","POS_TENDER_EXTERNAL_RECORD",
        "POS_REFUND_CASH_RECORD","POS_REFUND_PRINT","POS_RETURN_INSPECT","POS_RETURN_AUDIT_VIEW","POS_TENDER_VIEW"
    );
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

        boolean roleExists=Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT COUNT(*)>0 FROM vendor_roles WHERE tenant_id=? AND role_key=?",
            Boolean.class,tenantId,"vendor-admin-initial"));
        if(!roleExists){
            jdbc.update("""
                INSERT INTO vendor_roles(tenant_id,role_key,display_name,active,created_by,updated_by)
                VALUES(?,?,?,?,?,?)
                """,tenantId,"vendor-admin-initial","Vendor Admin",true,actor,actor);
            Long newRoleId=jdbc.queryForObject("SELECT id FROM vendor_roles WHERE tenant_id=? AND role_key=?",
                Long.class,tenantId,"vendor-admin-initial");
            jdbc.batchUpdate("INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by) VALUES(?,?,?,?)",
                VENDOR_ADMIN_PERMISSIONS.stream().map(p->new Object[]{tenantId,newRoleId,p,actor}).toList());
        }
        Long roleId=jdbc.queryForObject("SELECT id FROM vendor_roles WHERE tenant_id=? AND role_key=?",
            Long.class,tenantId,"vendor-admin-initial");
        Integer assignmentHistory=jdbc.queryForObject(
            "SELECT COUNT(*) FROM employee_role_assignments WHERE tenant_id=? AND username=?",
            Integer.class,tenantId,actor);
        if(assignmentHistory==null||assignmentHistory==0){
            jdbc.update("INSERT INTO employee_role_assignments(tenant_id,branch_id,username,role_id,active,assigned_by) VALUES(?,?,?,?,TRUE,?)",
                tenantId,branchId,actor,roleId,actor);
            jdbc.update("INSERT INTO vendor_access_audit_events(tenant_id,actor_username,subject_username,resource_type,resource_id,action,reason) VALUES(?,?,?,?,?,?,?)",
                tenantId,actor,actor,"ROLE",roleId.toString(),"INITIAL_VENDOR_ADMIN_ROLE_ASSIGNED","Initial Vendor Admin role assigned during tenant provisioning");
        }
        Integer permissionCount=jdbc.queryForObject(
            "SELECT COUNT(*) FROM vendor_role_permissions WHERE tenant_id=? AND role_id=?",Integer.class,tenantId,roleId);
        return new Provisioning(actor,tenantId,branchId,existing.isEmpty(),permissionCount==null?0:permissionCount);
    }

}
