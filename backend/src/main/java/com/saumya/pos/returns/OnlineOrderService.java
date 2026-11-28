package com.saumya.pos.returns;

import com.saumya.pos.checkout.OrderHistoryService;
import com.saumya.pos.identity.CustomerAccess;
import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.shared.EventOutbox;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import org.springframework.http.HttpStatus;

@Service
@Transactional
class OnlineOrderService {
 private final JdbcTemplate db; private final AuthorizationContext auth; private final OrderHistoryService orders;
 private final CustomerAccess customers; private final EventOutbox outbox; private final Clock clock;
 OnlineOrderService(JdbcTemplate db,AuthorizationContext auth,OrderHistoryService orders,CustomerAccess customers,EventOutbox outbox,Clock clock){
  this.db=db;this.auth=auth;this.orders=orders;this.customers=customers;this.outbox=outbox;this.clock=clock;
 }
 record RequestItem(long productId,int quantity){}
 record Create(String orderId,List<RequestItem> items,String reason){}
 record Decision(long expectedVersion,String decision,String reason,String customerMessage){}
 record PolicyChange(long expectedVersion,int returnWindowDays,String reason){}
 record Policy(int returnWindowDays,long version){}
 record Item(long productId,String name,int quantity){}
 record Event(long id,String eventType,String actorType,String message,Instant occurredAt){}
 record EligibleItem(long productId,String name,int purchasedQuantity,int requestedQuantity,int availableQuantity){}
 record EligibleOrder(String orderId,String sellerName,Instant deliveredAt,int returnWindowDays,Instant eligibleUntil,boolean eligible,String message,List<EligibleItem> items){}
 record ReturnView(String id,String orderId,String status,String reason,String customerMessage,String reviewReason,Instant createdAt,Instant reviewedAt,long version,List<Item> items,List<Event> events){}
 record Workspace(List<EligibleOrder> eligibleOrders,List<ReturnView> returns){}

