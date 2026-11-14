package com.saumya.pos.checkout;

import com.saumya.pos.catalog.CatalogService;
import com.saumya.pos.checkout.internal.*;
import com.saumya.pos.inventory.InventoryService;
import com.saumya.pos.retail.PosService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

/**
 * Settles a reserved POS bill with several tenders in one atomic step (SRS POS-013). Non-cash tenders use the
 * explicitly gated vendor-payment simulation; no funds move. Tender rules live in {@link TenderPlan}.
 */
@Service @Transactional
public class SplitTenderService {
    private final DraftRepository orders;private final DraftLineRepository lines;private final PaymentAttemptRepository attempts;
    private final OrderTenderRepository tenders;private final PaymentPolicy policy;private final VendorPaymentAdapter adapter;
    private final CatalogService catalogue;private final InventoryService stock;private final PosService retail;private final Clock clock;
    public SplitTenderService(DraftRepository orders,DraftLineRepository lines,PaymentAttemptRepository attempts,OrderTenderRepository tenders,PaymentPolicy policy,VendorPaymentAdapter adapter,CatalogService catalogue,InventoryService stock,PosService retail,Clock clock){
        this.orders=orders;this.lines=lines;this.attempts=attempts;this.tenders=tenders;this.policy=policy;this.adapter=adapter;this.catalogue=catalogue;this.stock=stock;this.retail=retail;this.clock=clock;
    }
    /** What the caller is permitted to record. */
    public record Rights(boolean cash,boolean external) {}
    private ResponseStatusException missing(){return new ResponseStatusException(NOT_FOUND,"Draft unavailable");}
    private void conflict(String message){throw new ResponseStatusException(CONFLICT,message);}

    public Map<String,Object> pay(String username,String id,List<TenderPlan.Entry> entries,Rights rights){
        var s=retail.scope(username);retail.lockOutlet(s);
        var order=orders.lock(s.tenant(),id).orElseThrow(this::missing);
        if(!"IN_STORE".equals(order.channel())||!Objects.equals(order.branch(),s.branch())||!s.cashier().equals(order.cashier()))throw missing();
        for(var entry:entries==null?List.<TenderPlan.Entry>of():entries){
            if(entry!=null&&entry.method()==TenderPlan.Method.CASH&&!rights.cash())throw new ResponseStatusException(FORBIDDEN,"Not permitted to record cash tender");
            if(entry!=null&&entry.method()!=null&&entry.method()!=TenderPlan.Method.CASH&&!rights.external())throw new ResponseStatusException(FORBIDDEN,"Not permitted to record UPI/card tender");
        }
        if("PAID".equals(order.status()))return replay(s,order,entries);
        TenderPlan.Result plan;
        try{plan=TenderPlan.settle(order.total(),entries);}catch(TenderPlan.TenderException e){throw new ResponseStatusException(BAD_REQUEST,e.getMessage());}
        if(!plan.isSplit())throw new ResponseStatusException(BAD_REQUEST,"Split payment needs at least two tender methods; use single-tender checkout");
        policy.requireEnabled();
        if(attempts.findByTenantAndOrder(s.tenant(),id).isPresent())conflict("This draft already has a single-method payment attempt; recover that payment instead");
        retail.requireOpen(s,order.session());
        if(!"DRAFT".equals(order.status())||!order.expires().isAfter(clock.instant()))conflict("Draft is cancelled or expired; refresh/cancel it before starting another sale");
        var stored=lines.lines(s.tenant(),id);
        for(var line:stored){var p=catalogue.lockForSale(s.tenant(),line.product());
            if(p.price().compareTo(line.catalogueUnit())!=0||p.taxRate().compareTo(line.rate())!=0||p.listPrice().compareTo(line.listPrice())!=0||p.discountPercent().compareTo(line.discountPercent())!=0)conflict("Catalogue price/tax changed; cancel and recreate the draft");}
        if(!order.expires().isAfter(clock.instant()))conflict("Draft expired while validating catalogue; refresh/cancel it");

        // Confirm every non-cash tender with the vendor adapter before stock or sale state changes (all in one transaction).
        var rows=new ArrayList<OrderTender>();int number=0;
        for(var method:List.of(TenderPlan.Method.CASH,TenderPlan.Method.UPI,TenderPlan.Method.CARD)){
            BigDecimal applied=plan.applied().get(method);if(applied==null)continue;number++;
            String tenderId=UUID.randomUUID().toString();
            if(method==TenderPlan.Method.CASH){rows.add(new OrderTender(tenderId,s.tenant(),id,number,"CASH",applied,plan.cashReceived(),plan.changeGiven(),null,null,clock.instant()));continue;}
            String merchant="mock-vendor-"+s.tenant();
            var confirmation=adapter.confirmSimulation(merchant,tenderId,applied);
            if(!tenderId.equals(confirmation.attemptId())||!merchant.equals(confirmation.merchantAccount())||applied.compareTo(confirmation.amount())!=0)conflict("Payment confirmation does not match vendor, tender and amount");
            rows.add(new OrderTender(tenderId,s.tenant(),id,number,method.name(),applied,applied,BigDecimal.ZERO,merchant,confirmation.reference(),clock.instant()));
        }
        var expected=new LinkedHashMap<Long,Integer>();for(var line:stored)expected.put(line.product(),line.quantity());
        var consumed=stock.consume(s.tenant(),id);
        if(consumed.isEmpty()||!consumed.equals(expected))throw new IllegalStateException("Reservation does not match draft snapshots");
        var receipt=retail.allocateReceipt(s);boolean invoice=receipt.invoiceRequired()||order.invoiceRequested();
        order.paySplit(plan.cashReceived(),plan.changeGiven(),receipt.number(),invoice,clock.instant());orders.flush();
        tenders.saveAllAndFlush(rows);
        retail.draftDocuments(s,id,stored.stream().map(l->new PosService.StockIssue(l.product(),l.quantity())).toList(),receipt,order.total(),invoice);
        retail.draftAudit(s,"SPLIT_TENDER_PAID",id);
        return retail.receipt(s,id);
    }

    /** A retry after the sale is PAID returns the original receipt only when it asks for exactly the tenders recorded. */
    private Map<String,Object> replay(PosService.Scope s,DraftOrder order,List<TenderPlan.Entry> requested){
        if(!"SPLIT".equals(order.paymentMethod()))conflict("Sale was paid with a different tender");
        var recorded=new EnumMap<TenderPlan.Method,BigDecimal>(TenderPlan.Method.class);
        for(var t:tenders.findByTenantAndOrderRefOrderByNumber(s.tenant(),order.id()))recorded.put(TenderPlan.Method.valueOf(t.method()),t.tendered());
        var asked=new EnumMap<TenderPlan.Method,BigDecimal>(TenderPlan.Method.class);
        for(var e:requested)if(e!=null&&e.method()!=null&&e.amount()!=null)asked.merge(e.method(),e.amount(),BigDecimal::add);
        if(recorded.size()!=asked.size())conflict("Tenders differ from the completed sale");
        for(var e:recorded.entrySet()){var a=asked.get(e.getKey());if(a==null||a.compareTo(e.getValue())!=0)conflict("Tenders differ from the completed sale");}
        return retail.receipt(s,order.id());
    }
}
