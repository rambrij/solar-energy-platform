package com.saumya.pos.inventory;

import com.saumya.pos.inventory.internal.*;
import com.saumya.pos.identity.CustomerAccess;
import com.saumya.pos.security.AuthorizationContext;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
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
 private final InventoryOrderRepository orderRecords;
 private final InventoryApprovedReturnRepository approvedReturns;

 @Autowired
 public InventoryService(InventoryRepository balances,ReservationRepository reservations,
     ReturnDispositionRepository returns,InventoryIssueRepository issues,ApplicationEventPublisher events,
     AuthorizationContext authorization,CustomerAccess customers,InventoryOrderRepository orderRecords,
     InventoryApprovedReturnRepository approvedReturns){
  this.balances=balances;this.reservations=reservations;this.returns=returns;this.issues=issues;this.events=events;
  this.authorization=authorization;this.customers=customers;this.orderRecords=orderRecords;this.approvedReturns=approvedReturns;
 }

 public InventoryService(InventoryRepository balances,ReservationRepository reservations,
     ReturnDispositionRepository returns,InventoryIssueRepository issues,ApplicationEventPublisher events){
  this(balances,reservations,returns,issues,events,null,null,null,null,null);
 }

 /** Resolves the sole server-configured POS stock location. No caller-supplied location id is accepted. */
 private long designatedLocation(long tenant,long branch){
  var rows=balances.activePosStockLocations(tenant,branch);
  if(rows.size()!=1)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Branch has no active designated stock location");
  return rows.getFirst();
 }
 private void requireAssignedRegister(AuthorizationContext.Context context,long tenant,long branch){
  if(balances.activeBranchRegisterAssignmentCount(tenant,branch,context.username())==0)
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Cashier has no active register assignment in this branch");
 }
 private long requireOwnerOpenSession(long tenant,long branch,String owner,String session,String actorName){
  if(session==null||session.isBlank()||"null".equals(session))
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"POS resource has no authoritative session");
  var active=balances.lockOwnerOpenSessionRegisterIds(tenant,branch,owner,session);
  if(active.size()!=1)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"POS session is not OPEN or its cashier/register assignment was revoked");
  long register=active.getFirst();
  if(balances.activeRegisterAssignmentCount(tenant,branch,actorName,register)==0)
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Actor is not assigned to the cashier's register");
  return register;
 }
 private void requireOpenOrderSession(AuthorizationContext.Context actor,Map<String,Object> order,String ownerPermission){
  long tenant=((Number)order.get("tenant_id")).longValue();
  long branch=((Number)order.get("branch_id")).longValue();
  String owner=String.valueOf(order.get("cashier"));
  actor.requireTenant(tenant);actor.requireBranch(branch);
  if(actor.username().equals(owner))actor.requirePermission(ownerPermission);
  else actor.requirePermission("POS_TENDER_MANUAL_VERIFY");
  requireOwnerOpenSession(tenant,branch,owner,String.valueOf(order.get("session_id")),actor.username());
 }
 private void requireCurrentOpenSession(AuthorizationContext.Context actor,long tenant,long branch){
  requireCurrentOpenSession(actor,tenant,branch,false);
 }
 private void lockCurrentOpenSession(AuthorizationContext.Context actor,long tenant,long branch){
  requireCurrentOpenSession(actor,tenant,branch,true);
 }
 private void requireCurrentOpenSession(AuthorizationContext.Context actor,long tenant,long branch,boolean lock){
  actor.requireTenant(tenant);actor.requireBranch(branch);
  var active=lock?balances.lockAssignedOpenSessions(tenant,branch,actor.username())
      :balances.assignedOpenSessions(tenant,branch,actor.username());
  if(active.size()!=1)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"An assigned OPEN register session is required");
 }
 private InventoryBalance locked(long tenant,long branch,long product){
  designatedLocation(tenant,branch);
  return balances.lock(tenant,branch,product).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Stock location unavailable"));
 }
 private long activeAvailable(long tenant,long branch,long product){
  designatedLocation(tenant,branch);
  return balances.activeAvailableAtBranch(tenant,branch,product);
 }
 private long activeAvailable(long tenant,long product){
  return balances.activeAvailableForTenant(tenant,product);
 }
 private void changed(long tenant,long branch,long product,InventoryBalance balance){
  balances.flush();events.publishEvent(new StockAvailabilityChanged(tenant,branch,product,balance.available()));
 }
 private AuthorizationContext.Context actor(){
  if(authorization==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Inventory authorization context unavailable");
  return authorization.current();
 }
 private Map<String,Object> order(long tenant,String id){
  if(orderRecords==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Inventory resource authorization unavailable");
  var record=orderRecords.findByTenantIdAndId(tenant,id)
      .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Stock reservation unavailable"));
  var row=new HashMap<String,Object>();
  row.put("tenant_id",record.tenantId());
  row.put("branch_id",record.branchId());
  row.put("stock_branch_id",record.stockBranchId()==null?record.branchId():record.stockBranchId());
  row.put("cashier",record.cashier());
  row.put("session_id",record.sessionId());
  row.put("customer_id",record.customerId());
  row.put("channel",record.channel());
  row.put("status",record.status());
  return row;
 }
 private boolean expiryJob(){
  Authentication auth=SecurityContextHolder.getContext().getAuthentication();
  boolean carriesSystemGrant=auth!=null&&auth.getAuthorities().stream()
      .anyMatch(a->EXPIRY_JOB_AUTHORITY.equals(a.getAuthority()));
  if(!carriesSystemGrant)return false;
  // The system authority is not a user permission. Bind it to the private worker identity
  // installed by DraftExpiryJob so a forged/accidentally assigned grant cannot release stock.
  if(!auth.isAuthenticated()||!"system:draft-expiry".equals(auth.getName()))
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Trusted draft-expiry worker required");
  return true;
 }
 /**
  * Checks the persisted order owner before reservation mutation. Online stock is changed only
  * by the authenticated customer who owns the online order; POS stock is changed only by the
  * authenticated cashier in the assigned tenant/branch with the operation-specific grant.
  */
 private record OrderPrincipal(AuthorizationContext.Context context,Long customerId) {}
 /** Authenticate and authorize the operation before any order or stock query is issued. */
 private OrderPrincipal orderPrincipal(long tenant,String... vendorPermissions){
  var context=actor();
  if(context.isCustomer()&&!context.isPlatformUser())
   return new OrderPrincipal(context,customers.resolve(context.username()));
  context.requireVendorMembership();
  context.requireTenant(tenant);
  if(!context.hasAnyPermission(vendorPermissions))
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Inventory order operation permission required");
  return new OrderPrincipal(context,null);
 }
 private Map<String,Object> authorizeOrder(long tenant,String id,long branch,String permission){
  var principal=orderPrincipal(tenant,permission);
  var row=order(tenant,id);
  if(!Objects.equals(((Number)row.get("stock_branch_id")).longValue(),branch))
   throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Stock reservation unavailable");
  String channel=String.valueOf(row.get("channel"));
  var context=principal.context();
  if("ONLINE".equals(channel)){
   if(principal.customerId()==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Stock reservation unavailable");
   Object owner=row.get("customer_id");
   if(owner==null||((Number)owner).longValue()!=principal.customerId())
    throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Stock reservation unavailable");
   return row;
  }
  if(!"IN_STORE".equals(channel)||principal.customerId()!=null)
   throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Stock reservation unavailable");
  context.requireBranch(branch);
  if(!context.username().equals(row.get("cashier")))
   throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Stock reservation unavailable");
  context.requirePermission(permission);
  return row;
 }

 /** Safe catalogue projection: only a boolean availability value is exposed to public catalogue reads. */
 @Transactional(readOnly=true)
 public boolean publicInStock(long tenant,long product){return activeAvailable(tenant,product)>0;}

 /** Customer cart projection: reveals only whether the requested quantity is available. */
 @Transactional(readOnly=true)
 public boolean publicAvailableAtLeast(long tenant,long branch,long product,int requested){
  return requested>0&&activeAvailable(tenant,branch,product)>=requested;
 }

 /** Exact tenant-wide quantity is available only to an authenticated tenant member. */
 @Transactional(readOnly=true)
 public long available(long tenant,long product){
  var context=actor();context.requireVendorMembership();context.requireTenant(tenant);context.requirePermission("INVENTORY_VIEW");
  return activeAvailable(tenant,product);
 }

 /** Exact branch quantity is available only to an inventory-authorized member. */
 @Transactional(readOnly=true)
 public long atBranch(long tenant,long branch,long product){
  var context=actor();context.requireVendorMembership();context.requireTenant(tenant);context.requireBranch(branch);context.requirePermission("INVENTORY_VIEW");
  return activeAvailable(tenant,branch,product);
 }

 /** Require the designated branch stock room and at least one active assigned till for POS catalogue access. */
 @Transactional(readOnly=true)
 public void requirePosStockLocation(long tenant,long branch){
  var context=actor();context.requireVendorMembership();context.requireTenant(tenant);context.requireBranch(branch);
  designatedLocation(tenant,branch);
 }

 @Transactional(readOnly=true)
 public void requirePosStockScope(long tenant,long branch){
  var context=actor();context.requireVendorMembership();context.requireTenant(tenant);context.requireBranch(branch);context.requirePermission("POS_VIEW");
  designatedLocation(tenant,branch);requireAssignedRegister(context,tenant,branch);requireCurrentOpenSession(context,tenant,branch);
 }

 /** POS catalogue projection uses POS_VIEW and the server-resolved branch stock location. */
 @Transactional(readOnly=true)
 public long atBranchForPos(long tenant,long branch,long product){
  var context=actor();context.requireVendorMembership();context.requireTenant(tenant);context.requireBranch(branch);context.requirePermission("POS_VIEW");
  designatedLocation(tenant,branch);requireAssignedRegister(context,tenant,branch);requireCurrentOpenSession(context,tenant,branch);
  return activeAvailable(tenant,branch,product);
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
  designatedLocation(tenant,branch);
  var balance=balances.lock(tenant,branch,product).orElseGet(()->balances.saveAndFlush(new InventoryBalance(tenant,branch,product)));
  balance.restock(quantity);changed(tenant,branch,product,balance);return balance.available();
 }

 @Transactional
 public void reserve(long tenant,long branch,long product,int quantity,String orderId,Instant expires){
  var order=authorizeOrder(tenant,orderId,branch,"POS_SALE_CREATE");
  if(!"DRAFT".equals(order.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only draft orders may reserve stock");
  if("IN_STORE".equals(order.get("channel")))
   requireOpenOrderSession(actor(),order,"POS_SALE_CREATE");
  var balance=locked(tenant,branch,product);balance.reserve(quantity);
  reservations.save(new StockReservation(tenant,orderId,product,branch,quantity,expires));
  changed(tenant,branch,product,balance);
 }

 /** Releases only an owned online/POS draft. System expiry is separately authorized by the scheduled expiry worker. */
 @Transactional
 public void release(long tenant,String orderId){
  // Validate the special system capability before even resolving the order. A user carrying
  // the reserved authority must not trigger a data-plane lookup or reservation mutation.
  boolean trustedExpiry=expiryJob();
  OrderPrincipal principal=trustedExpiry?null:orderPrincipal(tenant,
      "POS_SALE_CANCEL","POS_TENDER_MANUAL_VERIFY","POS_TENDER_EXTERNAL_RECORD");
  Map<String,Object> order=order(tenant,orderId);
  if(trustedExpiry){
   if(!"DRAFT".equals(order.get("status")))return;
  }else{
   long branch=((Number)order.get("stock_branch_id")).longValue();
   var context=principal.context();
   if(principal.customerId()!=null){
    if(!"ONLINE".equals(order.get("channel")))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Stock reservation unavailable");
    Object owner=order.get("customer_id");
    if(owner==null||((Number)owner).longValue()!=principal.customerId())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Stock reservation unavailable");
   }else{
    if(!"IN_STORE".equals(order.get("channel")))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Stock reservation unavailable");
    context.requireBranch(branch);
    if(context.username().equals(order.get("cashier"))){
     if(!context.hasAnyPermission("POS_SALE_CANCEL","POS_TENDER_MANUAL_VERIFY","POS_TENDER_EXTERNAL_RECORD"))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Sale cancellation or payment resolution permission required");
    }else if(!context.hasAnyPermission("POS_TENDER_MANUAL_VERIFY","POS_TENDER_EXTERNAL_RECORD"))
     throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Payment resolution permission required");
   }
   if(!"DRAFT".equals(order.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only unpaid draft orders may release stock");
   if("IN_STORE".equals(order.get("channel")))requireOpenOrderSession(context,order,"POS_SALE_CANCEL");
  }
  for(var reservation:reservations.held(tenant,orderId)){
   var balance=locked(tenant,reservation.branch(),reservation.product());
   balance.release(reservation.quantity());reservation.release();changed(tenant,reservation.branch(),reservation.product(),balance);
  }
 }

 @Transactional
 public Map<Long,Integer> consume(long tenant,String orderId){
  OrderPrincipal principal=orderPrincipal(tenant,
      "POS_SALE_CREATE","POS_TENDER_MANUAL_VERIFY","POS_TENDER_EXTERNAL_CONFIRM");
  Map<String,Object> row=order(tenant,orderId);
  long branch=((Number)row.get("stock_branch_id")).longValue();
  var context=principal.context();
  if(principal.customerId()!=null){
   if(!"ONLINE".equals(row.get("channel")))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Stock reservation unavailable");
   Object owner=row.get("customer_id");
   if(owner==null||((Number)owner).longValue()!=principal.customerId())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Stock reservation unavailable");
  }else{
   if(!"IN_STORE".equals(row.get("channel")))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Stock reservation unavailable");
   context.requireBranch(branch);
   if(context.username().equals(row.get("cashier")))context.requirePermission("POS_SALE_CREATE");
   else context.requirePermission("POS_TENDER_MANUAL_VERIFY");
   requireOpenOrderSession(context,row,"POS_SALE_CREATE");
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
  designatedLocation(tenant,branch);
  if(approvedReturns==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Approved return scope resolver unavailable");
  var owner=approvedReturns.approvedOwnerSession(tenant,branch,id)
      .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Approved return unavailable"));
  requireOwnerOpenSession(tenant,branch,owner.getRequester(),owner.getSessionId(),context.username());
  var key=new ReturnDisposition.Key(tenant,id,product);
  if(returns.existsById(key))throw new ResponseStatusException(HttpStatus.CONFLICT,"Return stock already recorded");
  var balance=locked(tenant,branch,product);
  if("RESTOCK".equals(disposition))balance.restock(quantity);
  returns.save(new ReturnDisposition(tenant,branch,id,product,quantity,disposition,condition,actorName,time));
  changed(tenant,branch,product,balance);
 }
}
