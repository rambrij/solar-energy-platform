package com.saumya.pos.checkout;

import com.saumya.pos.checkout.internal.*;
import com.saumya.pos.catalog.CatalogService;
import com.saumya.pos.identity.CustomerAccess;
import com.saumya.pos.inventory.InventoryService;
import com.saumya.pos.retail.PosService;
import java.time.Clock;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

/** Two requests separate durable initiation from explicit mock-provider confirmation. No real funds move. */
@Service @Transactional
public class PaymentCheckoutService {
 @org.springframework.beans.factory.annotation.Autowired private VendorPaymentAdapter adapter;
 @org.springframework.beans.factory.annotation.Autowired private TerminalPolicyRepository terminalPolicies;
 @org.springframework.beans.factory.annotation.Autowired private TerminalTenderRules terminalRules;
 @org.springframework.beans.factory.annotation.Autowired private com.saumya.pos.cart.CartService carts;
 private final DraftRepository orders;private final DraftLineRepository lines;private final PaymentAttemptRepository attempts;
 private final PaymentPolicy policy;private final CustomerAccess customers;private final CatalogService catalogue;private final InventoryService stock;private final PosService retail;private final Clock clock;
 public PaymentCheckoutService(DraftRepository orders,DraftLineRepository lines,PaymentAttemptRepository attempts,PaymentPolicy policy,CustomerAccess customers,CatalogService catalogue,InventoryService stock,PosService retail,Clock clock){this.orders=orders;this.lines=lines;this.attempts=attempts;this.policy=policy;this.customers=customers;this.catalogue=catalogue;this.stock=stock;this.retail=retail;this.clock=clock;}
 public record View(String orderId,String attemptId,String method,String state,BigDecimal amount,String merchantAccount,boolean mockEnabled,String message,boolean terminalEnabled,String orderStatus,String cashier,String reference,String reviewedBy,Instant reviewedAt,String evidenceReference){}
 private void conflict(String reason){throw new ResponseStatusException(CONFLICT,reason);}
 private ResponseStatusException missing(){return new ResponseStatusException(NOT_FOUND,"Payment unavailable");}
 private DraftOrder owned(String username,String id,boolean online){
  if(online){long customer=customers.resolve(username);customers.lock(customer);var order=orders.lockCustomer(customer,id).orElseThrow(this::missing);retail.lockOutlet(new PosService.Scope(order.tenant(),order.stockBranch(),"customer:"+customer));return order;}
  var s=retail.scope(username);retail.lockOutlet(s);var order=orders.lock(s.tenant(),id).orElseThrow(this::missing);
  if(!"IN_STORE".equals(order.channel())||!Objects.equals(order.branch(),s.branch())||!username.equals(order.cashier()))throw missing();
  return order;
 }
 private TerminalPolicy terminalPolicy(DraftOrder order){return terminalPolicies.findByTenantAndId(order.tenant(),order.branch()).orElseThrow(this::missing);}
 private View view(DraftOrder order){
  boolean online="ONLINE".equals(order.channel()),enabled=!online&&terminalPolicy(order).enabled();var attempt=attempts.findByTenantAndOrder(order.tenant(),order.id());
  String message=online?(policy.enabled()?"Pre-production online simulation. No real funds are collected.":"Online vendor payment provider is not configured."):"Prepare before collecting payment on the vendor's separate terminal. Only a different authorized supervisor can confirm external evidence. Unknown outcomes must be reconciled; do not charge again.";
  return attempt.map(a->new View(order.id(),a.id(),a.method(),a.state(),a.amount(),a.merchant(),online&&policy.enabled(),message,enabled,order.status(),order.cashier(),a.reference(),a.reviewer(),a.reviewedAt(),a.evidence())).orElseGet(()->new View(order.id(),null,null,"NOT_STARTED",order.total(),null,online&&policy.enabled(),enabled||online?message:"Vendor manual terminal verification is not configured for this outlet.",enabled,order.status(),order.cashier(),null,null,null,null));
 }
 public View status(String username,String id,boolean online){return view(owned(username,id,online));}
 private void payable(DraftOrder order){payable(order,false);}
 private void payable(DraftOrder order,boolean reconcilingTerminal){
  if(!"DRAFT".equals(order.status())||(!reconcilingTerminal&&!order.expires().isAfter(clock.instant())))conflict("Reservation is not payable; reload or cancel it");
  if(!catalogue.vendor(order.tenant()).active())conflict("Vendor is not active");
  if("IN_STORE".equals(order.channel()))retail.requireOpen(new PosService.Scope(order.tenant(),order.branch(),order.cashier()),order.session());
  else if(catalogue.onlineBranch(order.tenant())!=order.stockBranch())conflict("Vendor stock location changed");
  for(var line:lines.lines(order.tenant(),order.id())){
   var p=catalogue.lockForSale(order.tenant(),line.product());
   if(p.price().compareTo(line.price())!=0||p.taxRate().compareTo(line.rate())!=0||p.listPrice().compareTo(line.listPrice())!=0||p.discountPercent().compareTo(line.discountPercent())!=0)conflict("Price, tax or discount changed; cancel and recreate the draft");
  }
  if(!reconcilingTerminal&&!order.expires().isAfter(clock.instant()))conflict("Reservation expired during validation");
 }
 public View start(String username,String id,boolean online,String method){
  if(online)policy.requireEnabled();policy.method(method);var order=owned(username,id,online);
  var prior=attempts.findByTenantAndOrder(order.tenant(),id);
  if(prior.isPresent()){if(!online&&!prior.get().terminal())conflict("This historical simulated POS tender cannot be used as a terminal payment");if(!prior.get().method().equals(method))conflict("Recover the existing payment; payment method is frozen");return view(order);}
  payable(order);
  if(online)attempts.saveAndFlush(new PaymentAttempt(order.tenant(),id,method,order.total(),clock.instant()));
  else{var configured=terminalPolicy(order);if(!configured.enabled())conflict("Manual terminal verification is disabled");String merchant=terminalRules.token(configured.merchant(method),80);attempts.saveAndFlush(PaymentAttempt.terminal(order.tenant(),id,method,merchant,order.total(),clock.instant()));retail.draftAudit(new PosService.Scope(order.tenant(),order.branch(),username),"TERMINAL_TENDER_PREPARE",id);}
  return view(order);
 }
 public View confirm(String username,String id,boolean online,String attemptId){
  if(!online)conflict("Terminal tenders require independent supervisor verification; online simulation is prohibited");
  policy.requireEnabled();var order=owned(username,id,true);var attempt=attempts.findByTenantAndOrder(order.tenant(),id).orElseThrow(this::missing);
  if(!attempt.id().equals(attemptId))throw missing();
  if("CONFIRMED".equals(attempt.state()))return view(order);
  payable(order);
  if(attempt.amount().compareTo(order.total())!=0||!attempt.merchant().equals("mock-vendor-"+order.tenant()))conflict("Vendor payment context mismatch");
  var confirmation=adapter.confirmSimulation(attempt.merchant(),attempt.id(),attempt.amount());
  if(!attempt.id().equals(confirmation.attemptId())||!attempt.merchant().equals(confirmation.merchantAccount())||attempt.amount().compareTo(confirmation.amount())!=0)conflict("Payment confirmation does not match vendor, attempt and amount");
  complete(order,attempt,true);attempt.confirm(clock.instant(),confirmation.reference());attempts.flush();return view(order);
 }
 private void complete(DraftOrder order,PaymentAttempt attempt,boolean online){
  String id=order.id();var saved=lines.lines(order.tenant(),id);var expected=new LinkedHashMap<Long,Integer>();for(var line:saved)expected.put(line.product(),line.quantity());
  if(!stock.consume(order.tenant(),id).equals(expected))throw new IllegalStateException("Reservation does not match sale");
  PosService.Scope scope=new PosService.Scope(order.tenant(),order.stockBranch(),online?"customer:"+order.customer():order.cashier());
  if(online){var allocation=retail.allocateReceipt(scope);order.payOnlineMock(attempt.method(),allocation.invoiceRequired(),clock.instant());orders.flush();retail.onlineDocuments(scope,id,saved.stream().map(l->new PosService.StockIssue(l.product(),l.quantity())).toList(),order.total(),allocation);}
  else{var receipt=retail.allocateReceipt(scope);boolean invoice=receipt.invoiceRequired()||order.invoiceRequested();order.payTerminal(attempt.method(),attempt.reference(),receipt.number(),invoice,clock.instant());orders.flush();retail.draftDocuments(scope,id,saved.stream().map(l->new PosService.StockIssue(l.product(),l.quantity())).toList(),receipt,order.total(),invoice);}
  if(online)carts.archivePurchased(order.customer(),order.tenant(),expected);
 }
 public View unknown(String username,String id,String attemptId){var order=owned(username,id,false);var a=attempts.findByTenantAndOrder(order.tenant(),id).orElseThrow(this::missing);if(!a.id().equals(attemptId)||!a.terminal())throw missing();if("PENDING_VERIFICATION".equals(a.state())){a.unknown();attempts.flush();retail.draftAudit(new PosService.Scope(order.tenant(),order.branch(),username),"TERMINAL_TENDER_UNKNOWN",id);}return view(order);}
 private DraftOrder supervisorOrder(PosService.Scope scope,String id){retail.lockOutlet(scope);var order=orders.lock(scope.tenant(),id).orElseThrow(this::missing);if(!"IN_STORE".equals(order.channel())||!Objects.equals(order.branch(),scope.branch()))throw missing();return order;}
 public View reviewStatus(String username,String id){var scope=retail.scope(username);return view(supervisorOrder(scope,id));}
 public List<View> awaitingReview(String username){var scope=retail.scope(username);return attempts.awaitingReview(scope.tenant(),scope.branch(),org.springframework.data.domain.PageRequest.of(0,20)).stream().map(a->view(orders.findById(a.order()).orElseThrow(this::missing))).toList();}
 public record Review(String attemptId,String decision,String reference,String evidence,String reason){}
 public View review(String username,String id,Review request){
  if(request==null)throw new ResponseStatusException(BAD_REQUEST,"Verification evidence required");
  terminalRules.decision(request.decision());String reference=terminalRules.token(request.reference(),80),evidence=terminalRules.token(request.evidence(),100),reason=terminalRules.reason(request.reason());
  var scope=retail.scope(username);var order=supervisorOrder(scope,id);terminalRules.independent(order.cashier(),scope.cashier());var a=attempts.findByTenantAndOrder(scope.tenant(),id).orElseThrow(this::missing);
  if(!a.id().equals(request.attemptId())||!a.terminal())throw missing();
  if(a.amount().compareTo(order.total())!=0)conflict("Tender amount does not match the original order");
  if(!a.unresolved()){if(username.equals(a.reviewer())&&reference.equals(a.reference())&&evidence.equals(a.evidence())&&reason.equals(a.reason())&&(("CONFIRM".equals(request.decision())&&"CONFIRMED".equals(a.state()))||("FAIL".equals(request.decision())&&"FAILED".equals(a.state()))))return view(order);conflict("Tender already has another verification decision");}
  if(!"DRAFT".equals(order.status()))conflict("Tender does not belong to an unresolved draft");
  if("CONFIRM".equals(request.decision())){payable(order,true);a.review(username,request.decision(),reference,evidence,reason,clock.instant());complete(order,a,false);}
  else{a.review(username,request.decision(),reference,evidence,reason,clock.instant());stock.release(order.tenant(),id);order.cancel("Terminal payment verified failed: "+reason.substring(0,Math.min(210,reason.length())),clock.instant());orders.flush();}
  attempts.flush();retail.draftAudit(scope,"CONFIRM".equals(request.decision())?"TERMINAL_TENDER_CONFIRM":"TERMINAL_TENDER_FAIL",id);return view(order);
 }
}
