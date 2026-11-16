package com.saumya.pos.platform;

import com.saumya.pos.platform.internal.*;
import com.saumya.pos.security.AuthorizationContext;
import java.time.Clock;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service @Transactional
public class PlatformAuditServiceImpl implements PlatformAuditService {
    private final PlatformAuditRepository repository; private final Clock clock;private final AuthorizationContext authorization;
    public PlatformAuditServiceImpl(PlatformAuditRepository repository,Clock clock,AuthorizationContext authorization){
        this.repository=repository;this.clock=clock;this.authorization=authorization;
    }
    @Override public void record(String actor,String action,String type,String resourceId,Long tenantId,String reason,String correlationId){
        repository.saveAndFlush(new PlatformAuditEvent(actor,action,type,resourceId,tenantId,reason,"SUCCESS",correlationId,clock.instant()));
    }
    @Override @Transactional(readOnly=true) public AuditPage search(int page,int size,Long tenantId){
        var context=authorization.current();
        if(!context.isPlatformUser())throw new ResponseStatusException(FORBIDDEN,"Platform identity required");
        context.requirePermission("PLATFORM_AUDIT_VIEW");
        if(page<0||page>10000||size<1||size>100)throw new ResponseStatusException(BAD_REQUEST,"Audit pagination outside supported bounds");
        var result=tenantId==null?repository.findAllByOrderByOccurredAtDescIdDesc(PageRequest.of(page,size)):
            repository.findByTenantIdOrderByOccurredAtDescIdDesc(tenantId,PageRequest.of(page,size));
        return new AuditPage(page,size,result.getTotalElements(),result.getTotalPages(),result.getContent().stream()
            .map(e->new AuditEntry(e.id(),e.actor(),e.action(),e.resourceType(),e.resourceId(),e.tenantId(),e.reason(),e.outcome(),e.correlationId(),e.occurredAt())).toList());
    }
}
