package com.saumya.pos.checkout;

import com.saumya.pos.checkout.internal.*;
import com.saumya.pos.identity.CustomerAccess;
import com.saumya.pos.retail.PosService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class OnlineReceiptService {
 private final DraftRepository orders;private final DraftLineRepository lines;private final InvoiceSnapshotRepository invoices;private final CustomerAccess customers;private final PosService retail;
 public OnlineReceiptService(DraftRepository orders,DraftLineRepository lines,InvoiceSnapshotRepository invoices,CustomerAccess customers,PosService retail){this.orders=orders;this.lines=lines;this.invoices=invoices;this.customers=customers;this.retail=retail;}
 public record Line(long productId,String name,int quantity,BigDecimal unitPrice,BigDecimal taxRate,BigDecimal discount,BigDecimal tax,BigDecimal total){}
 public record Invoice(String number,String seller,String outlet,BigDecimal total){}
 public record Receipt(String orderId,String seller,String outlet,Instant issuedAt,BigDecimal total,String paymentMethod,List<Line> items,List<Invoice> invoices,boolean copy){}
 private ResponseStatusException missing(){return new ResponseStatusException(NOT_FOUND,"Online receipt unavailable");}
 private DraftOrder owned(String username,String id){
  if(id==null||!id.matches("[A-Za-z0-9-]{1,36}"))throw missing();
  long customer=customers.resolve(username);var order=orders.lockCustomer(customer,id).orElseThrow(this::missing);
  if(!"ONLINE".equals(order.channel())||!"PAID".equals(order.status()))throw missing();
  return order;
 }
 private Receipt view(DraftOrder order,boolean copy){
  var items=lines.lines(order.tenant(),order.id()).stream().map(l->new Line(l.product(),l.name(),l.quantity(),l.price(),l.rate(),l.discount(),l.tax(),l.total())).toList();
  var saved=invoices.findById(new InvoiceSnapshot.Key(order.tenant(),order.id())).map(i->List.of(new Invoice(i.number(),i.seller(),i.outlet(),i.total()))).orElseGet(List::of);
  return new Receipt(order.id(),order.receiptSeller(),order.receiptOutlet(),order.changed()==null?order.created():order.changed(),order.total(),order.paymentMethod(),items,saved,copy);
 }
 public Receipt read(String username,String id){return view(owned(username,id),false);}
 public Receipt copy(String username,String id){var order=owned(username,id);var receipt=view(order,true);retail.draftAudit(new PosService.Scope(order.tenant(),order.stockBranch(),"customer:"+order.customer()),"CUSTOMER_RECEIPT_COPY",id);return receipt;}
}
