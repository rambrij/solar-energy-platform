package com.saumya.pos.checkout;
import com.saumya.pos.checkout.internal.*;
import com.saumya.pos.retail.PosService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.NOT_FOUND;
@Service @Transactional
public class CheckoutReceiptService {
 private final DraftRepository orders;private final DraftLineRepository lines;private final InvoiceSnapshotRepository invoices;private final PaymentAttemptRepository tenders;private final BuyerDetailsService buyers;private final PosService retail;
 @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
 public CheckoutReceiptService(DraftRepository orders,DraftLineRepository lines,InvoiceSnapshotRepository invoices,PaymentAttemptRepository tenders,BuyerDetailsService buyers,PosService retail){this.orders=orders;this.lines=lines;this.invoices=invoices;this.tenders=tenders;this.buyers=buyers;this.retail=retail;}
 private ResponseStatusException missing(){return new ResponseStatusException(NOT_FOUND,"Receipt unavailable");}
 private Map<String,Object> fields(Object...pairs){var value=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)value.put((String)pairs[i],pairs[i+1]);return value;}
 private java.sql.Timestamp timestamp(java.time.Instant value){return value==null?null:java.sql.Timestamp.from(value);}
    
    @PreAuthorize("hasAuthority('POS_RECEIPT_VIEW')")
    public Map<String, Object> receipt(PosService.Scope requested, String id) {
        if (requested == null || id == null || !id.matches("[A-Za-z0-9-]{1,36}")) throw missing();
        var scope = retail.scope();
        retail.authorize(scope,scope.cashier(),"POS_RECEIPT_VIEW");
        retail.requireAssignedRegisterScope(scope,"POS_RECEIPT_VIEW");
        if (scope.tenant() != requested.tenant() || scope.branch() != requested.branch()) throw missing();
        var order = orders.lock(scope.tenant(), id).orElseThrow(this::missing);
        em.refresh(order, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
  if(!scope.cashier().equals(order.cashier()))
      retail.authorize(scope,scope.cashier(),"POS_RECEIPT_VIEW","POS_TENDER_MANUAL_VERIFY");
  if(!"PAID".equals(order.status())||!"IN_STORE".equals(order.channel())||!Objects.equals(order.branch(),scope.branch()))throw missing();
        retail.requireAssignedResourceSession(scope,order.cashier(),order.session(),"POS_RECEIPT_VIEW");
  var receipt=fields("id",order.id(),"branch_id",order.branch(),"sales_channel",order.channel(),"customer_id",order.customer(),"total",order.total(),"cash_received",order.cash(),"change_given",order.change(),"payment_method",order.paymentMethod(),"payment_reference",order.paymentReference(),"receipt_number",order.receiptNumber(),"tax_invoice",order.taxInvoice(),"created_at",timestamp(order.created()),"issued_at",timestamp(order.changed()==null?order.created():order.changed()),"receipt_seller_name",order.receiptSeller(),"receipt_outlet_name",order.receiptOutlet());
  receipt.put("items",lines.lockedLines(order.tenant(),id).stream().map(line->fields("name",line.name(),"quantity",line.quantity(),"price",line.price(),"tax_rate",line.rate(),"tax",line.tax(),"total",line.total(),"list_price",line.listPrice(),"discount",line.discount(),"discount_percent",line.discountPercent())).toList());
  var invoice=invoices.findById(new InvoiceSnapshot.Key(order.tenant(),id));invoice.ifPresent(row->em.refresh(row,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE));receipt.put("invoices",invoice.map(row->List.of(fields("invoice_number",row.number(),"seller_name",row.seller(),"outlet_name",row.outlet(),"total",row.total(),"issued_at",timestamp(row.issuedAt())))).orElseGet(List::of));
  var confirmed=tenders.findAllByTenantAndOrderOrderByCreatedAscIdAsc(order.tenant(),id).stream().filter(row->"CONFIRMED".equals(row.state())).map(row->new PaymentCheckoutService.ReceiptTender(row.method(),row.amount(),row.cash(),row.change(),row.reference())).toList();if(!confirmed.isEmpty())receipt.put("tenders",confirmed);
  var buyer=buyers.snapshot(order.tenant(),id);if(buyer!=null){receipt.put("buyer",buyer);retail.draftAudit(scope,"BUYER_SNAPSHOT_VIEW",id);}return receipt;
 }
 @PreAuthorize("hasAuthority('POS_RECEIPT_VIEW') and hasAuthority('POS_RECEIPT_REPRINT')")
 public Map<String,Object> reprint(PosService.Scope requested,String id){
  var current=retail.scope();
  retail.authorize(current,current.cashier(),"POS_RECEIPT_VIEW","POS_RECEIPT_REPRINT");
  retail.requireAssignedRegisterScope(current,"POS_RECEIPT_REPRINT");
  if(requested==null||requested.tenant()!=current.tenant()||requested.branch()!=current.branch())throw missing();
  var receipt=receipt(current,id);
  retail.draftAudit(current,"RECEIPT_REPRINT",id);
  receipt.put("copy",true);return receipt;
 }
}
