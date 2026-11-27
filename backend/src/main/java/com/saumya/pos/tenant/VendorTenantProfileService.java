package com.saumya.pos.tenant;

import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.tenant.internal.Tenant;
import com.saumya.pos.tenant.internal.TenantProfileAuditEvent;
import com.saumya.pos.tenant.internal.TenantProfileAuditRepository;
import com.saumya.pos.tenant.internal.TenantRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VendorTenantProfileService {
    private final TenantRepository tenants;
    private final TenantProfileAuditRepository audit;
    private final AuthorizationContext authorization;
    private final Clock clock;
    public VendorTenantProfileService(TenantRepository tenants,TenantProfileAuditRepository audit,AuthorizationContext authorization,Clock clock) {
        this.tenants=tenants;this.audit=audit;this.authorization=authorization;this.clock=clock;
    }
    public record Profile(long tenantId,String slug,String name,String legalName,String contactEmail,String contactPhone,String taxIdentifier,String address,String lifecycleStatus,long version,Instant updatedAt) {}
    public record Update(long expectedVersion,String name,String legalName,String contactEmail,String contactPhone,String taxIdentifier,String address) {}
    private AuthorizationContext.Context actor(String permission) {
        var context=authorization.current();context.requireVendorMembership();context.requirePermission(permission);return context;
    }
    @Transactional(readOnly=true)
    public Profile read() {
        var context=actor("TENANT_PROFILE_VIEW");
        Tenant tenant=tenants.findById(context.tenantId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Vendor profile unavailable"));
        return view(tenant);
    }
    @Transactional
    public Profile update(Update request) {
        if(request==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vendor profile update is required");
        var context=actor("TENANT_PROFILE_UPDATE");
        Tenant tenant=tenants.lockById(context.tenantId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Vendor profile unavailable"));
        if(request.expectedVersion()!=tenant.version())throw new ResponseStatusException(HttpStatus.CONFLICT,"Vendor profile changed; reload before saving");
        String name=TenantService.required(request.name(),160,"Display name");
        String legalName=TenantService.required(request.legalName(),180,"Legal name");
        String contactEmail=TenantService.emailOptional(request.contactEmail());
        String contactPhone=TenantService.optional(request.contactPhone(),30);
        String taxIdentifier=TenantService.optional(request.taxIdentifier(),40);
        String address=TenantService.optional(request.address(),500);
        List<String> changed=new ArrayList<>();
        if(!Objects.equals(tenant.name(),name))changed.add("name");
        if(!Objects.equals(tenant.legalName(),legalName))changed.add("legalName");
        if(!Objects.equals(tenant.contactEmail(),contactEmail))changed.add("contactEmail");
        if(!Objects.equals(tenant.contactPhone(),contactPhone))changed.add("contactPhone");
        if(!Objects.equals(tenant.taxIdentifier(),taxIdentifier))changed.add("taxIdentifier");
        if(!Objects.equals(tenant.address(),address))changed.add("address");
        if(changed.isEmpty())return view(tenant);
        long beforeVersion=tenant.version();
        try {
            Instant now=clock.instant();
            tenant.updateProfile(name,legalName,contactEmail,contactPhone,taxIdentifier,address,now);
            Tenant saved=tenants.saveAndFlush(tenant);
            audit.saveAndFlush(new TenantProfileAuditEvent(context.tenantId(),context.username(),"PROFILE_UPDATED",String.join(",",changed),beforeVersion,saved.version(),now));
            return view(saved);
        } catch(IllegalStateException failure) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,failure.getMessage());
        } catch(OptimisticLockingFailureException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Vendor profile changed; reload before saving");
        }
    }
    private static Profile view(Tenant tenant) {
        return new Profile(tenant.id(),tenant.slug(),tenant.name(),tenant.legalName(),tenant.contactEmail(),tenant.contactPhone(),tenant.taxIdentifier(),tenant.address(),tenant.lifecycleStatus().name(),tenant.version(),tenant.updatedAt());
    }
}
