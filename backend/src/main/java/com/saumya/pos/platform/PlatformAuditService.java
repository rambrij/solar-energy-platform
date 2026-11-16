package com.saumya.pos.platform;

import com.saumya.pos.platform.internal.PlatformAuditEvent;
import com.saumya.pos.platform.internal.PlatformAuditRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform audit trail. Events are append-only. Callers must pass already-sanitised details: secrets, passwords and
 * tenant-private content are never recorded here.
 */
@Service
public class PlatformAuditService {
    public record Event(String id,Instant occurredAt,String actor,String action,String targetType,String targetId,Long tenantId,String reason,String details,String outcome) {}
    public record Page(List<Event> items,int page,int size,long total) {}
    private final PlatformAuditRepository events;private final Clock clock;
    public PlatformAuditService(PlatformAuditRepository events,Clock clock){this.events=events;this.clock=clock;}

    private static String cut(String v,int max){return v==null?null:v.length()<=max?v:v.substring(0,max);}
    private void write(String actor,String action,String targetType,String targetId,Long tenant,String reason,String details,String outcome){
        events.saveAndFlush(new PlatformAuditEvent(UUID.randomUUID().toString(),clock.instant(),cut(actor,100),cut(action,60),cut(targetType,40),cut(targetId,64),tenant,cut(reason,500),cut(details,2000),outcome));
    }
    /** Joins the caller's transaction so an audit record and the change it describes commit or roll back together. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void record(String actor,String action,String targetType,String targetId,Long tenant,String reason,String details){write(actor,action,targetType,targetId,tenant,reason,details,"SUCCESS");}
    /** Denials are recorded in their own transaction so they survive the caller's rollback. */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void recordDenied(String actor,String action,String targetType,String targetId,Long tenant,String reason){write(actor,action,targetType,targetId,tenant,reason,null,"DENIED");}

    @Transactional(readOnly=true)
    public Page search(String action,Long tenant,int page,int size){
        if(page<0||size<1||size>100)throw Problems.invalid("Page must be >= 0 and size 1-100");
        var result=events.search(action==null?"":action.strip(),tenant==null?0L:tenant,PageRequest.of(page,size));
        return new Page(result.getContent().stream().map(e->new Event(e.id(),e.occurredAt(),e.actor(),e.action(),e.targetType(),e.targetId(),e.tenantId(),e.reason(),e.details(),e.outcome())).toList(),page,size,result.getTotalElements());
    }
}
