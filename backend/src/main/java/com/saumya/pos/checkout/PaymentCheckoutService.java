package com.saumya.pos.checkout;

import com.saumya.pos.checkout.internal.*;
import com.saumya.pos.catalog.CatalogService;
import com.saumya.pos.identity.CustomerAccess;
import com.saumya.pos.inventory.InventoryService;
import com.saumya.pos.retail.PosService;
import java.time.Clock;
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
 @org.springframework.beans.factory.annotation.Autowired private com.saumya.pos.cart.CartService carts;
 private final DraftRepository orders;private final DraftLineRepository lines;private final PaymentAttemptRepository attempts;
 private final PaymentPolicy policy;private final CustomerAccess customers;private final CatalogService catalogue;private final InventoryService stock;private final PosService retail;private final Clock clock;
 public PaymentCheckoutService(DraftRepository orders,DraftLineRepository lines,PaymentAttemptRepository attempts,PaymentPolicy policy,CustomerAccess customers,CatalogService catalogue,InventoryService stock,PosService retail,Clock clock){this.orders=orders;this.lines=lines;this.attempts=attempts;this.policy=policy;this.customers=customers;this.catalogue=catalogue;this.stock=stock;this.retail=retail;this.clock=clock;}
 public record View(String orderId,String attemptId,String method,String state,BigDecimal amount,String merchantAccount,boolean mockEnabled,String message){}
 private void conflict(String reason){throw new ResponseStatusException(CONFLICT,reason);}
 private ResponseStatusException missing(){return new ResponseStatusException(NOT_FOUND,"Payment unavailable");}
 private DraftOrder owned(String username,String id,boolean online){
  if(online){long customer=customers.resolve(username);customers.lock(customer);var order=orders.lockCustomer(customer,id).orElseThrow(this::missing);retail.lockOutlet(new PosService.Scope(order.tenant(),order.stockBranch(),"customer:"+customer));return order;}
  var s=retail.scope(username);retail.lockOutlet(s);var order=orders.lock(s.tenant(),id).orElseThrow(this::missing);
  if(!"IN_STORE".equals(order.channel())||!Objects.equals(order.branch(),s.branch())||!username.equals(order.cashier()))throw missing();
  return order;
 }
 private View view(DraftOrder order){var attempt=attempts.findByTenantAndOrder(order.tenant(),order.id());return attempt.map(a->new View(order.id(),a.id(),a.method(),a.state(),a.amount(),a.merchant(),policy.enabled(),"SIMULATION ONLY — no funds collected. Payments belong directly to this vendor.")).orElseGet(()->new View(order.id(),null,null,"NOT_STARTED",order.total(),null,policy.enabled(),policy.enabled()?"Pre-production simulation. No real funds are collected.":"Online/UPI/card payments require a configured vendor-direct provider."));}
 public View status(String username,String id,boolean online){return view(owned(username,id,online));}
 private void payable(DraftOrder order){
  if(!"DRAFT".equals(order.status())||!order.expires().isAfter(clock.instant()))conflict("Reservation is not payable; reload or cancel it");
  if(!catalogue.vendor(order.tenant()).active())conflict("Vendor is not active");
  if("IN_STORE".equals(order.channel()))retail.requireOpen(new PosService.Scope(order.tenant(),order.branch(),order.cashier()),order.session());
  else if(catalogue.onlineBranch(order.tenant())!=order.stockBranch())conflict("Vendor stock location changed");
  for(var line:lines.lines(order.tenant(),order.id())){
   var p=catalogue.lockForSale(order.tenant(),line.product());
   if(p.price().compareTo(line.price())!=0||p.taxRate().compareTo(line.rate())!=0||p.listPrice().compareTo(line.listPrice())!=0||p.discountPercent().compareTo(line.discountPercent())!=0)conflict("Price, tax or discount changed; cancel and recreate the draft");
  }
  if(!order.expires().isAfter(clock.instant()))conflict("Reservation expired during validation");
 }
 public View start(String username,String id,boolean online,String method){
  policy.requireEnabled();policy.method(method);var order=owned(username,id,online);
  var prior=attempts.findByTenantAndOrder(order.tenant(),id);
  if(prior.isPresent()){if(!prior.get().method().equals(method))conflict("Recover the existing payment; payment method is frozen");return view(order);}
  payable(order);attempts.saveAndFlush(new PaymentAttempt(order.tenant(),id,method,order.total(),clock.instant()));
  if(!online)retail.draftAudit(new PosService.Scope(order.tenant(),order.branch(),username),"MOCK_PAYMENT_START",id);
  return view(order);
 }
 public View confirm(String username,String id,boolean online,String attemptId){
  policy.requireEnabled();var order=owned(username,id,online);var attempt=attempts.findByTenantAndOrder(order.tenant(),id).orElseThrow(this::missing);
  if(!attempt.id().equals(attemptId))throw missing();
  if("CONFIRMED".equals(attempt.state()))return view(order);
  payable(order);
  if(attempt.amount().compareTo(order.total())!=0||!attempt.merchant().equals("mock-vendor-"+order.tenant()))conflict("Vendor payment context mismatch");
  var confirmation=adapter.confirmSimulation(attempt.merchant(),attempt.id(),attempt.amount());
  if(!attempt.id().equals(confirmation.attemptId())||!attempt.merchant().equals(confirmation.merchantAccount())||attempt.amount().compareTo(confirmation.amount())!=0)conflict("Payment confirmation does not match vendor, attempt and amount");
  var saved=lines.lines(order.tenant(),id);var expected=new LinkedHashMap<Long,Integer>();for(var line:saved)expected.put(line.product(),line.quantity());
  if(!stock.consume(order.tenant(),id).equals(expected))throw new IllegalStateException("Reservation does not match sale");
  PosService.Scope scope=new PosService.Scope(order.tenant(),order.stockBranch(),online?"customer:"+order.customer():username);
  if(online){var allocation=retail.allocateReceipt(scope);order.payExternal(attempt.method(),null,allocation.invoiceRequired(),clock.instant());orders.flush();retail.onlineDocuments(scope,id,saved.stream().map(l->new PosService.StockIssue(l.product(),l.quantity())).toList(),order.total(),allocation);}
  else{var receipt=retail.allocateReceipt(scope);boolean invoice=receipt.invoiceRequired()||order.invoiceRequested();order.payExternal(attempt.method(),receipt.number(),invoice,clock.instant());orders.flush();retail.draftDocuments(scope,id,saved.stream().map(l->new PosService.StockIssue(l.product(),l.quantity())).toList(),receipt,order.total(),invoice);retail.draftAudit(scope,"MOCK_PAYMENT_CONFIRM",id);}
  if(online)carts.archivePurchased(order.customer(),order.tenant(),expected);
  attempt.confirm(clock.instant(),confirmation.reference());attempts.flush();return view(order);
 }
}
