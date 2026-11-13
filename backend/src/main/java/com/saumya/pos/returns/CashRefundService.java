package com.saumya.pos.returns;

import com.saumya.pos.returns.internal.*;
import com.saumya.pos.checkout.ReturnSaleAccess;
import com.saumya.pos.retail.PosService;
import jakarta.validation.constraints.*;
import java.math.*;
import java.time.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service @Transactional
public class CashRefundService {
 @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
 private final CashRefundRepository refunds;private final CashRefundItemRepository amounts;private final ReturnRepository returns;private final ReturnItemRepository returnedItems;private final ReturnEventRepository events;
 private final ReturnSaleAccess sales;private final PosService retail;private final RefundAllocation allocation;private final ReturnRules rules;private final Clock clock;
 public CashRefundService(CashRefundRepository refunds,CashRefundItemRepository amounts,ReturnRepository returns,ReturnItemRepository returnedItems,ReturnEventRepository events,ReturnSaleAccess sales,PosService retail,RefundAllocation allocation,ReturnRules rules,Clock clock){this.refunds=refunds;this.amounts=amounts;this.returns=returns;this.returnedItems=returnedItems;this.events=events;this.sales=sales;this.retail=retail;this.allocation=allocation;this.rules=rules;this.clock=clock;}
 public record Pay(@NotBlank @Size(max=36) String returnId,@NotBlank @Size(max=36) String sessionId,@NotBlank @Size(max=250) String reason,@NotBlank @Pattern(regexp="[0-9a-f]{64}") String quoteHash,@NotBlank String confirmation){}
 public record Line(long productId,String name,int quantity,BigDecimal net,BigDecimal tax,BigDecimal amount){}
 public record Quote(String returnId,String orderId,String originalReceipt,String sessionId,BigDecimal amount,BigDecimal drawerCash,boolean enoughCash,String quoteHash,List<Line> items,String message){}
 public record View(String id,String returnId,String orderId,String originalReceipt,String branchId,String cashier,String sessionId,BigDecimal amount,String reason,Instant paidAt,String method,String status,boolean copy,List<Line> items){}
 private record Context(ReturnCase returned,ReturnSaleAccess.CashSale sale){}
 private ResponseStatusException missing(){return new ResponseStatusException(NOT_FOUND,"Cash refund unavailable");}
 private void conflict(String message){throw new ResponseStatusException(CONFLICT,message);}
 private void bad(String message){throw new ResponseStatusException(BAD_REQUEST,message);}
 private String hash(List<String> parts){try{var md=MessageDigest.getInstance("SHA-256");for(String part:parts){byte[] bytes=part.getBytes(StandardCharsets.UTF_8);md.update(ByteBuffer.allocate(4).putInt(bytes.length).array());md.update(bytes);}return HexFormat.of().formatHex(md.digest());}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private void event(CashRefund refund,String actor,String action){events.save(new ReturnEvent(refund.tenant(),refund.returned(),actor,action,"Cash refund "+refund.id(),clock.instant()));}
 private Context context(PosService.Scope scope,String returned){
  var initial=returns.findByTenantAndBranchAndId(scope.tenant(),scope.branch(),returned).orElseThrow(this::missing);
  var sale=sales.cashSale(scope.tenant(),scope.branch(),initial.order());
  var record=returns.locked(scope.tenant(),scope.branch(),returned).orElseThrow(this::missing);em.refresh(record,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
  if(!"INSPECTED".equals(record.state()))conflict("Independent return approval and inspection are required first");
  rules.independent(record.requester(),scope.cashier());
  if(!sale.refundsEnabled())conflict("Cash refunds are disabled for this branch");
  if(sale.taxInvoice())conflict("Tax-invoice refunds require configured statutory credit notes");
  return new Context(record,sale);
 }
 private Quote quote(PosService.Scope scope,String returned,String session,Context context){
  if(refunds.forReturn(scope.tenant(),returned).isPresent())conflict("This return already has a paid cash refund; recover its acknowledgement");
  var prior=amounts.forOrder(scope.tenant(),context.sale().id());var paidQty=new HashMap<Long,Long>();BigDecimal refunded=BigDecimal.ZERO;
  for(var line:prior){paidQty.merge(line.product(),(long)line.quantity(),Long::sum);refunded=refunded.add(line.amount());}
  var sold=new HashMap<Long,ReturnSaleAccess.CashLine>();for(var line:context.sale().items())sold.put(line.productId(),line);
  var lines=new ArrayList<Line>();var parts=new ArrayList<>(List.of(scope.cashier(),session,returned,context.sale().id(),refunded.toPlainString()));BigDecimal total=BigDecimal.ZERO;
  for(var item:returnedItems.items(scope.tenant(),returned)){
   var original=sold.get(item.product());if(original==null)throw missing();long already=paidQty.getOrDefault(item.product(),0L);
   var allocated=allocation.line(original.price(),original.tax(),original.quantity(),already,item.quantity());
   lines.add(new Line(item.product(),item.name(),item.quantity(),allocated.net(),allocated.tax(),allocated.total()));total=total.add(allocated.total());
   parts.add(Long.toString(item.product()));parts.add(Integer.toString(item.quantity()));parts.add(Long.toString(already));parts.add(allocated.net().toPlainString());parts.add(allocated.tax().toPlainString());
  }
  if(lines.isEmpty()||total.signum()<=0||refunded.add(total).compareTo(context.sale().total())>0)conflict("No positive refundable original cash balance is available");
  parts.add(total.toPlainString());var drawer=retail.reconciliation(scope,session).expectedCash();
  return new Quote(returned,context.sale().id(),scope.branch()+"/"+context.sale().receiptNumber(),session,total,drawer,drawer.compareTo(total)>=0,hash(parts),List.copyOf(lines),"Original discounted sale and cumulative tax allocation. Recheck before handing cash; no tax credit note is generated.");
 }
 public Quote quote(String username,String returned,String session){if(session==null||session.isBlank()||session.length()>36)bad("Owned open refund session required");var scope=retail.scope(username);retail.lockOutlet(scope);retail.requireOpen(scope,session);return quote(scope,returned,session,context(scope,returned));}
 private View view(CashRefund refund,boolean copy){return new View(refund.id(),refund.returned(),refund.order(),refund.branch()+"/"+refund.receipt(),Long.toString(refund.branch()),refund.cashier(),refund.session(),refund.amount(),refund.reason(),refund.paid(),"CASH","PAID",copy,amounts.items(refund.tenant(),refund.id()).stream().map(i->new Line(i.product(),i.name(),i.quantity(),i.net(),i.tax(),i.amount())).toList());}
 private View replay(PosService.Scope scope,CashRefund refund,String hash){if(refund.branch()!=scope.branch()||!refund.cashier().equals(scope.cashier())||!refund.hash().equals(hash))conflict("Idempotency key already used for another cash refund command");return view(refund,false);}
 public View pay(String username,String clientKey,Pay request){
  if(request==null||clientKey==null||!clientKey.matches("[A-Za-z0-9_-]{8,80}")||request.returnId()==null||request.returnId().isBlank()||request.returnId().length()>36||request.sessionId()==null||request.sessionId().isBlank()||request.sessionId().length()>36||request.reason()==null||request.reason().isBlank()||request.reason().length()>250||request.quoteHash()==null||!request.quoteHash().matches("[0-9a-f]{64}")||!"CASH_HANDED_TO_BUYER".equals(request.confirmation()))bad("Exact quote, reason, cash handover confirmation and retry key required");
  var scope=retail.scope(username);String key="refund-"+hash(List.of(scope.cashier(),clientKey));String reason=request.reason().strip();String fingerprint=hash(List.of(scope.cashier(),request.returnId(),request.sessionId(),reason,request.quoteHash(),request.confirmation()));
  var prior=refunds.findByTenantAndKey(scope.tenant(),key);if(prior.isPresent())return replay(scope,prior.get(),fingerprint);
  retail.lockOutlet(scope);prior=refunds.currentKey(scope.tenant(),key);if(prior.isPresent())return replay(scope,prior.get(),fingerprint);
  retail.requireOpen(scope,request.sessionId());var context=context(scope,request.returnId());var quote=quote(scope,request.returnId(),request.sessionId(),context);
  if(!quote.quoteHash().equals(request.quoteHash()))conflict("Refund allocation changed. Reload the quote and reconcile any cash already handed over");
  if(!quote.enoughCash())conflict("Cash refund exceeds the paying drawer balance");
  String id=UUID.randomUUID().toString();var record=refunds.saveAndFlush(new CashRefund(id,scope.tenant(),scope.branch(),request.returnId(),quote.orderId(),context.sale().receiptNumber(),scope.cashier(),context.returned().requester(),request.sessionId(),key,fingerprint,quote.quoteHash(),quote.amount(),reason,clock.instant()));
  amounts.saveAllAndFlush(quote.items().stream().map(i->new CashRefundItem(scope.tenant(),id,request.returnId(),quote.orderId(),i.productId(),i.name(),i.quantity(),i.net(),i.tax(),i.amount())).toList());
  event(record,scope.cashier(),"CASH_REFUND");return view(record,false);
 }
 public View get(String username,String id){var scope=retail.scope(username);return view(refunds.findByTenantAndBranchAndId(scope.tenant(),scope.branch(),id).orElseThrow(this::missing),false);}
 public View byReturn(String username,String returned){var scope=retail.scope(username);var record=refunds.forReturn(scope.tenant(),returned).orElseThrow(this::missing);if(record.branch()!=scope.branch())throw missing();return view(record,false);}
 public List<View> list(String username){var scope=retail.scope(username);return refunds.findTop20ByTenantAndBranchOrderByPaidDescIdDesc(scope.tenant(),scope.branch()).stream().map(r->view(r,false)).toList();}
 public View copy(String username,String id){var scope=retail.scope(username);var refund=refunds.findByTenantAndBranchAndId(scope.tenant(),scope.branch(),id).orElseThrow(this::missing);event(refund,scope.cashier(),"REFUND_COPY");return view(refund,true);}
}
