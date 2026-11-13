package com.saumya.pos.checkout;

import com.saumya.pos.checkout.internal.*;
import com.saumya.pos.cart.CartService;
import com.saumya.pos.catalog.CatalogService;
import com.saumya.pos.identity.CustomerAccess;
import com.saumya.pos.inventory.InventoryService;
import com.saumya.pos.retail.PosService;
import com.saumya.pos.shared.Pricing;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service @Transactional
public class CheckoutService {
    private final DraftRepository orders;private final DraftLineRepository lines;private final CartService carts;
    private final CustomerAccess customers;private final CatalogService catalogue;private final InventoryService stock;
    private final PosService retail;private final Pricing pricing;private final Clock clock;private final long ttl;
    public CheckoutService(DraftRepository orders,DraftLineRepository lines,CartService carts,CustomerAccess customers,CatalogService catalogue,InventoryService stock,PosService retail,Pricing pricing,Clock clock,@Value("${checkout.reservation-ttl-seconds:600}") long ttl){
        if(ttl<60||ttl>3600)throw new IllegalArgumentException("Reservation TTL must be 60..3600 seconds");
        this.orders=orders;this.lines=lines;this.carts=carts;this.customers=customers;this.catalogue=catalogue;this.stock=stock;this.retail=retail;this.pricing=pricing;this.clock=clock;this.ttl=ttl;
    }
    public record Item(@Positive long productId,@Min(1) @Max(10000) int quantity) {}
    public record PosDraft(@NotBlank String sessionId,@NotEmpty @Size(max=100) List<@Valid Item> items,boolean invoiceRequested) {}
    public record OnlineDraft(@NotBlank @Size(max=36) String expectedCartId) {}
    public record Cash(@NotNull @DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal cashReceived) {}
    public record Cancel(@NotBlank @Size(max=250) String reason) {}
    public record Line(long productId,String name,int quantity,BigDecimal unitPrice,BigDecimal taxRate,BigDecimal tax,BigDecimal total,BigDecimal listPrice,BigDecimal discount) {}
    public record Draft(String id,String vendorName,String channel,String status,Instant expiresAt,BigDecimal total,List<Line> items,boolean paymentAvailable,String message) {}
    private ResponseStatusException missing(){return new ResponseStatusException(NOT_FOUND,"Draft unavailable");}
    private void conflict(String message){throw new ResponseStatusException(CONFLICT,message);}
    private String sha(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private String key(String actor,String key){if(key==null||!key.matches("[A-Za-z0-9_-]{8,80}"))throw new ResponseStatusException(BAD_REQUEST,"Valid Idempotency-Key required");return "draft-"+sha(actor+"|"+key);}
    private List<DraftLine> stored(DraftOrder order){return lines.lines(order.tenant(),order.id());}
    private Draft view(DraftOrder order){return new Draft(order.id(),catalogue.vendor(order.tenant()).name(),order.channel(),order.status(),order.expires(),order.total(),stored(order).stream().map(l->new Line(l.product(),l.name(),l.quantity(),l.price(),l.rate(),l.tax(),l.total(),l.listPrice(),l.discount())).toList(),false,
        "PAID".equals(order.status())?"Cash sale completed. Receipt is available.":"Unpaid stock reservation. Online/terminal payments are not configured; no external charge was made.");}
    private Draft replay(DraftOrder order,String hash){if(!hash.equals(order.hash()))conflict("Idempotency key already used for a different draft request");return view(order);}
    public Draft online(String username,String clientKey,OnlineDraft request){
        long customer=customers.resolve(username);customers.lock(customer);String key=key("ONLINE:"+customer,clientKey),hash=sha(request.expectedCartId());
        var prior=orders.findByCustomerIdAndKey(customer,key);if(prior.isPresent())return replay(prior.get(),hash);
        var cart=carts.cart(customer);if(!Objects.equals(cart.id(),request.expectedCartId())||cart.items().isEmpty())conflict("Cart changed or is empty; reload first");
        var vendor=catalogue.activeVendor(cart.vendorSlug());long branch=catalogue.onlineBranch(vendor.id());
        return create(vendor.id(),customer,null,null,null,"ONLINE",key,hash,branch,"ONLINE:"+customer,
            cart.items().stream().map(i->new Item(i.productId(),i.quantity())).toList(),false);
    }
    public Draft pos(String username,String clientKey,PosDraft request){
        var scope=retail.scope(username);String key=key("POS:"+scope.cashier(),clientKey);
        String hash=sha(request.sessionId()+"|"+request.items().stream().sorted(Comparator.comparingLong(Item::productId)).toList()+"|"+request.invoiceRequested());
        var prior=orders.findByTenantIdAndKey(scope.tenant(),key);if(prior.isPresent()){owned(scope,prior.get());return replay(prior.get(),hash);}
        retail.requireOpen(scope,request.sessionId());
        var result=create(scope.tenant(),null,scope.branch(),scope.cashier(),request.sessionId(),"IN_STORE",key,hash,scope.branch(),"POS:"+sha(scope.cashier()+"|"+request.sessionId()),request.items(),request.invoiceRequested());
        retail.draftAudit(scope,"DRAFT_RESERVE",result.id());return result;
    }
    private Draft create(long tenant,Long customer,Long posBranch,String cashier,String session,String channel,String key,String hash,long branch,String owner,List<Item> items,boolean invoice){
        if(items.isEmpty()||items.size()>100)throw new ResponseStatusException(BAD_REQUEST,"Draft requires 1..100 distinct products");
        if(orders.existsByTenantIdAndDraftOwner(tenant,owner))conflict("Resolve the existing reserved draft first");
        String id=UUID.randomUUID().toString();Instant expires=clock.instant().plusSeconds(ttl);BigDecimal total=BigDecimal.ZERO;
        var saved=new ArrayList<DraftLine>();var seen=new HashSet<Long>();
        for(var item:items.stream().sorted(Comparator.comparingLong(Item::productId)).toList()){
            if(!seen.add(item.productId()))conflict("Duplicate product");
            var p=catalogue.lockForSale(tenant,item.productId());var amount=pricing.line(p.price(),p.taxRate(),item.quantity());total=total.add(amount.total());
            saved.add(new DraftLine(tenant,id,p.id(),p.name(),item.quantity(),p.price(),p.taxRate(),amount.tax(),amount.total(),p.listPrice(),p.discountPercent()));
        }
        var order=orders.saveAndFlush(new DraftOrder(id,tenant,customer,posBranch,cashier,session,channel,key,hash,total,branch,owner,expires,invoice));
        lines.saveAllAndFlush(saved);
        for(var line:saved)stock.reserve(tenant,branch,line.product(),line.quantity(),id,expires);
        return view(order);
    }
    private void owned(PosService.Scope scope,DraftOrder order){if(!"IN_STORE".equals(order.channel())||!Objects.equals(order.branch(),scope.branch())||!scope.cashier().equals(order.cashier()))throw missing();}
    public List<Draft> onlineList(String username){long customer=customers.resolve(username);return orders.findTop20ByCustomerIdAndChannelOrderByExpiresAtDesc(customer,"ONLINE").stream().map(this::view).toList();}
    public List<Draft> posList(String username,String session){var s=retail.scope(username);return orders.findTop20ByTenantIdAndBranchIdAndCashierAndSessionIdAndExpiresAtIsNotNullOrderByExpiresAtDesc(s.tenant(),s.branch(),s.cashier(),session).stream().map(this::view).toList();}
    public Draft cancelOnline(String username,String id,String reason){var order=orders.lockCustomer(customers.resolve(username),id).orElseThrow(this::missing);cancel(order,reason);return view(order);}
    public Draft cancelPos(String username,String id,String reason){var s=retail.scope(username);var order=orders.lock(s.tenant(),id).orElseThrow(this::missing);owned(s,order);if(cancel(order,reason))retail.draftAudit(s,"DRAFT_CANCEL",id);return view(order);}
    private boolean cancel(DraftOrder order,String reason){if("PAID".equals(order.status()))conflict("Paid sales require the return/refund workflow");if("DRAFT".equals(order.status())){stock.release(order.tenant(),order.id());order.cancel(reason.strip(),clock.instant());orders.flush();return true;}return false;}
    public Map<String,Object> finalizeCash(String username,String id,BigDecimal cash){
        var s=retail.scope(username);retail.lockOutlet(s);var order=orders.lock(s.tenant(),id).orElseThrow(this::missing);owned(s,order);
        if("PAID".equals(order.status())){if(cash.compareTo(order.cash())!=0)conflict("Cash amount differs from completed sale");return retail.receipt(s,id);}
        retail.requireOpen(s,order.session());
        if(!"DRAFT".equals(order.status())||!order.expires().isAfter(clock.instant()))conflict("Draft is cancelled or expired; refresh/cancel it before starting another sale");
        if(cash.compareTo(order.total())<0)conflict("Cash received is below amount due");
        var stored=stored(order);
        for(var line:stored){var p=catalogue.lockForSale(s.tenant(),line.product());if(p.price().compareTo(line.price())!=0||p.taxRate().compareTo(line.rate())!=0||p.listPrice().compareTo(line.listPrice())!=0||p.discountPercent().compareTo(line.discountPercent())!=0)conflict("Catalogue price/tax changed; cancel and recreate the draft");}
        if(!order.expires().isAfter(clock.instant()))conflict("Draft expired while validating catalogue; refresh/cancel it");
        var consumed=stock.consume(s.tenant(),id);
        var expected=new LinkedHashMap<Long,Integer>();for(var line:stored)expected.put(line.product(),line.quantity());
        if(consumed.isEmpty()||!consumed.equals(expected))throw new IllegalStateException("Reservation does not match draft snapshots");
        var receipt=retail.allocateReceipt(s);boolean invoice=receipt.invoiceRequired()||order.invoiceRequested();
        order.pay(cash,receipt.number(),invoice,clock.instant());orders.flush();
        retail.draftDocuments(s,id,stored.stream().map(l->new PosService.StockIssue(l.product(),l.quantity())).toList(),receipt,order.total(),invoice);
        return retail.receipt(s,id);
    }
    public void expire(long tenant,String id){var order=orders.lock(tenant,id).orElseThrow(this::missing);if("DRAFT".equals(order.status())&&!order.expires().isAfter(clock.instant())){stock.release(tenant,id);order.finish("EXPIRED",clock.instant());orders.flush();}}
}
