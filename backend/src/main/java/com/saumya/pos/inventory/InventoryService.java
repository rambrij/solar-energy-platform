package com.saumya.pos.inventory;

import com.saumya.pos.inventory.internal.*;
import com.saumya.pos.identity.CustomerAccess;
import com.saumya.pos.security.AuthorizationContext;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class InventoryService {
 private static final String EXPIRY_JOB_AUTHORITY="SYSTEM_DRAFT_EXPIRY";
 private final InventoryIssueRepository issues;
 private final InventoryRepository balances;
 private final ReservationRepository reservations;
 private final ReturnDispositionRepository returns;
 private final ApplicationEventPublisher events;
 private final AuthorizationContext authorization;
 private final CustomerAccess customers;
 private final JdbcTemplate jdbc;

 @Autowired
 public InventoryService(InventoryRepository balances,ReservationRepository reservations,
     ReturnDispositionRepository returns,InventoryIssueRepository issues,ApplicationEventPublisher events,
     AuthorizationContext authorization,CustomerAccess customers,JdbcTemplate jdbc){
  this.balances=balances;this.reservations=reservations;this.returns=returns;this.issues=issues;this.events=events;
  this.authorization=authorization;this.customers=customers;this.jdbc=jdbc;
 }

 /** Compatibility constructor for isolated tests; mutation methods fail closed without an authorization context. */
 public InventoryService(InventoryRepository balances,ReservationRepository reservations,
     ReturnDispositionRepository returns,InventoryIssueRepository issues,ApplicationEventPublisher events){
  this(balances,reservations,returns,issues,events,null,null,null);
 }

 private InventoryBalance locked(long tenant,long branch,long product){
  return balances.lock(tenant,branch,product).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Stock location unavailable"));
 }
 private void changed(long tenant,long branch,long product,InventoryBalance balance){
  balances.flush();events.publishEvent(new StockAvailabilityChanged(tenant,branch,product,balance.available()));
 }
 private AuthorizationContext.Context actor(){
  if(authorization==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Inventory authorization context unavailable");
  return authorization.current();
 }
 private Map<String,Object> order(long tenant,String id){
  if(jdbc==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Inventory resource authorization unavailable");
  return jdbc.queryForList("""
      SELECT tenant_id,branch_id,COALESCE(stock_branch_id,branch_id) AS stock_branch_id,
             cashier,customer_id,channel,status
      FROM orders WHERE tenant_id=? AND id=?
      """,tenant,id).stream().findFirst()
      .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Stock reservation unavailable"));
 }
 private boolean expiryJob(){
  Authentication auth=SecurityContextHolder.getContext().getAuthentication();
  return auth!=null&&auth.isAuthenticated()&&auth.getAuthorities().stream()
      .anyMatch(a->EXPIRY_JOB_AUTHORITY.equals(a.getAuthority()));
 }
 /**
  * Checks the persisted order owner before reservation mutation. Online stock is changed only
  * by the authenticated customer who owns the online order; POS stock is changed only by the
  * authenticated cashier in the assigned tenant/branch with the operation-specific grant.
  */
 private Map<String,Object> authorizeOrder(long tenant,String id,long branch,String permission){
  var row=order(tenant,id);
  if(!Objects.equals(((Number)row.get("stock_branch_id")).longValue(),branch))
   throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Stock reservation unavailable");
  String channel=String.valueOf(row.get("channel"));
  var context=actor();
  if("ONLINE".equals(channel)){
   if(!context.isCustomer()||context.isPlatformUser())
    throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Stock reservation unavailable");
   long customer=customers.resolve(context.username());
   Object owner=row.get("customer_id");
   if(owner==null||((Number)owner).longValue()!=customer)
    throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Stock reservation unavailable");
   return row;
  }
  if(!"IN_STORE".equals(channel))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Stock reservation unavailable");
  context.requireVendorMembership();
  context.requireTenant(tenant);
  context.requireBranch(branch);
  if(!context.username().equals(row.get("cashier")))
   throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Stock reservation unavailable");
  context.requirePermission(permission);
  return row;
 }

 /** Safe catalogue projection: only a boolean availability value is exposed to public catalogue reads. */
 @Transactional(readOnly=true)
 public boolean publicInStock(long tenant,long branch,long product){return balances.atBranch(tenant,branch,product)>0;}

 /** Internal exact-quantity query. Callers must already hold a vendor scope or be a stock projection. */
 @Transactional(readOnly=true)
 public boolean publicInStock(long tenant,long branch,long product){return balances.atBranch(tenant,branch,product)>0;}

 /** Exact tenant-wide quantity is available only to an authenticated tenant member. */
 @Transactional(readOnly=true)
 public long available(long tenant,long product){
  var context=actor();context.requireVendorMembership();context.requireTenant(tenant);context.requirePermission("INVENTORY_VIEW");
  return balances.available(tenant,product);
 }

 /** Exact branch quantity is available only to an authenticated member assigned to that branch. */
 @Transactional(readOnly=true)
 public long atBranch(long tenant,long branch,long product){
  var context=actor();context.requireVendorMembership();context.requireTenant(tenant);context.requireBranch(branch);context.requirePermission("INVENTORY_VIEW");
  return balances.atBranch(tenant,branch,product);
 }

 @Transactional
 public void issue(long tenant,long branch,long product,int quantity){
  var context=actor();context.requireVendorMembership();context.requireTenant(tenant);context.requireBranch(branch);
  context.requirePermission("INVENTORY_ADJUST");
  var balance=locked(tenant,branch,product);balance.issue(quantity);changed(tenant,branch,product,balance);
 }

 @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
 public int receive(long tenant,long branch,long product,int quantity){
  var context=actor();context.requireVendorMembership();context.requireTenant(tenant);context.requireBranch(branch);
  context.requirePermission("INVENTORY_RECEIVE");
  var balance=balances.lock(tenant,branch,product).orElseGet(()->balances.saveAndFlush(new InventoryBalance(tenant,branch,product)));
  balance.restock(quantity);changed(tenant,branch,product,balance);return balance.available();
 }

 @Transactional
 public void reserve(long tenant,long branch,long product,int quantity,String orderId,Instant expires){
  var order=authorizeOrder(tenant,orderId,branch,"POS_SALE_CREATE");
  if(!"DRAFT".equals(order.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only draft orders may reserve stock");
  var balance=locked(tenant,branch,product);balance.reserve(quantity);
  reservations.save(new StockReservation(tenant,orderId,product,branch,quantity,expires));
  changed(tenant,branch,product,balance);
 }

 /** Releases only an owned online/POS draft. System expiry is separately authorized by the scheduled expiry worker. */
 @Transactional
 public void release(long tenant,String orderId){
  Map<String,Object> order=order(tenant,orderId);
  if(expiryJob()){
   if(!"DRAFT".equals(order.get("status")))return;
  }else{
   long branch=((Number)order.get("stock_branch_id")).longValue();
   var context=actor();
   if(!context.isCustomer()){
    context.requireVendorMembership();context.requireTenant(tenant);context.requireBranch(branch);
    if(context.username().equals(row.get("cashier")))context.requirePermission("POS_SALE_CANCEL");
    else if(!context.hasAnyPermission("POS_TENDER_MANUAL_VERIFY","POS_TENDER_EXTERNAL_RECORD"))
     throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Payment resolution permission required");
   }else order=authorizeOrder(tenant,orderId,branch,"POS_SALE_CANCEL");
   if(!"DRAFT".equals(order.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only unpaid draft orders may release stock");
  }
  for(var reservation:reservations.held(tenant,orderId)){
   var balance=locked(tenant,reservation.branch(),reservation.product());
   balance.release(reservation.quantity());reservation.release();changed(tenant,reservation.branch(),reservation.product(),balance);
  }
 }

 @Transactional
 public Map<Long,Integer> consume(long tenant,String orderId){
  Map<String,Object> row=order(tenant,orderId);
  long branch=((Number)row.get("stock_branch_id")).longValue();
  var context=actor();
  if("ONLINE".equals(row.get("sales_channel"))){
   if(!context.isCustomer()||context.isPlatformUser())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Stock reservation unavailable");
   long customer=customers.resolve(context.username());Object owner=row.get("customer_id");
   if(owner==null||((Number)owner).longValue()!=customer)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Stock reservation unavailable");
  }else{
   context.requireVendorMembership();context.requireTenant(tenant);context.requireBranch(branch);
   if(!context.username().equals(row.get("cashier"))&&!context.hasAnyPermission("POS_TENDER_MANUAL_VERIFY","POS_TENDER_EXTERNAL_CONFIRM"))
    throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Stock reservation unavailable");
   if(context.username().equals(row.get("cashier")))context.requirePermission("POS_SALE_CREATE");
   else if(!context.hasAnyPermission("POS_TENDER_MANUAL_VERIFY","POS_TENDER_EXTERNAL_CONFIRM"))
    throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Payment confirmation permission required");
  }
  if(!Set.of("DRAFT","PAID").contains(String.valueOf(row.get("status"))))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Only payable draft orders may consume reserved stock");
  var consumed=new LinkedHashMap<Long,Integer>();
  for(var reservation:reservations.held(tenant,orderId)){
   var balance=locked(tenant,reservation.branch(),reservation.product());
   balance.consume(reservation.quantity());reservation.consume();
   issues.saveAndFlush(new InventoryIssue(tenant,orderId,reservation.product(),reservation.branch(),reservation.quantity()));
   consumed.put(reservation.product(),reservation.quantity());changed(tenant,reservation.branch(),reservation.product(),balance);
  }
  return consumed;
 }

 @Transactional
 public void inspectReturn(long tenant,long branch,String id,long product,int quantity,
     String disposition,String condition,String actorName,Instant time){
  var context=actor();context.requireVendorMembership();context.requireTenant(tenant);context.requireBranch(branch);
  if(!context.username().equals(actorName))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Inventory actor must match authenticated identity");
  context.requirePermission("POS_RETURN_INSPECT");
  if(quantity<1||!Set.of("RESTOCK","QUARANTINE").contains(disposition)
      ||!Set.of("SELLABLE","DAMAGED","INCOMPLETE").contains(condition)
      ||("RESTOCK".equals(disposition)&&!"SELLABLE".equals(condition)))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Invalid return disposition");
  if(jdbc==null||!Boolean.TRUE.equals(jdbc.queryForObject(
      "SELECT COUNT(*)>0 FROM pos_returns WHERE tenant_id=? AND branch_id=? AND id=? AND state='APPROVED'",
      Boolean.class,tenant,branch,id)))
   throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Approved return unavailable");
  var key=new ReturnDisposition.Key(tenant,id,product);
  if(returns.existsById(key))throw new ResponseStatusException(HttpStatus.CONFLICT,"Return stock already recorded");
  var balance=locked(tenant,branch,product);
  if("RESTOCK".equals(disposition))balance.restock(quantity);
  returns.save(new ReturnDisposition(tenant,branch,id,product,quantity,disposition,condition,actorName,time));
  changed(tenant,branch,product,balance);
 }
}
