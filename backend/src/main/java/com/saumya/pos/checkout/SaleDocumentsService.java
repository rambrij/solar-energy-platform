package com.saumya.pos.checkout;
import com.saumya.pos.checkout.internal.*;
import com.saumya.pos.catalog.CatalogService;
import com.saumya.pos.retail.PosService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
/** Checkout owns issued order identity and invoice persistence; retail owns outlet numbering. */
@Service
public class SaleDocumentsService {
 private final CatalogService catalog;private final InvoiceSnapshotRepository invoices;private final PosService retail;
 public SaleDocumentsService(CatalogService catalog,InvoiceSnapshotRepository invoices,PosService retail){this.catalog=catalog;this.invoices=invoices;this.retail=retail;}
 @Transactional(propagation=Propagation.MANDATORY)
 void issue(PosService.Scope scope,DraftOrder order,PosService.ReceiptAllocation allocation,boolean invoice,boolean online){
  issue(scope,scope,order,allocation,invoice,online);
 }

 /**
  * The ownership scope remains tied to the original cashier. A separately authorized
  * supervisor scope may perform the tender finalization and is the actor recorded in audit.
  */
 @Transactional(propagation=Propagation.MANDATORY)
 void issue(PosService.Scope ownerScope,PosService.Scope actorScope,DraftOrder order,
        PosService.ReceiptAllocation allocation,boolean invoice,boolean online){
  if(!"PAID".equals(order.status())||order.tenant()!=ownerScope.tenant()
      ||order.stockBranch()!=ownerScope.branch()
      ||actorScope.tenant()!=ownerScope.tenant()||actorScope.branch()!=ownerScope.branch()
      ||online!= "ONLINE".equals(order.channel())
      ||!online&&!java.util.Objects.equals(ownerScope.cashier(),order.cashier()))
      throw new IllegalStateException("Issued document context does not match paid order");
  String seller=catalog.vendor(order.tenant()).name();order.receiptIdentity(seller,allocation.outlet());
  if(invoice){
   if(invoices.findById(new InvoiceSnapshot.Key(order.tenant(),order.id())).isPresent())
    throw new IllegalStateException("Invoice is already issued");
   invoices.saveAndFlush(new InvoiceSnapshot(order.tenant(),order.id(),"INV-"+ownerScope.branch()+"-"+allocation.number(),seller,allocation.outlet(),order.total()));
   if(!online)retail.draftAudit(actorScope,"INVOICE_ISSUE",order.id());
  }
  if(online)retail.customerDraftAudit(order.tenant(),order.stockBranch(),order.customer(),"MOCK_ONLINE_PAYMENT",order.id());
  else retail.draftAudit(actorScope,"SALE_COMPLETE",order.id());
 }
}
