package com.saumya.pos.platform;

import com.saumya.pos.platform.internal.SupportGrant;
import com.saumya.pos.platform.internal.SupportGrantRepository;
import com.saumya.pos.shared.TenantAuditReader;
import com.saumya.pos.shared.TenantAuditReader.AuditEntry;
import com.saumya.pos.tenant.TenantLifecycleService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only route by which the platform operator may see tenant-private operational data: an explicit grant with
 * purpose, ticket and expiry, usable only by the grantee, read-only, and audited on grant, use, denial and revocation.
 */
@Service @Transactional
public class SupportAccessService {
    public record GrantView(String id,long tenantId,String grantedTo,String scope,String reason,String ticket,Instant grantedAt,Instant expiresAt,Instant revokedAt,String revokedBy,boolean active) {}
    public record AuditRead(String grantId,long tenantId,List<AuditEntry> entries) {}
    private final SupportGrantRepository grants;private final TenantLifecycleService tenants;private final TenantAuditReader reader;private final PlatformAuditService audit;private final Clock clock;
    public SupportAccessService(SupportGrantRepository grants,TenantLifecycleService tenants,TenantAuditReader reader,PlatformAuditService audit,Clock clock){this.grants=grants;this.tenants=tenants;this.reader=reader;this.audit=audit;this.clock=clock;}

    private GrantView view(SupportGrant g,Instant now){
        return new GrantView(g.id(),g.tenant(),g.grantedTo(),g.scope(),g.reason(),g.ticket(),g.grantedAt(),g.expiresAt(),g.revokedAt(),g.revokedBy(),SupportAccessPolicy.active(g.grantedAt(),g.expiresAt(),g.revokedAt(),now));
    }
    public GrantView grant(String actor,long tenantId,String reason,String ticket,int minutes){
        try{SupportAccessPolicy.validate(reason,ticket,minutes);}catch(SupportAccessPolicy.Rejected e){throw Problems.invalid(e.getMessage());}
        tenants.find(tenantId).orElseThrow(()->Problems.missing("Tenant not found"));
        var now=clock.instant();
        var saved=grants.saveAndFlush(new SupportGrant(UUID.randomUUID().toString(),tenantId,actor,SupportAccessPolicy.Scope.VENDOR_AUDIT_READ.name(),reason.strip(),ticket.strip(),now,SupportAccessPolicy.expiry(now,minutes)));
        audit.record(actor,"SUPPORT_ACCESS_GRANTED","SUPPORT_GRANT",saved.id(),tenantId,reason.strip(),"ticket="+saved.ticket()+" scope="+saved.scope()+" expires="+saved.expiresAt());
        return view(saved,now);
    }
    public GrantView revoke(String actor,String grantId){
        var grant=grants.findById(grantId).orElseThrow(()->Problems.missing("Grant not found"));
        if(grant.revokedAt()!=null)throw Problems.conflict("Grant is already revoked");
        var now=clock.instant();grant.revoke(actor,now);grants.saveAndFlush(grant);
        audit.record(actor,"SUPPORT_ACCESS_REVOKED","SUPPORT_GRANT",grantId,grant.tenant(),null,null);
        return view(grant,now);
    }
    @Transactional(readOnly=true) public List<GrantView> list(long tenantId){var now=clock.instant();return grants.findByTenantOrderByGrantedAtDescIdDesc(tenantId).stream().map(g->view(g,now)).toList();}

    /** Reads the tenant's own operational audit trail through an active grant. Only the grantee may use it, only before expiry/revocation. */
    public AuditRead readVendorAudit(String actor,String grantId,int limit){
        var grant=grants.findById(grantId).orElse(null);
        var now=clock.instant();
        boolean allowed=grant!=null&&grant.grantedTo().equals(actor)&&SupportAccessPolicy.Scope.VENDOR_AUDIT_READ.name().equals(grant.scope())&&SupportAccessPolicy.active(grant.grantedAt(),grant.expiresAt(),grant.revokedAt(),now);
        if(!allowed){
            audit.recordDenied(actor,"SUPPORT_ACCESS_DENIED","SUPPORT_GRANT",grantId==null?"unknown":grantId,grant==null?null:grant.tenant(),"Grant missing, expired, revoked or not held by caller");
            throw Problems.forbidden("No active support-access grant");
        }
        var entries=reader.recentAudit(grant.tenant(),limit);
        audit.record(actor,"SUPPORT_ACCESS_READ","SUPPORT_GRANT",grantId,grant.tenant(),grant.reason(),"scope="+grant.scope()+" entries="+entries.size());
        return new AuditRead(grantId,grant.tenant(),entries);
    }
}
