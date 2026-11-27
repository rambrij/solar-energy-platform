package com.saumya.pos.integration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumya.pos.integration.internal.*;
import com.saumya.pos.tenant.TenantLifecycleService;
import java.time.Clock;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service public class InboxService{
 public record Inbound(String consumer,String providerId,Long tenant,String type,String payload){}
 private final InboxMessageRepository rows;private final ObjectMapper mapper;private final Clock clock;private final TenantLifecycleService tenants;
 public InboxService(InboxMessageRepository rows,ObjectMapper mapper,Clock clock,TenantLifecycleService tenants){this.rows=rows;this.mapper=mapper;this.clock=clock;this.tenants=tenants;}
 @Transactional public boolean consume(String consumer,String providerId,Long tenant,String type,Map<String,Object> payload,Consumer<Inbound> handler){
  if(consumer==null||consumer.isBlank()||consumer.length()>100||providerId==null||providerId.isBlank()||providerId.length()>160||type==null||type.isBlank()||type.length()>120||payload==null||handler==null)throw new IllegalArgumentException("Invalid inbound event");
  if(tenant!=null&&(tenant<=0||!tenants.isActive(tenant)))throw new IllegalStateException("Tenant is not active");
  String json;try{json=mapper.writeValueAsString(payload);}catch(JsonProcessingException e){throw new IllegalArgumentException("Invalid inbound payload",e);}
  if(json.length()>65536)throw new IllegalArgumentException("Inbound payload exceeds 64 KB");
  var prior=rows.findByConsumerKeyAndProviderEventId(consumer,providerId);
  if(prior.isPresent()){var m=prior.get();if(!java.util.Objects.equals(m.tenantId(),tenant)||!m.eventType().equals(type)||!m.payloadJson().equals(json))throw new IllegalStateException("Provider event ID reused with different content");m.duplicate();return false;}
  var receipt=rows.saveAndFlush(new InboxMessage(consumer,providerId,tenant,type,json,clock.instant()));
  handler.accept(new Inbound(consumer,providerId,tenant,type,json));return true;
 }
}