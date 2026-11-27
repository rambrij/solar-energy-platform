package com.saumya.pos.shared;
import java.util.Map;
/** Public outbound-event port; domain modules do not depend on integration persistence. */
public interface EventOutbox {
 String append(Long tenantId,String actor,String aggregateType,String aggregateId,String eventType,String correlationId,Map<String,Object> payload);
}