package com.saumya.pos.platform;

import com.saumya.pos.identity.IdentityProvisioningService;
import com.saumya.pos.membership.MembershipService;
import com.saumya.pos.retail.BranchProvisioningService;
import com.saumya.pos.tenant.*;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service @Transactional
public class PlatformAdminService {
    private final TenantLifecycleService tenants;private final BranchProvisioningService branches;
    private final MembershipService memberships;private final IdentityProvisioningService identities;
    private final PlatformAuditService audit;private final HealthEndpoint health;private final java.time.Clock clock;
    public PlatformAdminService(TenantLifecycleService tenants,BranchProvisioningService branches,MembershipService memberships,
        IdentityProvisioningService identities,PlatformAuditService audit,HealthEndpoint health,java.time.Clock clock){
        this.tenants=tenants;this.branches=branches;this.memberships=memberships;this.identities=identities;this.audit=audit;this.health=health;this.clock=clock;
    }
    public record OnboardingResult(TenantLifecycleService.TenantSummary tenant,long initialBranchId,String vendorAdminUsername,
                                   String invitationToken,Instant invitationExpiresAt,boolean accountAlreadyActive){}
    public record Dashboard(List<TenantLifecycleService.StatusCount> tenantStatuses,String healthStatus,Instant checkedAt,
                            String platformRole,String catalogOwnership,String customerPaymentOwnership,
                            String subscriptionPaymentOwnership,String supportAccessPolicy){}
    public OnboardingResult onboard(TenantLifecycleService.Registration request,String actor,String correlation){
        var tenant=tenants.create(request);identities.ensureVendorEligible(tenant.initialVendorAdminUsername());
        long branch=branches.createInitialBranch(tenant.id(),"Main Branch");tenants.assignOnlineBranch(tenant.id(),branch);
        var invitation=identities.issueInvitation(tenant.initialVendorAdminUsername());
        var provisioned=memberships.provisionInitialVendorAdmin(tenant.id(),branch,tenant.initialVendorAdminUsername());
        audit.record(actor,"TENANT_CREATED","TENANT",Long.toString(tenant.id()),tenant.id(),"Tenant onboarding created; Vendor Admin membership provisioned",correlation);
        return new OnboardingResult(tenants.get(tenant.id()),branch,provisioned.username(),invitation.token(),invitation.expiresAt(),invitation.accountAlreadyActive());
    }
    public TenantLifecycleService.TenantSummary update(long id,TenantLifecycleService.ProfileUpdate update,String actor,String correlation){
        var result=tenants.updateProfile(id,update);audit.record(actor,"TENANT_UPDATED","TENANT",Long.toString(id),id,"Vendor profile updated",correlation);return result;
    }
    public TenantLifecycleService.TenantSummary transition(long id,TenantStatus target,String reason,String actor,String correlation){
        if(reason==null||reason.isBlank()||reason.strip().length()>500)throw new ResponseStatusException(BAD_REQUEST,"A lifecycle reason is required");
        var before=tenants.get(id);var result=tenants.transition(id,target);
        audit.record(actor,"TENANT_"+before.lifecycleStatus()+"_TO_"+target,"TENANT",Long.toString(id),id,reason.strip(),correlation);return result;
    }
    @Transactional(readOnly=true) public TenantLifecycleService.DirectoryPage tenants(String query,TenantStatus status,int page,int size){return tenants.search(query,status,page,size);}
    @Transactional(readOnly=true) public Dashboard dashboard(){return new Dashboard(tenants.statusCounts(),health.health().getStatus().getCode(),clock.instant(),"PLATFORM_ONLY","NO_PLATFORM_CATALOGUE","DIRECT_TO_VENDOR","PLATFORM_MERCHANT_ONLY","EXPLICIT_PURPOSED_TICKETED_MAX_4_HOURS");}
}