 private ResponseStatusException fail(HttpStatus status,String message){return new ResponseStatusException(status,message);}
 private void bad(String message){throw fail(HttpStatus.BAD_REQUEST,message);}
 private void conflict(String message){throw fail(HttpStatus.CONFLICT,message);}
 private void notFound(){throw fail(HttpStatus.NOT_FOUND,"Order or return unavailable");}
 private AuthorizationContext.Context customer(String username){
  var actor=auth.current();
  if(!actor.isCustomer()||actor.isVendorEmployee()||actor.isPlatformUser()||!Objects.equals(actor.username(),username))
   throw fail(HttpStatus.FORBIDDEN,"Authenticated customer identity required");
  return actor;
 }
 private AuthorizationContext.Context vendor(String permission){var actor=auth.current();actor.requireVendorMembership();actor.requirePermission(permission);return actor;}
 private String clean(String value,int max,String field){
  String v=value==null?"":value.strip();
  if(v.isEmpty()||v.length()>max||v.codePoints().anyMatch(Character::isISOControl))bad(field+" is invalid");
  return v;
 }
 private Instant time(Object value){
  if(value instanceof Timestamp t)return t.toInstant();
  if(value instanceof Instant i)return i;
  if(value instanceof LocalDateTime d)return d.toInstant(ZoneOffset.UTC);
  if(value instanceof java.util.Date d)return d.toInstant();
  throw new IllegalStateException("Unsupported timestamp value");
 }
 private String hash(List<String> values){
  try{var d=MessageDigest.getInstance("SHA-256");for(String s:values){byte[] b=s.getBytes(StandardCharsets.UTF_8);d.update(ByteBuffer.allocate(4).putInt(b.length).array());d.update(b);}return HexFormat.of().formatHex(d.digest());}
  catch(Exception e){throw new IllegalStateException(e);}
 }
 private int days(long tenant,boolean lock){
  var rows=lock
   ?db.queryForList("SELECT return_window_days FROM online_return_policies WHERE tenant_id=? FOR UPDATE",tenant)
   :db.queryForList("SELECT return_window_days FROM online_return_policies WHERE tenant_id=?",tenant);
  return rows.isEmpty()?0:((Number)rows.getFirst().get("return_window_days")).intValue();
 }
 private Instant delivered(long tenant,String order){
  var rows=db.queryForList("""
   SELECT MAX(e.occurred_at) delivered_at FROM shipments s
   JOIN shipment_events e ON e.tenant_id=s.tenant_id AND e.shipment_id=s.id
   WHERE s.tenant_id=? AND s.order_id=? AND UPPER(e.event_code)='DELIVERED'
   """,tenant,order);
  Object value=rows.isEmpty()?null:rows.getFirst().get("delivered_at");
  return value==null?null:time(value);
 }
 private List<EligibleItem> eligibleItems(long tenant,String order){
  return db.queryForList("""
   SELECT oi.product_id,oi.name,oi.quantity,
    COALESCE(SUM(CASE WHEN r.status='REJECTED' THEN 0 ELSE ri.quantity END),0) requested
   FROM order_items oi
   LEFT JOIN online_return_items ri ON ri.tenant_id=oi.tenant_id AND ri.order_id=oi.order_id AND ri.product_id=oi.product_id
   LEFT JOIN online_returns r ON r.tenant_id=ri.tenant_id AND r.id=ri.return_id
   WHERE oi.tenant_id=? AND oi.order_id=?
   GROUP BY oi.product_id,oi.name,oi.quantity ORDER BY oi.product_id
   """,tenant,order).stream().map(row->{
    int bought=((Number)row.get("quantity")).intValue(),requested=((Number)row.get("requested")).intValue();
    return new EligibleItem(((Number)row.get("product_id")).longValue(),(String)row.get("name"),bought,requested,Math.max(0,bought-requested));
   }).toList();
 }
 private EligibleOrder eligible(OrderHistoryService.CustomerOrderReference order){
  int window=days(order.tenantId(),false);Instant delivered=delivered(order.tenantId(),order.orderId());
  Instant until=delivered==null||window==0?null:delivered.plus(Duration.ofDays(window));
  var lines=eligibleItems(order.tenantId(),order.orderId());
  boolean available=lines.stream().anyMatch(i->i.availableQuantity()>0);
  boolean within=until!=null&&!clock.instant().isAfter(until);
  String message=window==0?"Online returns are disabled by this vendor.":delivered==null?"A delivered shipment event is required.":!within?"The return window has expired.":!available?"No purchased quantity remains available.":"This delivered order is within the vendor return window.";
  return new EligibleOrder(order.orderId(),order.vendorName(),delivered,window,until,within&&available,message,lines);
 }
 private List<Item> items(long tenant,String id){
  return db.queryForList("SELECT product_id,product_name,quantity FROM online_return_items WHERE tenant_id=? AND return_id=? ORDER BY product_id",tenant,id)
   .stream().map(r->new Item(((Number)r.get("product_id")).longValue(),(String)r.get("product_name"),((Number)r.get("quantity")).intValue())).toList();
 }
 private List<Event> events(long tenant,String id){
  return db.queryForList("SELECT id,event_type,actor_type,customer_message,occurred_at FROM online_return_events WHERE tenant_id=? AND return_id=? ORDER BY occurred_at,id",tenant,id)
   .stream().map(r->new Event(((Number)r.get("id")).longValue(),(String)r.get("event_type"),(String)r.get("actor_type"),(String)r.get("customer_message"),time(r.get("occurred_at")))).toList();
 }
 private ReturnView customerView(long tenant,Map<String,Object> r){
  String id=(String)r.get("id");Object reviewed=r.get("reviewed_at");
  return new ReturnView(id,(String)r.get("order_id"),(String)r.get("status"),(String)r.get("reason"),
   (String)r.get("customer_message"),null,time(r.get("created_at")),reviewed==null?null:time(reviewed),
   ((Number)r.get("version")).longValue(),items(tenant,id),events(tenant,id));
 }
 private ReturnView vendorView(long tenant,Map<String,Object> r){
  String id=(String)r.get("id");Object reviewed=r.get("reviewed_at");
  return new ReturnView(id,(String)r.get("order_id"),(String)r.get("status"),(String)r.get("reason"),
   (String)r.get("customer_message"),(String)r.get("review_reason"),time(r.get("created_at")),reviewed==null?null:time(reviewed),
   ((Number)r.get("version")).longValue(),items(tenant,id),events(tenant,id));
 }
 private List<Map<String,Object>> caseRows(long tenant,long customer){
  return db.queryForList("""
   SELECT id,order_id,status,reason,customer_message,created_at,reviewed_at,version
   FROM online_returns WHERE tenant_id=? AND customer_id=? ORDER BY created_at DESC,id DESC LIMIT 100
   """,tenant,customer);
 }
 private void appendEvent(long tenant,String id,String actorType,String actor,String event,String message,Instant now){
  db.update("""
   INSERT INTO online_return_events(tenant_id,return_id,actor_type,actor_id,event_type,customer_message,occurred_at)
   VALUES(?,?,?,?,?,?,?)
   """,tenant,id,actorType,actor,event,message,Timestamp.from(now));
 }

