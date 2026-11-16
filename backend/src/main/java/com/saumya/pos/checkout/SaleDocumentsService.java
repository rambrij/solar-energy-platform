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
  if(!"PAID".equals(order.status())||order.tenant()!=scope.tenant()||order.stockBranch()!=scope.branch()||online!= "ONLINE".equals(order.channel())||!online&&!java.util.Objects.equals(scope.cashier(),order.cashier()))throw new IllegalStateException("Issued document context does not match paid order");
  String seller=catalog.vendor(order.tenant()).name();order.receiptIdentity(seller,allocation.outlet());
  if(invoice){if(invoices.findById(new InvoiceSnapshot.Key(order.tenant(),order.id())).isPresent())throw new IllegalStateException("Invoice is already issued");invoices.saveAndFlush(new InvoiceSnapshot(order.tenant(),order.id(),"INV-"+scope.branch()+"-"+allocation.number(),seller,allocation.outlet(),order.total()));if(!online)retail.draftAudit(scope,"INVOICE_ISSUE",order.id());}
  retail.draftAudit(scope,online?"MOCK_ONLINE_PAYMENT":"SALE_COMPLETE",order.id());
 }
}
