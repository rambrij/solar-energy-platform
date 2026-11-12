package com.saumya.pos.cart;

import com.saumya.pos.cart.internal.*;
import com.saumya.pos.catalog.CatalogService;
import com.saumya.pos.identity.CustomerAccess;
import com.saumya.pos.inventory.InventoryService;
import com.saumya.pos.shared.Pricing;
import java.math.BigDecimal;
import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service @Transactional
public class CartService {
    private final CartRepository carts; private final CartItemRepository items;
    private final CustomerAccess customers; private final CatalogService catalogue; private final InventoryService stock; private final Pricing pricing;
    public CartService(CartRepository carts,CartItemRepository items,CustomerAccess customers,CatalogService catalogue,InventoryService stock,Pricing pricing){
        this.carts=carts;this.items=items;this.customers=customers;this.catalogue=catalogue;this.stock=stock;this.pricing=pricing;
    }
    public record Item(@NotBlank @Size(max=100) String vendorSlug,@Positive long productId,@Min(1) @Max(10000) int quantity) {}
    public record Switch(@NotBlank String expectedCartId,@NotBlank @Size(max=100) String vendorSlug,@Positive long productId,@Min(1) @Max(10000) int quantity) {}
    public record Restore(@NotBlank @Size(max=36) String archivedCartId,@Size(max=36) String expectedCartId) {}
    public record CartLine(long productId,String name,int quantity,BigDecimal unitPrice,BigDecimal taxRate,BigDecimal tax,BigDecimal total,boolean available) {}
    public record Cart(String id,String vendorSlug,String vendorName,List<CartLine> items,BigDecimal total,boolean checkoutAvailable,String message) {}
    private static final String PAYMENT_GATE="Online payment is not configured. This preview does not reserve stock or create a paid order. Contact the vendor or buy in-store.";
    private ShoppingCart active(long customer){return carts.findByCustomerIdAndState(customer,"ACTIVE").orElse(null);}
    public Cart cart(long customer){customers.lock(customer);return view(active(customer));}
    private Cart view(ShoppingCart cart){
        if(cart==null)return new Cart(null,null,null,List.of(),BigDecimal.ZERO,false,PAYMENT_GATE);
        var vendor=catalogue.vendor(cart.tenantId());var lines=new ArrayList<CartLine>();BigDecimal total=BigDecimal.ZERO;
        for(var item:items.items(cart.tenantId(),cart.id())){
            var product=catalogue.product(cart.tenantId(),item.productId());boolean published=vendor.active()&&product.published();
            var amount=pricing.line(product.price(),product.taxRate(),item.quantity());
            lines.add(new CartLine(product.id(),published?product.name():"Product unavailable",item.quantity(),published?product.price():null,
                published?product.taxRate():null,published?amount.tax():null,published?amount.total():null,
                published&&stock.available(cart.tenantId(),product.id())>=item.quantity()));
            if(published)total=total.add(amount.total());
        }
        return new Cart(cart.id(),vendor.slug(),vendor.active()?vendor.name():"Vendor unavailable",lines,total,false,PAYMENT_GATE);
    }
    public Cart put(long customer,Item request){
        customers.lock(customer);var vendor=catalogue.activeVendor(request.vendorSlug());catalogue.published(vendor.id(),request.productId());
        var current=active(customer);
        if(current!=null&&current.tenantId()!=vendor.id())throw new ResponseStatusException(CONFLICT,"Confirm a vendor cart switch");
        if(current==null)current=carts.saveAndFlush(new ShoppingCart(UUID.randomUUID().toString(),vendor.id(),customer));
        var key=new CartItem.Key(vendor.id(),current.id(),request.productId());
        var item=items.findById(key).orElseGet(()->new CartItem(key,request.quantity()));item.quantity(request.quantity());items.saveAndFlush(item);
        return view(current);
    }
    public Cart remove(long customer,long product){
        customers.lock(customer);var current=active(customer);
        if(current!=null){items.item(current.tenantId(),current.id(),product).ifPresent(items::delete);items.flush();
            if(items.items(current.tenantId(),current.id()).isEmpty()){current.archive();carts.flush();return view(null);}}
        return view(current);
    }
    public Cart switchVendor(long customer,Switch request){
        customers.lock(customer);var current=active(customer);
        if(current==null||!request.expectedCartId().equals(current.id()))throw new ResponseStatusException(CONFLICT,"Cart changed; reload before switching");
        var vendor=catalogue.activeVendor(request.vendorSlug());catalogue.published(vendor.id(),request.productId());
        current.archive();carts.flush();return put(customer,new Item(vendor.slug(),request.productId(),request.quantity()));
    }
    public Cart quote(long customer){
        customers.lock(customer);Cart cart=view(active(customer));
        if(cart.items().isEmpty()||cart.items().stream().anyMatch(item->!item.available()))throw new ResponseStatusException(CONFLICT,"Cart is empty or unavailable");
        return cart;
    }
    public List<Cart> history(long customer){
        customers.lock(customer);return carts.findTop20ByCustomerIdAndStateOrderByIdAsc(customer,"ARCHIVED").stream().map(this::view).toList();
    }
    public Cart restore(long customer,Restore request){
        customers.lock(customer);var current=active(customer);
        if(!Objects.equals(current==null?null:current.id(),request.expectedCartId()))throw new ResponseStatusException(CONFLICT,"Active cart changed; reload before restoring");
        var archived=carts.findByIdAndCustomerIdAndState(request.archivedCartId(),customer,"ARCHIVED")
            .orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Archived cart unavailable"));
        var vendor=catalogue.vendor(archived.tenantId());if(!vendor.active())throw new ResponseStatusException(CONFLICT,"Vendor unavailable");
        var stored=items.items(archived.tenantId(),archived.id());
        if(stored.isEmpty())throw new ResponseStatusException(CONFLICT,"Archived cart is empty");
        for(var item:stored)catalogue.published(archived.tenantId(),item.productId());
        // Release the unique active-customer slot first, within the same transaction.
        if(current!=null){current.archive();carts.flush();}
        archived.activate();carts.flush();return view(archived);
    }
}
