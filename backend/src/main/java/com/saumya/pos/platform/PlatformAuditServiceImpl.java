package com.saumya.pos.platform;

import com.saumya.pos.platform.internal.*;
import java.time.Clock;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service @Transactional
public class PlatformAuditServiceImpl implements PlatformAuditService {
    private final PlatformAuditRepository repository; private final Clock clock;
    public PlatformAuditServiceImpl(PlatformAuditRepository repository,Clock clock){this.repository=repository;this.clock=clock;}
    private String writePermission(String action){
        if("TENANT_CREATED".equals(action))return "PLATFORM_TENANT_CREATE";
        if("TENANT_UPDATED".equals(action))return "PLATFORM_TENANT_UPDATE";
        if("TENANT_SUSPENDED".equals(action)||action.endsWith("_TO_SUSPENDED"))return "PLATFORM_TENANT_SUSPEND";
        if("TENANT_CLOSED".equals(action)||action.endsWith("_TO_CLOSED"))return "PLATFORM_TENANT_CLOSE";
        if(action.endsWith("_TO_UNDER_REVIEW"))return "PLATFORM_TENANT_UPDATE";
        if(action.endsWith("_TO_ACTIVE"))return "PLATFORM_TENANT_REACTIVATE";
        if(action.startsWith("SUPPORT_ACCESS_"))return "PLATFORM_SUPPORT_ACCESS";
        if("PLATFORM_CONFIGURATION_UPDATED".equals(action))return "PLATFORM_CONFIG_MANAGE";
        if(action.startsWith("SUBSCRIPTION_PLAN_"))return "PLATFORM_PLAN_MANAGE";
        if(action.startsWith("TENANT_SUBSCRIPTION_"))return "PLATFORM_SUBSCRIPTION_ADJUST";
        if("PLATFORM_MFA_ENROLLMENT_ISSUED".equals(action))return "PLATFORM_IDENTITY_MANAGE";
        throw new ResponseStatusException(FORBIDDEN,"Unsupported platform audit action");
    }
    @Override public void record(String actor,String action,String type,String resourceId,Long tenantId,String reason,String correlationId){
        PlatformAuthorization.require(writePermission(action),actor);
        repository.saveAndFlush(new PlatformAuditEvent(actor,action,type,resourceId,tenantId,reason,"SUCCESS",correlationId,clock.instant()));
    }
    @Override @Transactional(readOnly=true) public AuditPage search(int page,int size,Long tenantId){
        PlatformAuthorization.require("PLATFORM_AUDIT_VIEW",null);
        if(page<0||page>10000||size<1||size>100)throw new ResponseStatusException(BAD_REQUEST,"Audit pagination outside supported bounds");
        var result=tenantId==null?repository.findAllByOrderByOccurredAtDescIdDesc(PageRequest.of(page,size)):
            repository.findByTenantIdOrderByOccurredAtDescIdDesc(tenantId,PageRequest.of(page,size));
        return new AuditPage(page,size,result.getTotalElements(),result.getTotalPages(),result.getContent().stream()
            .map(e->new AuditEntry(e.id(),e.actor(),e.action(),e.resourceType(),e.resourceId(),e.tenantId(),e.reason(),e.outcome(),e.correlationId(),e.occurredAt())).toList());
    }
}
