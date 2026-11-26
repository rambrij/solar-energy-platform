package com.saumya.pos.fulfillment;
import com.saumya.pos.checkout.OrderHistoryService;
import com.saumya.pos.security.AuthorizationContext;
import java.math.BigDecimal;import java.sql.Timestamp;import java.time.Instant;import java.util.*;
import org.springframework.http.HttpStatus;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import org.springframework.web.server.ResponseStatusException;
@Service @Transactional(readOnly=true)
public class ShipmentService {
 private final JdbcTemplate db;private final AuthorizationContext auth;private final OrderHistoryService orderHistory;
 public ShipmentService(JdbcTemplate db,AuthorizationContext auth,OrderHistoryService orderHistory){this.db=db;this.auth=auth;this.orderHistory=orderHistory;}
 public record EligibleOrder(String orderId,Instant orderedAt,BigDecimal total){}
 public record Event(long id,String eventCode,String description,String reason,String actorUsername,Instant occurredAt){}
 public record CustomerEvent(long id,String eventCode,String description,Instant occurredAt){}
 public record Shipment(long id,String orderId,String carrier,String trackingReference,String trackingUrl,Instant createdAt,List<Event> events){}
 public record CustomerShipment(long id,String carrier,String trackingReference,String trackingUrl,Instant createdAt,List<CustomerEvent> events){}
 private AuthorizationContext.Context vendor(String p){var c=auth.current();c.requireVendorMembership();c.requirePermission(p);return c;}
 private ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Shipment or order unavailable");}
 private String clean(String s,int n,String label){String v=s==null?"":s.strip();if(v.isEmpty()||v.length()>n||v.codePoints().anyMatch(Character::isISOControl))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,label+" is invalid");return v;}
 private String url(String s){if(s==null||s.isBlank())return null;String v=s.strip();if(v.length()>1000||!v.matches("https://\\S+"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tracking URL must use HTTPS");return v;}
 private Instant time(Object x){
  if(x instanceof Timestamp t)return t.toInstant();
  if(x instanceof Instant i)return i;
  if(x instanceof java.time.LocalDateTime d)return d.toInstant(java.time.ZoneOffset.UTC);
  throw new IllegalStateException("Unsupported database timestamp representation");
 }
 private List<Event> events(long tenant,long id){return db.queryForList("SELECT id,event_code,description,reason,actor_username,occurred_at FROM shipment_events WHERE tenant_id=? AND shipment_id=? ORDER BY occurred_at,id",tenant,id).stream().map(r->new Event(((Number)r.get("id")).longValue(),(String)r.get("event_code"),(String)r.get("description"),(String)r.get("reason"),(String)r.get("actor_username"),time(r.get("occurred_at")))).toList();}
 private Shipment view(long tenant,Map<String,Object> r){long id=((Number)r.get("id")).longValue();return new Shipment(id,(String)r.get("order_id"),(String)r.get("carrier"),(String)r.get("tracking_reference"),(String)r.get("tracking_url"),time(r.get("created_at")),events(tenant,id));}
 @PreAuthorize("hasAuthority('SHIPMENT_VIEW')") public List<EligibleOrder> eligibleOrders(){var c=vendor("SHIPMENT_VIEW");return db.queryForList("SELECT o.id order_id,o.created_at,o.total FROM orders o LEFT JOIN shipments s ON s.tenant_id=o.tenant_id AND s.order_id=o.id WHERE o.tenant_id=? AND o.sales_channel='ONLINE' AND o.status='PAID' AND o.customer_id IS NOT NULL AND s.id IS NULL ORDER BY o.created_at DESC,o.id DESC LIMIT 100",c.tenantId()).stream().map(r->new EligibleOrder((String)r.get("order_id"),time(r.get("created_at")),(BigDecimal)r.get("total"))).toList();}
 @PreAuthorize("hasAuthority('SHIPMENT_VIEW')") public List<Shipment> shipments(){var c=vendor("SHIPMENT_VIEW");return db.queryForList("SELECT id,order_id,carrier,tracking_reference,tracking_url,created_at FROM shipments WHERE tenant_id=? ORDER BY created_at DESC,id DESC LIMIT 100",c.tenantId()).stream().map(r->view(c.tenantId(),r)).toList();}
 @Transactional @PreAuthorize("hasAuthority('SHIPMENT_UPDATE')") public Shipment createShipment(String orderId,String carrier0,String reference0,String url0,String reason0){
  var c=vendor("SHIPMENT_UPDATE");String carrier=clean(carrier0,120,"Carrier"),reference=clean(reference0,160,"Tracking reference"),trackingUrl=url(url0),reason=clean(reason0,500,"Reason");
  if(orderId==null||!orderId.matches("[A-Za-z0-9-]{1,36}"))throw missing();
  var rows=db.queryForList("SELECT customer_id,sales_channel,status FROM orders WHERE tenant_id=? AND id=? FOR UPDATE",c.tenantId(),orderId);if(rows.isEmpty())throw missing();var o=rows.getFirst();
  if(!"ONLINE".equals(o.get("sales_channel"))||!"PAID".equals(o.get("status"))||o.get("customer_id")==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Shipment requires a paid online customer order");
  if(!db.queryForList("SELECT id FROM shipments WHERE tenant_id=? AND order_id=?",c.tenantId(),orderId).isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"Shipment already exists for this order");
  db.update("INSERT INTO shipments(tenant_id,order_id,carrier,tracking_reference,tracking_url,created_by) VALUES(?,?,?,?,?,?)",c.tenantId(),orderId,carrier,reference,trackingUrl,c.username());
  long id=db.queryForObject("SELECT id FROM shipments WHERE tenant_id=? AND order_id=?",Long.class,c.tenantId(),orderId);
  db.update("INSERT INTO vendor_access_audit_events(tenant_id,actor_username,resource_type,resource_id,action,reason) VALUES(?,?,'SHIPMENT',?,'SHIPMENT_CREATED',?)",c.tenantId(),c.username(),Long.toString(id),reason);
  return view(c.tenantId(),db.queryForList("SELECT id,order_id,carrier,tracking_reference,tracking_url,created_at FROM shipments WHERE tenant_id=? AND id=?",c.tenantId(),id).getFirst());
 }
 @Transactional @PreAuthorize("hasAuthority('SHIPMENT_UPDATE')") public Event addEvent(long id,String code0,String description0,String reason0){
  var c=vendor("SHIPMENT_UPDATE");String code=clean(code0,40,"Event code").toUpperCase(Locale.ROOT);if(!code.matches("[A-Z][A-Z0-9_]{1,39}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid event code");String description=clean(description0,500,"Description"),reason=clean(reason0,500,"Reason");
  if(db.queryForList("SELECT id FROM shipments WHERE tenant_id=? AND id=? FOR UPDATE",c.tenantId(),id).isEmpty())throw missing();
  db.update("INSERT INTO shipment_events(tenant_id,shipment_id,event_code,description,reason,actor_username) VALUES(?,?,?,?,?,?)",c.tenantId(),id,code,description,reason,c.username());
  long eventId=db.queryForObject("SELECT id FROM shipment_events WHERE tenant_id=? AND shipment_id=? ORDER BY id DESC LIMIT 1",Long.class,c.tenantId(),id);
  db.update("INSERT INTO vendor_access_audit_events(tenant_id,actor_username,resource_type,resource_id,action,reason) VALUES(?,?,'SHIPMENT',?,'SHIPMENT_EVENT_ADDED',?)",c.tenantId(),c.username(),Long.toString(id),reason);
  var r=db.queryForList("SELECT id,event_code,description,reason,actor_username,occurred_at FROM shipment_events WHERE tenant_id=? AND shipment_id=? AND id=?",c.tenantId(),id,eventId).getFirst();
  return new Event(eventId,code,description,reason,c.username(),time(r.get("occurred_at")));
 }
 @PreAuthorize("hasAuthority('CUSTOMER')") public List<CustomerShipment> customerShipments(String username,String orderId){
  if(orderId==null||!orderId.matches("[A-Za-z0-9-]{1,36}"))throw missing();
  long tenant=orderHistory.customerOrderTenant(username,orderId);
  return db.queryForList("SELECT id,carrier,tracking_reference,tracking_url,created_at FROM shipments WHERE tenant_id=? AND order_id=? ORDER BY id",tenant,orderId).stream().map(r->{long id=((Number)r.get("id")).longValue();var es=db.queryForList("SELECT id,event_code,description,occurred_at FROM shipment_events WHERE tenant_id=? AND shipment_id=? ORDER BY occurred_at,id",tenant,id).stream().map(e->new CustomerEvent(((Number)e.get("id")).longValue(),(String)e.get("event_code"),(String)e.get("description"),time(e.get("occurred_at")))).toList();return new CustomerShipment(id,(String)r.get("carrier"),(String)r.get("tracking_reference"),(String)r.get("tracking_url"),time(r.get("created_at")),es);}).toList();
 }
}