 @PreAuthorize("hasAuthority('CUSTOMER')")
 Workspace customerWorkspace(String username){
  customer(username);long customerId=customers.resolve(username);
  var refs=orders.customerPaidOnlineOrderReferences(username);
  var eligible=refs.stream().map(this::eligible).filter(o->o.deliveredAt()!=null).toList();
  var results=new ArrayList<ReturnView>();var seen=new HashSet<Long>();
  for(var ref:refs)if(seen.add(ref.tenantId()))for(var row:caseRows(ref.tenantId(),customerId))results.add(customerView(ref.tenantId(),row));
  results.sort(Comparator.comparing(ReturnView::createdAt).reversed());
  return new Workspace(eligible,results.stream().limit(100).toList());
 }

 @PreAuthorize("hasAuthority('CUSTOMER')")
 ReturnView createCustomer(String username,String clientKey,Create request){
  customer(username);
  if(clientKey==null||!clientKey.matches("[A-Za-z0-9_-]{8,80}"))bad("Valid Idempotency-Key required");
  if(request==null)bad("Return request required");
  String orderId=request.orderId();
  if(orderId==null||!orderId.matches("[A-Za-z0-9-]{1,36}"))notFound();
  String reason=clean(request.reason(),250,"Return reason");
  if(request.items()==null||request.items().isEmpty()||request.items().size()>100)bad("Choose purchased items");
  var chosen=request.items().stream().sorted(Comparator.comparingLong(RequestItem::productId)).toList();
  var seen=new HashSet<Long>();var fingerprint=new ArrayList<String>(List.of(orderId,reason));
  for(var item:chosen){
   if(item.productId()<1||item.quantity()<1||item.quantity()>10000||!seen.add(item.productId()))bad("Invalid or duplicate item quantity");
   fingerprint.add(Long.toString(item.productId()));fingerprint.add(Integer.toString(item.quantity()));
  }
  long tenant=orders.customerOrderTenant(username,orderId),customerId=customers.resolve(username);
  String key=hash(List.of(Long.toString(customerId),clientKey)),requestHash=hash(fingerprint);
  var orderRows=db.queryForList("SELECT customer_id,sales_channel,status FROM orders WHERE tenant_id=? AND id=? AND customer_id=? FOR UPDATE",tenant,orderId,customerId);
  if(orderRows.isEmpty())notFound();
  var order=orderRows.getFirst();
  if(!"ONLINE".equals(order.get("sales_channel"))||!"PAID".equals(order.get("status")))notFound();
  var prior=db.queryForList("SELECT id,request_hash FROM online_returns WHERE tenant_id=? AND customer_id=? AND idempotency_key=?",tenant,customerId,key);
  if(!prior.isEmpty()){
   if(!requestHash.equals(prior.getFirst().get("request_hash")))conflict("Idempotency key reused with a different request");
   var existing=db.queryForList("SELECT id,order_id,status,reason,customer_message,created_at,reviewed_at,version FROM online_returns WHERE tenant_id=? AND customer_id=? AND id=?",tenant,customerId,prior.getFirst().get("id"));
   return customerView(tenant,existing.getFirst());
  }
  int window=days(tenant,true);Instant delivered=delivered(tenant,orderId);
  if(window<=0||delivered==null||clock.instant().isAfter(delivered.plus(Duration.ofDays(window))))conflict("Order is outside the vendor's active return window");
  var selected=new HashMap<Long,Map<String,Object>>();
  for(var item:chosen){
   var line=db.queryForList("SELECT product_id,name,quantity FROM order_items WHERE tenant_id=? AND order_id=? AND product_id=?",tenant,orderId,item.productId());
   if(line.isEmpty())notFound();selected.put(item.productId(),line.getFirst());
   int bought=((Number)line.getFirst().get("quantity")).intValue();
   Integer used=db.queryForObject("""
    SELECT COALESCE(SUM(ri.quantity),0) FROM online_return_items ri
    JOIN online_returns r ON r.tenant_id=ri.tenant_id AND r.id=ri.return_id
    WHERE ri.tenant_id=? AND ri.order_id=? AND ri.product_id=? AND r.status<>'REJECTED'
    """,Integer.class,tenant,orderId,item.productId());
   if(item.quantity()>bought-(used==null?0:used))conflict("Requested quantity exceeds the remaining quantity");
  }
  String id=UUID.randomUUID().toString();Instant now=clock.instant();
  db.update("""
   INSERT INTO online_returns(tenant_id,id,customer_id,order_id,idempotency_key,request_hash,reason,status,created_at,version)
   VALUES(?,?,?,?,?,?,?,'REQUESTED',?,0)
   """,tenant,id,customerId,orderId,key,requestHash,reason,Timestamp.from(now));
  for(var item:chosen)db.update("""
   INSERT INTO online_return_items(tenant_id,return_id,order_id,product_id,product_name,quantity) VALUES(?,?,?,?,?,?)
   """,tenant,id,orderId,item.productId(),selected.get(item.productId()).get("name"),item.quantity());
  String message="Your online return request has been submitted for vendor review.";
  appendEvent(tenant,id,"CUSTOMER",username,"REQUESTED",message,now);
  outbox.append(tenant,username,"ONLINE_RETURN",id,"ONLINE_RETURN_REQUESTED",clientKey,Map.of("orderId",orderId,"status","REQUESTED"));
  var row=db.queryForList("SELECT id,order_id,status,reason,customer_message,created_at,reviewed_at,version FROM online_returns WHERE tenant_id=? AND customer_id=? AND id=?",tenant,customerId,id).getFirst();
  return customerView(tenant,row);
 }

