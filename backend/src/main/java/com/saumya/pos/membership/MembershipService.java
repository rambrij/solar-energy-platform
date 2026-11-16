package com.saumya.pos.membership;

import com.saumya.pos.membership.internal.*;
import com.saumya.pos.tenant.TenantLifecycleService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service @Transactional(readOnly=true)
public class MembershipService {
    private static final List<String> VENDOR_ADMIN_PERMISSIONS=List.of(
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
        "POS_DISCOUNT_APPLY","POS_DISCOUNT_OVERRIDE","POS_PRICE_OVERRIDE","POS_TENDER_CASH_RECORD",
        "POS_TENDER_EXTERNAL_CONFIRM","POS_RECEIPT_VIEW","POS_RECEIPT_REPRINT","POS_RETURN_CREATE",
        "POS_REFUND_APPROVE","POS_CASH_IN","POS_CASH_OUT","POS_SAFE_DROP","POS_SESSION_REOPEN",
        "POS_SHIFT_VARIANCE_APPROVE","POS_REPORT_VIEW","POS_TENDER_MANUAL_VERIFY","POS_CASHIER_REPORT_VIEW"
    );
    private final EmployeeMembershipRepository memberships;
    private final PermissionGrantRepository grants;
    private final TenantLifecycleService tenants;
    public MembershipService(EmployeeMembershipRepository memberships,PermissionGrantRepository grants,TenantLifecycleService tenants){
        this.memberships=memberships;this.grants=grants;this.tenants=tenants;
    }
    public record Access(long tenant,long branch,String username,List<String> permissions){}
    public record Provisioning(String username,long tenantId,long branchId,boolean created,int permissionCount){}
    public Optional<Access> access(String username){
        return memberships.findByUsernameIgnoreCase(username).filter(EmployeeMembership::enabled)
            .filter(m->tenants.isActive(m.tenant()))
            .map(m->new Access(m.tenant(),m.branch(),m.username(),
                grants.findByIdUsernameAndTenant(m.username(),m.tenant()).stream()
                    .map(PermissionGrant::permission).filter(p->!p.startsWith("PLATFORM_")).toList()));
    }
    public boolean hasMembership(String username){return memberships.findByUsernameIgnoreCase(username).isPresent();}
    public Access require(String username){return access(username).orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"Active vendor membership required"));}

    @Transactional
    public Provisioning provisionInitialVendorAdmin(long tenantId,long branchId,String username){
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
        return new Provisioning(actor,tenantId,branchId,true,VENDOR_ADMIN_PERMISSIONS.size());
    }
}
