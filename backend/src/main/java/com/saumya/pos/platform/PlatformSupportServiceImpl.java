package com.saumya.pos.platform;

import com.saumya.pos.platform.internal.*;
import com.saumya.pos.tenant.TenantLifecycleService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service @Transactional
public class PlatformSupportServiceImpl implements PlatformSupportService {
    private final PlatformSupportRepository repository;private final TenantLifecycleService tenants;
    private final PlatformAuditService audit;private final Clock clock;
    public PlatformSupportServiceImpl(PlatformSupportRepository repository,TenantLifecycleService tenants,
        PlatformAuditService audit,Clock clock){this.repository=repository;this.tenants=tenants;this.audit=audit;this.clock=clock;}
    private void requirePlatform(String actor,String permission){PlatformAuthorization.require(permission,actor);}
    @Override public SupportAccess grant(long tenantId,String actor,String purpose,String ticket,Instant expiry,String correlation){
        requirePlatform(actor,"PLATFORM_SUPPORT_ACCESS");
        tenants.get(tenantId);
        String why=required(purpose,500,"Purpose");String ref=required(ticket,120,"Ticket/reference");
        Instant now=clock.instant();
        if(expiry==null||!expiry.isAfter(now)||expiry.isAfter(now.plus(Duration.ofHours(4))))
            throw new ResponseStatusException(BAD_REQUEST,"Support access must expire within four hours");
        if(!ref.matches("[A-Za-z0-9._:/-]{3,120}"))throw new ResponseStatusException(BAD_REQUEST,"Ticket/reference contains unsupported characters");
        var row=repository.saveAndFlush(new PlatformSupportGrant(tenantId,actor,why,ref,now,expiry));
        audit.record(actor,"SUPPORT_ACCESS_GRANTED","SUPPORT_ACCESS",Long.toString(row.id()),tenantId,why,correlation);
        return view(row,now);
    }
    @Override public SupportAccess revoke(long id,String actor,String reason,String correlation){
        requirePlatform(actor,"PLATFORM_SUPPORT_ACCESS");
        String why=required(reason,500,"Reason");
        var row=repository.findById(id).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Support access unavailable"));
        Instant now=clock.instant();
        if(row.revokedAt()!=null)return view(row,now);
        if(!row.activeAt(now))throw new ResponseStatusException(CONFLICT,"Support access has expired");
        row.revoke(actor,now);
        audit.record(actor,"SUPPORT_ACCESS_REVOKED","SUPPORT_ACCESS",Long.toString(id),row.tenantId(),why,correlation);
        return view(row,now);
    }
    @Override @Transactional(readOnly=true) public List<SupportAccess> recent(){
        PlatformAuthorization.require("PLATFORM_SUPPORT_ACCESS",null);
        Instant now=clock.instant();return repository.findTop100ByOrderByGrantedAtDesc().stream().map(row->view(row,now)).toList();
    }
    private static SupportAccess view(PlatformSupportGrant row,Instant now){return new SupportAccess(row.id(),row.tenantId(),row.actor(),row.purpose(),row.ticketReference(),row.grantedAt(),row.expiresAt(),row.revokedAt(),row.revokedBy(),row.activeAt(now));}
    private static String required(String v,int max,String label){if(v==null||v.isBlank()||v.strip().length()>max)throw new ResponseStatusException(BAD_REQUEST,label+" is required");return v.strip();}
}
