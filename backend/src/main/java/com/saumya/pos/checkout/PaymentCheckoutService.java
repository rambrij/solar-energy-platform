package com.saumya.pos.checkout;

import com.saumya.pos.checkout.internal.*;
import com.saumya.pos.retail.BranchPolicyService;
import com.saumya.pos.catalog.CatalogService;
import com.saumya.pos.identity.CustomerAccess;
import com.saumya.pos.inventory.InventoryService;
import com.saumya.pos.membership.TenantEntitlementPolicy;
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
 @org.springframework.beans.factory.annotation.Autowired private SaleDocumentsService documents;
 @org.springframework.beans.factory.annotation.Autowired private org.springframework.context.ApplicationEventPublisher events;
 @org.springframework.beans.factory.annotation.Autowired private VendorPaymentAdapter adapter;
 @org.springframework.beans.factory.annotation.Autowired private BranchPolicyService terminalPolicies;
 @org.springframework.beans.factory.annotation.Autowired private TerminalTenderRules terminalRules;
 @org.springframework.beans.factory.annotation.Autowired private com.saumya.pos.cart.CartService carts;
 @org.springframework.beans.factory.annotation.Autowired private TenantEntitlementPolicy entitlements;
 private final DraftRepository orders;private final DraftLineRepository lines;private final PaymentAttemptRepository attempts;
 private final PaymentPolicy policy;private final CustomerAccess customers;private final CatalogService catalogue;private final InventoryService stock;private final PosService retail;private final Clock clock;
 public PaymentCheckoutService(DraftRepository orders,DraftLineRepository lines,PaymentAttemptRepository attempts,PaymentPolicy policy,CustomerAccess customers,CatalogService catalogue,InventoryService stock,PosService retail,Clock clock){this.orders=orders;this.lines=lines;this.attempts=attempts;this.policy=policy;this.customers=customers;this.catalogue=catalogue;this.stock=stock;this.retail=retail;this.clock=clock;}
 public record TenderRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(regexp="CASH|UPI|CARD") String method,@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0.00") @jakarta.validation.constraints.Digits(integer=12,fraction=2) BigDecimal appliedAmount,@jakarta.validation.constraints.DecimalMin("0.00") @jakarta.validation.constraints.Digits(integer=12,fraction=2) BigDecimal cashReceived){}
 public record TenderSummary(String orderId,String orderStatus,BigDecimal total,BigDecimal confirmed,BigDecimal remaining,boolean splitEnabled,List<View> tenders){}
 private List<PaymentAttempt> tenders(DraftOrder order){return attempts.findAllByTenantAndOrderOrderByCreatedAscIdAsc(order.tenant(),order.id());}
 private BigDecimal collected(DraftOrder order){return tenders(order).stream().filter(a->"CONFIRMED".equals(a.state())).map(PaymentAttempt::amount).reduce(BigDecimal.ZERO,BigDecimal::add);}
 private View tenderView(DraftOrder order,PaymentAttempt a){return new View(order.id(),a.id(),a.method(),a.state(),a.amount(),a.merchant(),false,"Only confirmed applied amounts count toward the bill.",terminalPolicy(order).enabled(),order.status(),order.cashier(),a.reference(),a.reviewer(),a.reviewedAt(),a.evidence());}
 private TenderSummary summary(DraftOrder order){var confirmed=collected(order);return new TenderSummary(order.id(),order.status(),order.total(),confirmed,order.total().subtract(confirmed),terminalPolicy(order).split(),tenders(order).stream().map(a->tenderView(order,a)).toList());}
 public record ReceiptTender(String method,BigDecimal appliedAmount,BigDecimal cashReceived,BigDecimal changeGiven,String reference){}
 public TenderSummary tenderStatus(String username,String id){return summary(owned(username,id,false));}
 public TenderSummary tender(String username,String id,String key,TenderRequest request){
  if(key==null||!key.matches("[A-Za-z0-9_-]{8,80}")||request==null||request.method()==null||!Set.of("CASH","UPI","CARD").contains(request.method())||request.appliedAmount()==null||request.appliedAmount().signum()<0||request.appliedAmount().scale()>2||request.appliedAmount().precision()-request.appliedAmount().scale()>12)throw new ResponseStatusException(BAD_REQUEST,"Valid tender method, amount and idempotency key required");
  var order=owned(username,id,false);retail.authorize(new PosService.Scope(order.tenant(),order.branch(),username),username,
      "POS_SALE_CREATE","CASH".equals(request.method())?"POS_TENDER_CASH_RECORD":"POS_TENDER_EXTERNAL_RECORD");String fingerprint=request.method()+"|"+request.appliedAmount().setScale(2)+"|"+(request.cashReceived()==null?"":request.cashReceived().stripTrailingZeros().toPlainString());
  String hash;try{hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(fingerprint.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
  var prior=tenders(order).stream().filter(a->key.equals(a.key())).findFirst();if(prior.isPresent()){if(!hash.equals(prior.get().hash()))conflict("Idempotency key already used for a different tender");return summary(order);}
  payable(order,collected(order).signum()>0);var configured=terminalPolicy(order);var confirmed=collected(order);var remaining=order.total().subtract(confirmed);
  if(tenders(order).stream().anyMatch(PaymentAttempt::unresolved))conflict("Reconcile the existing external tender before another payment");
  if(request.appliedAmount().compareTo(remaining)>0||request.appliedAmount().signum()==0&&order.total().signum()!=0)throw new com.saumya.pos.shared.DomainProblem("PAYMENT_TOTAL_MISMATCH","Tender applied amount exceeds the remaining bill or is zero");
  if(!configured.split()&&(confirmed.signum()>0||request.appliedAmount().compareTo(remaining)!=0))conflict("Split tender is disabled for this outlet");
  PaymentAttempt attempt;
  if("CASH".equals(request.method())){
   var received=request.cashReceived();if(received==null||received.scale()>2||received.precision()-received.scale()>12||received.compareTo(request.appliedAmount())<0)throw new ResponseStatusException(BAD_REQUEST,"Cash received must cover the applied cash amount");
   attempt=PaymentAttempt.cash(order.tenant(),id,request.appliedAmount(),received,key,hash,clock.instant());
  }else{
   if(request.cashReceived()!=null)throw new ResponseStatusException(BAD_REQUEST,"External tender cannot contain cash received");
   if(!configured.enabled())conflict("Manual terminal verification is disabled");
   attempt=PaymentAttempt.terminal(order.tenant(),id,request.method(),terminalRules.token(configured.merchant(request.method()),80),request.appliedAmount(),clock.instant());attempt.command(key,hash);
  }
  attempts.saveAndFlush(attempt);if("CASH".equals(attempt.method()))events.publishEvent(new com.saumya.pos.shared.CashTenderRecorded(attempt.id(),order.tenant(),order.branch(),order.session(),id,attempt.cash(),attempt.change()));retail.draftAudit(new PosService.Scope(order.tenant(),order.branch(),username),"TENDER_RECORD",id);
  if("CASH".equals(request.method())&&collected(order).compareTo(order.total())==0)complete(order,attempt,false);
  return summary(order);
 }
 public TenderSummary finalizeTenders(String username,String id){var order=owned(username,id,false);retail.authorize(new PosService.Scope(order.tenant(),order.branch(),username),username,"POS_SALE_CREATE");if(!"PAID".equals(order.status())){payable(order,true);var rows=tenders(order);if(rows.isEmpty()||collected(order).compareTo(order.total())!=0||rows.stream().anyMatch(PaymentAttempt::unresolved))throw new com.saumya.pos.shared.DomainProblem("PAYMENT_TOTAL_MISMATCH","Confirmed tenders must equal the bill total");complete(order,rows.getFirst(),false);}return summary(order);}
 public record View(String orderId,String attemptId,String method,String state,BigDecimal amount,String merchantAccount,boolean mockEnabled,String message,boolean terminalEnabled,String orderStatus,String cashier,String reference,String reviewedBy,Instant reviewedAt,String evidenceReference){}
 private void conflict(String reason){throw new ResponseStatusException(CONFLICT,reason);}
 private ResponseStatusException missing(){return new ResponseStatusException(NOT_FOUND,"Payment unavailable");}
    
    private DraftOrder owned(String username, String id, boolean online) {
        if (online) {
            retail.authorizeCustomer(username);
            long customer = customers.resolve(username);
            customers.lock(customer);
            var order = orders.lockCustomer(customer, id).orElseThrow(this::missing);
            return order;
        }
        var s = retail.scope();retail.authorize(s,username,"POS_VIEW");
        retail.lockOutlet(s);
        var order = orders.lock(s.tenant(), id).orElseThrow(this::missing);
  if(!"IN_STORE".equals(order.channel())||!Objects.equals(order.branch(),s.branch())||!username.equals(order.cashier()))throw missing();
  return order;
 }
 private void requireOnlineEntitlement(DraftOrder order,boolean write){
  if(entitlements==null)throw new ResponseStatusException(FORBIDDEN,"Tenant entitlement policy unavailable");
  entitlements.requireFeature(order.tenant(),"ORDERS",write);
  entitlements.requireFeature(order.tenant(),"CATALOG",false);
 }
 private BranchPolicyService.Policy terminalPolicy(DraftOrder order){return terminalPolicies.find(order.tenant(),order.branch()).orElseThrow(this::missing);}
 private View view(DraftOrder order){
  boolean online="ONLINE".equals(order.channel()),enabled=!online&&terminalPolicy(order).enabled();var attempt=attempts.findByTenantAndOrder(order.tenant(),order.id());
  String message=online?(policy.enabled()?"Pre-production online simulation. No real funds are collected.":"Online vendor payment provider is not configured."):"Prepare before collecting payment on the vendor's separate terminal. Only a different authorized supervisor can confirm external evidence. Unknown outcomes must be reconciled; do not charge again.";
  return attempt.map(a->new View(order.id(),a.id(),a.method(),a.state(),a.amount(),a.merchant(),online&&policy.enabled(),message,enabled,order.status(),order.cashier(),a.reference(),a.reviewer(),a.reviewedAt(),a.evidence())).orElseGet(()->new View(order.id(),null,null,"NOT_STARTED",order.total(),null,online&&policy.enabled(),enabled||online?message:"Vendor manual terminal verification is not configured for this outlet.",enabled,order.status(),order.cashier(),null,null,null,null));
 }
 public View status(String username,String id){var order=owned(username,id,true);if("ONLINE".equals(order.channel()))requireOnlineEntitlement(order,false);return view(order);}
 private void payable(DraftOrder order){payable(order,false);}
 private void payable(DraftOrder order,boolean reconcilingFunds){
  payable(order,reconcilingFunds,null);
 }
 private void payable(DraftOrder order,boolean reconcilingFunds,PosService.Scope reviewerScope){
  if(!"DRAFT".equals(order.status())||(!reconcilingFunds&&!order.expires().isAfter(clock.instant())))conflict("Reservation is not payable; reload or cancel it");
  if(!catalogue.vendor(order.tenant()).active())conflict("Vendor is not active");
  if("IN_STORE".equals(order.channel())){
   if(reviewerScope==null)retail.requireOpen(new PosService.Scope(order.tenant(),order.branch(),order.cashier()),order.session());
   else retail.requireOpenForPaymentReview(reviewerScope,order.cashier(),order.session());
  }
  else if(catalogue.onlineBranch(order.tenant())!=order.stockBranch())conflict("Vendor stock location changed");
  for(var line:lines.lines(order.tenant(),order.id())){
   var p=reviewerScope==null
       ?catalogue.lockForSale(order.tenant(),line.product())
       :catalogue.lockForSaleForPaymentReview(order.tenant(),line.product());
   if(p.price().compareTo(line.price())!=0||p.taxRate().compareTo(line.rate())!=0||p.listPrice().compareTo(line.listPrice())!=0||p.discountPercent().compareTo(line.discountPercent())!=0)conflict("Price, tax or discount changed; cancel and recreate the draft");
  }
  if(!reconcilingFunds&&!order.expires().isAfter(clock.instant()))conflict("Reservation expired during validation");
 }
 public View start(String username,String id,String method){
  policy.requireEnabled();policy.method(method);var order=owned(username,id,true);if("ONLINE".equals(order.channel()))requireOnlineEntitlement(order,true);
  var prior=attempts.findByTenantAndOrder(order.tenant(),id);
  if(prior.isPresent()){if(!prior.get().method().equals(method))conflict("Recover the existing payment; payment method is frozen");return view(order);}
  payable(order);attempts.saveAndFlush(new PaymentAttempt(order.tenant(),id,method,order.total(),clock.instant()));return view(order);
 }
 public View confirm(String username,String id,String attemptId){
  policy.requireEnabled();var order=owned(username,id,true);if("ONLINE".equals(order.channel()))requireOnlineEntitlement(order,true);var attempt=attempts.findByTenantAndOrder(order.tenant(),id).orElseThrow(this::missing);
  if(!attempt.id().equals(attemptId))throw missing();
  if("CONFIRMED".equals(attempt.state()))return view(order);
  payable(order);
  if(attempt.amount().compareTo(order.total())!=0||!attempt.merchant().equals("mock-vendor-"+order.tenant()))conflict("Vendor payment context mismatch");
  var confirmation=adapter.confirmSimulation(attempt.merchant(),attempt.id(),attempt.amount());
  if(!attempt.id().equals(confirmation.attemptId())||!attempt.merchant().equals(confirmation.merchantAccount())||attempt.amount().compareTo(confirmation.amount())!=0)conflict("Payment confirmation does not match vendor, attempt and amount");
  attempt.confirm(clock.instant(),confirmation.reference());attempts.flush();complete(order,attempt,true);attempts.flush();return view(order);
 }
 private void complete(DraftOrder order,PaymentAttempt attempt,boolean online){
  complete(order,attempt,online,null);
 }

 /** Payment supervisors finalize on their own authenticated scope without changing sale ownership. */
 private void complete(DraftOrder order,PaymentAttempt attempt,boolean online,PosService.Scope finalizerScope){
  String id=order.id();var saved=lines.lines(order.tenant(),id);var expected=new LinkedHashMap<Long,Integer>();for(var line:saved)expected.put(line.product(),line.quantity());
  if(!stock.consume(order.tenant(),id).equals(expected))throw new IllegalStateException("Reservation does not match sale");
  PosService.Scope ownerScope=new PosService.Scope(order.tenant(),order.stockBranch(),online?"customer:"+order.customer():order.cashier());
  PosService.Scope actorScope=finalizerScope==null?ownerScope:finalizerScope;
  if(actorScope.tenant()!=ownerScope.tenant()||actorScope.branch()!=ownerScope.branch()||(online&&finalizerScope!=null))
   throw new IllegalStateException("Payment finalizer scope does not match sale ownership");
  if(online){
   var allocation=retail.allocateReceiptForCustomer(order.tenant(),order.stockBranch(),order.customer(),order.id());
   order.payOnlineMock(attempt.method(),allocation.invoiceRequired(),clock.instant());
   documents.issue(ownerScope,actorScope,order,allocation,allocation.invoiceRequired(),true);orders.flush();
  } else {
   var confirmed=tenders(order).stream().filter(a->"CONFIRMED".equals(a.state())).toList();
   if(collected(order).compareTo(order.total())!=0||tenders(order).stream().anyMatch(PaymentAttempt::unresolved))
    throw new com.saumya.pos.shared.DomainProblem("PAYMENT_TOTAL_MISMATCH","Confirmed tenders must equal the bill total");
   var receipt=retail.allocateReceipt(actorScope);boolean invoice=receipt.invoiceRequired()||order.invoiceRequested();
   var received=confirmed.stream().map(PaymentAttempt::cash).reduce(BigDecimal.ZERO,BigDecimal::add);
   var change=confirmed.stream().map(PaymentAttempt::change).reduce(BigDecimal.ZERO,BigDecimal::add);
   order.payTenders(confirmed.size()==1?confirmed.getFirst().method():"SPLIT",attempt.reference(),received,change,receipt.number(),invoice,clock.instant());
   documents.issue(ownerScope,actorScope,order,receipt,invoice,false);orders.flush();
  }
  if(!online)events.publishEvent(new com.saumya.pos.shared.PosDraftStateChanged(order.tenant(),order.branch(),order.cashier(),order.session(),order.id(),order.status()));
  if(online)carts.archivePurchased(order.customer(),order.tenant(),expected);
 }
 
 public View unknown(String username, String id, String attemptId) {
  var order = owned(username, id, false);
  retail.authorize(new PosService.Scope(order.tenant(),order.branch(),username),username,"POS_TENDER_EXTERNAL_RECORD");
  var a = tenders(order).stream().filter(t -> t.id().equals(attemptId)).findFirst().orElseThrow(this::missing);
  if (!a.id().equals(attemptId) || !a.terminal()) throw missing();
  if ("PENDING_VERIFICATION".equals(a.state())) {
   a.unknown();
   attempts.flush();
   retail.draftAudit(new PosService.Scope(order.tenant(), order.branch(), username), "TERMINAL_TENDER_UNKNOWN", id);
  }
  return tenderView(order, a);
 }
 
 private DraftOrder supervisorOrder(PosService.Scope scope, String id) {
  retail.lockOutlet(scope);
  var order = orders.lock(scope.tenant(), id).orElseThrow(this::missing);
  if (!"IN_STORE".equals(order.channel()) || !Objects.equals(order.branch(), scope.branch())) throw missing();
  return order;
 }
 
 public View reviewStatus(String username, String id) {
  var scope = retail.scope();retail.authorize(scope,username,"POS_TENDER_MANUAL_VERIFY");
  var order = supervisorOrder(scope, id);
  return tenderView(order, tenders(order).stream().filter(PaymentAttempt::unresolved).findFirst().orElseGet(() -> tenders(order).getLast()));
 }
 
 public List<View> awaitingReview(String username) {
  var scope = retail.scope();retail.authorize(scope,username,"POS_TENDER_MANUAL_VERIFY");
  return attempts.awaitingReview(scope.tenant(), scope.branch(), org.springframework.data.domain.PageRequest.of(0, 20)).stream().map(a -> tenderView(orders.findById(a.order()).orElseThrow(this::missing), a)).toList();
 }
 
 public View finalizeVerified(String username, String id, String attemptId) {
  var scope = retail.scope();retail.authorize(scope,username,"POS_TENDER_MANUAL_VERIFY");
  var order = supervisorOrder(scope, id);
  var a = tenders(order).stream().filter(t -> t.id().equals(attemptId)).findFirst().orElseThrow(this::missing);
  if (!"PAID".equals(order.status()) && collected(order).compareTo(order.total()) == 0) {
   payable(order, true, scope);
   complete(order, a, false, scope);
  }
  return tenderView(order, a);
 }
 public record Review(String attemptId,String decision,String reference,String evidence,String reason){}
 
 public View verifyEvidence(String username, String id, Review request) {
  if (request == null) throw new ResponseStatusException(BAD_REQUEST, "Verification evidence required");
  terminalRules.decision(request.decision());
  String reference = terminalRules.token(request.reference(), 80), evidence = terminalRules.token(request.evidence(), 100), reason = terminalRules.reason(request.reason());
  var scope = retail.scope();retail.authorize(scope,username,"POS_TENDER_MANUAL_VERIFY");
  var order = supervisorOrder(scope, id);
  terminalRules.independent(order.cashier(), scope.cashier());
  var a = tenders(order).stream().filter(t -> t.id().equals(request.attemptId())).findFirst().orElseThrow(this::missing);
  if(!a.id().equals(request.attemptId())||!a.terminal())throw missing();
  if(a.amount().compareTo(order.total())>0)conflict("Tender amount does not match the original order");
  if(!a.unresolved()){if(username.equals(a.reviewer())&&reference.equals(a.reference())&&evidence.equals(a.evidence())&&reason.equals(a.reason())&&(("CONFIRM".equals(request.decision())&&"CONFIRMED".equals(a.state()))||("FAIL".equals(request.decision())&&"FAILED".equals(a.state()))))return tenderView(order,a);conflict("Tender already has another verification decision");}
  if(!"DRAFT".equals(order.status()))conflict("Tender does not belong to an unresolved draft");
  if("CONFIRM".equals(request.decision())){a.review(username,request.decision(),reference,evidence,reason,clock.instant());}
  else{a.review(username,request.decision(),reference,evidence,reason,clock.instant());if(collected(order).signum()==0){stock.release(order.tenant(),id);order.cancel("Terminal payment verified failed: "+reason.substring(0,Math.min(210,reason.length())),clock.instant());orders.flush();}}
  attempts.flush();retail.draftAudit(scope,"CONFIRM".equals(request.decision())?"TERMINAL_TENDER_CONFIRM":"TERMINAL_TENDER_FAIL",id);return tenderView(order,a);
 }
}
