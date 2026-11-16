package com.saumya.pos.platform;

import com.saumya.pos.identity.VendorAdminService;
import com.saumya.pos.shared.OutletProvisioner;
import com.saumya.pos.tenant.TenantException;
import com.saumya.pos.tenant.TenantLifecycleService;
import com.saumya.pos.tenant.TenantLifecycleService.NewTenant;
import com.saumya.pos.tenant.TenantLifecycleService.TenantProfile;
import com.saumya.pos.tenant.TenantLifecycleService.TenantView;
import com.saumya.pos.tenant.TenantStatus;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Platform orchestration of tenant onboarding and lifecycle. Persistence of tenants stays inside the tenant module. */
@Service @Transactional
public class TenantAdminService {
    public record Onboarding(TenantView tenant,boolean created) {}
    public record AdminResult(String username,boolean created,String temporaryPassword,long branchId) {}
    private final TenantLifecycleService tenants;private final VendorAdminService admins;private final OutletProvisioner outlets;private final PlatformAuditService audit;
    public TenantAdminService(TenantLifecycleService tenants,VendorAdminService admins,OutletProvisioner outlets,PlatformAuditService audit){this.tenants=tenants;this.admins=admins;this.outlets=outlets;this.audit=audit;}

    static org.springframework.web.server.ResponseStatusException map(TenantException e){
        return switch(e.kind()){case NOT_FOUND->Problems.missing(e.getMessage());case INVALID->Problems.invalid(e.getMessage());case CONFLICT->Problems.conflict(e.getMessage());};
    }

    /** Creates a tenant in PENDING. Replaying the same slug with the same profile returns the existing tenant instead of failing. */
    public Onboarding onboard(String actor,NewTenant request){
        try{
            var existing=request.slug()==null?java.util.Optional.<TenantView>empty():tenants.findBySlug(request.slug().strip());
            if(existing.isPresent()){
                var t=existing.get();
                if(Objects.equals(t.name(),request.name()==null?null:request.name().strip())&&Objects.equals(t.legalName(),request.legalName()==null?null:request.legalName().strip())&&Objects.equals(t.contactEmail(),request.contactEmail()==null?null:request.contactEmail().strip()))return new Onboarding(t,false);
                throw Problems.conflict("Slug is already in use by a different tenant");
            }
            var created=tenants.create(request);
            audit.record(actor,"TENANT_CREATED","TENANT",String.valueOf(created.id()),created.id(),null,"slug="+created.slug());
            return new Onboarding(created,true);
        }catch(TenantException e){throw map(e);}
    }
    /** Idempotent first Vendor Admin: a repeat for the same tenant and username returns the existing account without a password. */
    public AdminResult provisionVendorAdmin(String actor,long tenantId,String username){
        try{
            var tenant=tenants.find(tenantId).orElseThrow(()->Problems.missing("Tenant not found"));
            if(tenant.status()==TenantStatus.CLOSED)throw Problems.conflict("A closed tenant cannot receive a Vendor Admin");
            long branch=outlets.defaultOutlet(tenantId,"Main outlet");
            var result=admins.provision(tenantId,branch,username==null?null:username.strip());
            audit.record(actor,result.created()?"VENDOR_ADMIN_PROVISIONED":"VENDOR_ADMIN_PROVISION_REPLAYED","TENANT",String.valueOf(tenantId),tenantId,null,"username="+result.username());
            return new AdminResult(result.username(),result.created(),result.temporaryPassword(),branch);
        }catch(TenantException e){throw map(e);}
    }
    public TenantView transition(String actor,long tenantId,TenantStatus target,String reason){
        try{
            var before=tenants.find(tenantId).orElseThrow(()->Problems.missing("Tenant not found"));
            var after=tenants.transition(tenantId,target,reason,actor);
            audit.record(actor,"TENANT_"+target.name(),"TENANT",String.valueOf(tenantId),tenantId,reason.strip(),"from="+before.status()+" to="+after.status());
            return after;
        }catch(TenantException e){throw map(e);}
    }
    public TenantView updateProfile(String actor,long tenantId,TenantProfile profile){
        try{
            var updated=tenants.updateProfile(tenantId,profile);
            audit.record(actor,"TENANT_PROFILE_UPDATED","TENANT",String.valueOf(tenantId),tenantId,null,null);
            return updated;
        }catch(TenantException e){throw map(e);}
    }
    @Transactional(readOnly=true) public TenantView get(long id){return tenants.find(id).orElseThrow(()->Problems.missing("Tenant not found"));}
    @Transactional(readOnly=true) public TenantLifecycleService.Page list(TenantStatus status,String query,int page,int size){
        try{return tenants.list(status,query,page,size);}catch(TenantException e){throw map(e);}
    }
}
