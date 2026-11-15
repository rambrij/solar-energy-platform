package com.saumya.pos.checkout;
import com.saumya.pos.checkout.internal.*;
import com.saumya.pos.catalog.CatalogService;
import com.saumya.pos.identity.CustomerAccess;
import com.saumya.pos.retail.PosService;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service @Transactional
public class DraftRecoveryService {
 @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
 private final DraftRepository orders;private final DraftLineRepository lines;private final CustomerAccess customers;private final PosService retail;private final CatalogService catalog;private final DraftRecoveryPolicy policy;private final Clock clock;
 public DraftRecoveryService(DraftRepository orders,DraftLineRepository lines,CustomerAccess customers,PosService retail,CatalogService catalog,DraftRecoveryPolicy policy,Clock clock){this.orders=orders;this.lines=lines;this.customers=customers;this.retail=retail;this.catalog=catalog;this.policy=policy;this.clock=clock;}
 public record Recovery(String id,String vendorName,String channel,String status,Instant expiresAt,BigDecimal total,List<CheckoutService.Line> items,String sessionId,boolean expired,String message){}
 private ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Draft unavailable");}
 private void validate(String id){if(id==null||id.isBlank()||id.length()>36)throw missing();}
 public Recovery pos(String username,String id){validate(id);var scope=retail.scope(username);return view(orders.recoverPos(scope.tenant(),scope.branch(),scope.cashier(),id).orElseThrow(this::missing));}
 public Recovery customer(String username,String id){validate(id);return view(orders.recoverCustomer(customers.resolve(username),id).orElseThrow(this::missing));}
 private Recovery view(DraftOrder order){
  em.refresh(order,jakarta.persistence.LockModeType.PESSIMISTIC_READ);
  boolean expired=policy.expired(order.status(),order.expires(),clock.instant());
  return new Recovery(order.id(),catalog.vendor(order.tenant()).name(),order.channel(),order.status(),order.expires(),order.total(),lines.lockedLines(order.tenant(),order.id()).stream().map(l->new CheckoutService.Line(l.product(),l.name(),l.quantity(),l.price(),l.rate(),l.tax(),l.total(),l.listPrice(),l.discount())).toList(),order.session(),expired,policy.message(order.status(),expired));
 }
}
