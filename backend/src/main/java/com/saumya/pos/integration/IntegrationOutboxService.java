package com.saumya.pos.integration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumya.pos.integration.internal.*;
import com.saumya.pos.shared.EventOutbox;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
@Service public class IntegrationOutboxService implements EventOutbox{
 public record Event(String id,Long tenant,String actor,String aggregate,String aggregateId,String type,int version,String correlation,String payload,Instant createdAt){}
 private final OutboxMessageRepository rows;private final ObjectMapper mapper;private final Clock clock;private final int maxAttempts;
 public IntegrationOutboxService(OutboxMessageRepository rows,ObjectMapper mapper,Clock clock,@Value("${integration.outbox.max-attempts:10}") int max){if(max<1||max>25)throw new IllegalArgumentException("max attempts must be 1..25");this.rows=rows;this.mapper=mapper;this.clock=clock;maxAttempts=max;}
 @Override @Transactional public String append(Long tenant,String actor,String aggregate,String aggregateId,String type,String correlation,Map<String,Object> payload){
  if(tenant!=null&&tenant<=0)throw new IllegalArgumentException("Invalid tenant");
  require(actor,100);require(aggregate,80);require(aggregateId,100);require(type,120);
  if(payload==null)throw new IllegalArgumentException("Payload required");
  String json;try{json=mapper.writeValueAsString(payload);}catch(JsonProcessingException e){throw new IllegalArgumentException("Invalid event payload",e);}
  if(json.length()>65536)throw new IllegalArgumentException("Event payload exceeds 64 KB");
  String id=UUID.randomUUID().toString();String corr=StringUtils.hasText(correlation)?correlation:UUID.randomUUID().toString();
  rows.save(new OutboxMessage(id,tenant,actor,aggregate,aggregateId,type,corr,json,clock.instant()));return id;
 }
 @Transactional public List<Event> claim(String worker,int limit,Duration lease){
  Instant now=clock.instant();return rows.claimable(now,PageRequest.of(0,Math.max(1,Math.min(100,limit)))).stream().map(m->{m.claim(worker,now.plus(lease));return new Event(m.eventId(),m.tenantId(),m.actor(),m.aggregateType(),m.aggregateId(),m.eventType(),m.schemaVersion(),m.correlationId(),m.payloadJson(),m.createdAt());}).toList();
 }
 @Transactional public void published(String id,String worker){rows.findByEventIdAndLeaseOwnerAndState(id,worker,"PROCESSING").ifPresent(m->m.published(clock.instant()));}
 @Transactional public void failed(String id,String worker,Throwable cause){rows.findByEventIdAndLeaseOwnerAndState(id,worker,"PROCESSING").ifPresent(m->{long delay=Math.min(3600L,5L<<Math.min(10,Math.max(0,m.attemptCount()-1)));m.failed(clock.instant().plusSeconds(delay),cause.getClass().getSimpleName(),maxAttempts);});}
 private void require(String s,int max){if(!StringUtils.hasText(s)||s.length()>max)throw new IllegalArgumentException("Invalid event metadata");}
}