 @PreAuthorize("hasAuthority('RETURN_VIEW')")
 List<ReturnView> vendorList(){
  var actor=vendor("RETURN_VIEW");
  return db.queryForList("SELECT id,order_id,status,reason,review_reason,customer_message,created_at,reviewed_at,version FROM online_returns WHERE tenant_id=? ORDER BY CASE status WHEN 'REQUESTED' THEN 0 ELSE 1 END,created_at,id LIMIT 100",actor.tenantId())
   .stream().map(r->vendorView(actor.tenantId(),r)).toList();
 }
 @PreAuthorize("hasAuthority('RETURN_APPROVE')")
 ReturnView decide(String id,Decision request){
  var actor=vendor("RETURN_APPROVE");
  if(request==null)bad("Decision required");
  String decision=request.decision()==null?"":request.decision().toUpperCase(Locale.ROOT);
  if(!Set.of("APPROVED","REJECTED").contains(decision))bad("Decision must be APPROVED or REJECTED");
  String reason=clean(request.reason(),500,"Review reason"),message=clean(request.customerMessage(),500,"Customer message");
  var rows=db.queryForList("SELECT id,order_id,status,reason,review_reason,customer_message,created_at,reviewed_at,version FROM online_returns WHERE tenant_id=? AND id=? FOR UPDATE",actor.tenantId(),id);
  if(rows.isEmpty())notFound();var row=rows.getFirst();long version=((Number)row.get("version")).longValue();
  if(version!=request.expectedVersion()||!"REQUESTED".equals(row.get("status")))conflict("Return changed or was already reviewed");
  Instant now=clock.instant();
  if(db.update("UPDATE online_returns SET status=?,reviewed_by=?,review_reason=?,customer_message=?,reviewed_at=?,version=version+1 WHERE tenant_id=? AND id=? AND version=? AND status='REQUESTED'",decision,actor.username(),reason,message,Timestamp.from(now),actor.tenantId(),id,version)!=1)conflict("Return changed; reload");
  appendEvent(actor.tenantId(),id,"VENDOR",actor.username(),decision,message,now);
  outbox.append(actor.tenantId(),actor.username(),"ONLINE_RETURN",id,"ONLINE_RETURN_"+decision,id,Map.of("status",decision,"customerMessage",message));
  var updated=db.queryForList("SELECT id,order_id,status,reason,review_reason,customer_message,created_at,reviewed_at,version FROM online_returns WHERE tenant_id=? AND id=?",actor.tenantId(),id).getFirst();
  return vendorView(actor.tenantId(),updated);
 }
 @PreAuthorize("hasAuthority('RETURN_VIEW')")
 Policy policy(){var actor=vendor("RETURN_VIEW");var rows=db.queryForList("SELECT return_window_days,version FROM online_return_policies WHERE tenant_id=?",actor.tenantId());return rows.isEmpty()?new Policy(0,0):new Policy(((Number)rows.getFirst().get("return_window_days")).intValue(),((Number)rows.getFirst().get("version")).longValue());}
 @PreAuthorize("hasAuthority('RETURN_APPROVE')")
 Policy updatePolicy(PolicyChange request){
  var actor=vendor("RETURN_APPROVE");if(request==null||request.returnWindowDays()<0||request.returnWindowDays()>365)bad("Return window must be 0..365 days; zero disables returns");
  String reason=clean(request.reason(),500,"Policy reason");
  db.queryForObject("SELECT id FROM tenants WHERE id=? FOR UPDATE",Long.class,actor.tenantId());
  var rows=db.queryForList("SELECT return_window_days,version FROM online_return_policies WHERE tenant_id=? FOR UPDATE",actor.tenantId());
  int before=0;long version=0;if(!rows.isEmpty()){before=((Number)rows.getFirst().get("return_window_days")).intValue();version=((Number)rows.getFirst().get("version")).longValue();}
  if(version!=request.expectedVersion())conflict("Policy changed; reload");
  long next=version+1;Instant now=clock.instant();
  if(rows.isEmpty())db.update("INSERT INTO online_return_policies(tenant_id,return_window_days,version,updated_by,updated_at) VALUES(?,?,?,?,?)",actor.tenantId(),request.returnWindowDays(),next,actor.username(),Timestamp.from(now));
  else db.update("UPDATE online_return_policies SET return_window_days=?,version=?,updated_by=?,updated_at=? WHERE tenant_id=? AND version=?",request.returnWindowDays(),next,actor.username(),Timestamp.from(now),actor.tenantId(),version);
  db.update("INSERT INTO online_return_policy_events(tenant_id,actor_username,before_days,after_days,reason,occurred_at) VALUES(?,?,?,?,?,?)",actor.tenantId(),actor.username(),before,request.returnWindowDays(),reason,Timestamp.from(now));
  outbox.append(actor.tenantId(),actor.username(),"ONLINE_RETURN_POLICY",Long.toString(actor.tenantId()),"ONLINE_RETURN_POLICY_UPDATED",UUID.randomUUID().toString(),Map.of("beforeDays",before,"afterDays",request.returnWindowDays(),"reason",reason));
  return new Policy(request.returnWindowDays(),next);
 }
}

