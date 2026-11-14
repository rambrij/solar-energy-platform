package com.saumya.pos.checkout;
import com.saumya.pos.checkout.internal.*;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class ReturnSaleAccess {
 @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
 private final DraftRepository orders;private final DraftLineRepository items;private final BranchReturnPolicyRepository policies;
 public ReturnSaleAccess(DraftRepository orders,DraftLineRepository items,BranchReturnPolicyRepository policies){this.orders=orders;this.items=items;this.policies=policies;}
 public record Line(long productId,String name,int quantity){}
 public record Sale(String id,Instant paidAt,int windowDays,List<Line> items){}
 public record CashLine(long productId,String name,int quantity,BigDecimal price,BigDecimal tax){}
 public record CashSale(String id,long receiptNumber,BigDecimal total,boolean taxInvoice,boolean refundsEnabled,List<CashLine> items){}
 @Transactional(propagation=Propagation.MANDATORY) public CashSale cashSale(long tenant,long branch,String id){
  var order=orders.lock(tenant,id).orElseThrow(this::missing);em.refresh(order,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
  if(!"IN_STORE".equals(order.channel())||!Objects.equals(order.branch(),branch)||!"PAID".equals(order.status())||!"CASH".equals(order.paymentMethod())||order.cash()==null||order.change()==null||order.cash().subtract(order.change()).compareTo(order.total())!=0)throw missing();
  var policy=policies.locked(tenant,branch).orElseThrow(this::missing);em.refresh(policy,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
  return new CashSale(id,order.receiptNumber(),order.total(),order.taxInvoice(),policy.cashRefunds(),items.lockedLines(tenant,id).stream().map(i->new CashLine(i.product(),i.name(),i.quantity(),i.price(),i.tax())).toList());
 }
 private ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Paid POS sale unavailable");}
 @Transactional(readOnly=true) public Sale read(long tenant,long branch,String id){return sale(tenant,branch,orders.findOne((r,q,b)->b.and(b.equal(r.get("tenantId"),tenant),b.equal(r.get("branchId"),branch),b.equal(r.get("id"),id))).orElseThrow(this::missing),false);}
 @Transactional(propagation=Propagation.MANDATORY) public Sale lock(long tenant,long branch,String id){var order=orders.lock(tenant,id).orElseThrow(this::missing);em.refresh(order,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);return sale(tenant,branch,order,true);}
 private Sale sale(long tenant,long branch,DraftOrder order,boolean locked){
  if(!"IN_STORE".equals(order.channel())||!Objects.equals(order.branch(),branch)||!"PAID".equals(order.status()))throw missing();
  var policy=(locked?policies.locked(tenant,branch):policies.findByTenantAndId(tenant,branch)).orElseThrow(this::missing);if(locked)em.refresh(policy,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);int days=policy.days();
  return new Sale(order.id(),order.changed()==null?order.created():order.changed(),days,(locked?items.lockedLines(tenant,order.id()):items.lines(tenant,order.id())).stream().map(i->new Line(i.product(),i.name(),i.quantity())).toList());
 }
}
