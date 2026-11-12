package com.saumya.pos;

import java.math.BigDecimal;
import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
public class MarketplaceService {
    private final JdbcTemplate db;
    private final Pricing pricing;
    public MarketplaceService(JdbcTemplate db,Pricing pricing){this.db=db;this.pricing=pricing;}
    public record Vendor(long id,String slug,String name) {}
    public record Product(long id,String sku,String name,BigDecimal price,BigDecimal taxRate,boolean inStock) {}
    public record Item(@NotBlank @Size(max=100) String vendorSlug,@Positive long productId,@Min(1) @Max(10000) int quantity) {}
    public record Switch(@NotBlank String expectedCartId,@NotBlank @Size(max=100) String vendorSlug,@Positive long productId,@Min(1) @Max(10000) int quantity) {}
    public record CartLine(long productId,String name,int quantity,BigDecimal unitPrice,BigDecimal taxRate,BigDecimal tax,BigDecimal total,boolean available) {}
    public record Cart(String id,String vendorSlug,String vendorName,List<CartLine> items,BigDecimal total,boolean checkoutAvailable,String message) {}
    private static final String PAYMENT_GATE="Online payment is not configured. This preview does not reserve stock or create a paid order. Contact the vendor or buy in-store.";
    public List<Vendor> vendors(){return db.query("SELECT id,slug,name FROM tenants WHERE active=TRUE AND slug IS NOT NULL ORDER BY name,id",(r,n)->new Vendor(r.getLong("id"),r.getString("slug"),r.getString("name")));}
    private Vendor vendor(String slug){return db.query("SELECT id,slug,name FROM tenants WHERE active=TRUE AND slug=?",(r,n)->new Vendor(r.getLong("id"),r.getString("slug"),r.getString("name")),slug).stream().findFirst().orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Vendor unavailable"));}
    public List<Product> products(String slug){
        Vendor v=vendor(slug);
        return db.query("SELECT p.*,COALESCE((SELECT SUM(i.quantity) FROM inventory i WHERE i.tenant_id=p.tenant_id AND i.product_id=p.id),0) AS stock FROM products p WHERE p.tenant_id=? AND p.published=TRUE ORDER BY p.id",
            (r,n)->new Product(r.getLong("id"),r.getString("sku"),r.getString("name"),r.getBigDecimal("price"),r.getBigDecimal("tax_rate"),r.getLong("stock")>0),v.id);
    }
    public long customer(String username){
        return db.query("SELECT c.id FROM customer_profiles c JOIN users u ON u.id=c.user_id WHERE u.username=? AND u.enabled=TRUE",(r,n)->r.getLong("id"),username)
            .stream().findFirst().orElseThrow(()->new ResponseStatusException(FORBIDDEN));
    }
    private void lockCustomer(long id){db.queryForObject("SELECT id FROM customer_profiles WHERE id=? FOR UPDATE",Long.class,id);}
    private Map<String,Object> active(long customer){return db.queryForList("SELECT * FROM carts WHERE customer_id=? AND state='ACTIVE'",customer).stream().findFirst().orElse(null);}
    @Transactional public Cart cart(long customer){lockCustomer(customer);return view(active(customer));}
    private Cart view(Map<String,Object> cart){
        if(cart==null)return new Cart(null,null,null,List.of(),BigDecimal.ZERO,false,PAYMENT_GATE);
        long tenant=((Number)cart.get("tenant_id")).longValue();String id=(String)cart.get("id");
        var vendor=db.queryForMap("SELECT name,slug,active FROM tenants WHERE id=?",tenant);
        var rows=db.queryForList("SELECT p.id,p.name,p.price,p.tax_rate,p.published,c.quantity,COALESCE((SELECT SUM(i.quantity) FROM inventory i WHERE i.tenant_id=p.tenant_id AND i.product_id=p.id),0) AS stock FROM cart_items c JOIN products p ON p.tenant_id=c.tenant_id AND p.id=c.product_id WHERE c.tenant_id=? AND c.cart_id=? ORDER BY p.id",tenant,id);
        var lines=new ArrayList<CartLine>();BigDecimal total=BigDecimal.ZERO;
        for(var r:rows){
            // A withdrawn product must not be exposed through an old customer cart.
            boolean published=Boolean.TRUE.equals(r.get("published"))&&Boolean.TRUE.equals(vendor.get("active"));
            int quantity=((Number)r.get("quantity")).intValue();
            var amount=pricing.line((BigDecimal)r.get("price"),(BigDecimal)r.get("tax_rate"),quantity);
            lines.add(new CartLine(((Number)r.get("id")).longValue(),published?(String)r.get("name"):"Product unavailable",quantity,
                published?(BigDecimal)r.get("price"):null,published?(BigDecimal)r.get("tax_rate"):null,published?amount.tax():null,published?amount.total():null,
                published&&((Number)r.get("stock")).longValue()>=quantity));
            if(published)total=total.add(amount.total());
        }
        return new Cart(id,(String)vendor.get("slug"),Boolean.TRUE.equals(vendor.get("active"))?(String)vendor.get("name"):"Vendor unavailable",lines,total,false,PAYMENT_GATE);
    }
    private void validateProduct(Vendor vendor,long product){
        if(db.queryForList("SELECT id FROM products WHERE tenant_id=? AND id=? AND published=TRUE",vendor.id,product).isEmpty())throw new ResponseStatusException(NOT_FOUND,"Product unavailable");
    }
    @Transactional public Cart put(long customer,Item item){
        lockCustomer(customer);Vendor vendor=vendor(item.vendorSlug);validateProduct(vendor,item.productId);
        var current=active(customer);
        if(current!=null&&((Number)current.get("tenant_id")).longValue()!=vendor.id)throw new ResponseStatusException(CONFLICT,"Your cart belongs to a different vendor; confirm a cart switch");
        if(current==null){String id=UUID.randomUUID().toString();db.update("INSERT INTO carts(id,tenant_id,customer_id,state) VALUES(?,?,?,'ACTIVE')",id,vendor.id,customer);current=active(customer);}
        String id=(String)current.get("id");
        if(db.queryForList("SELECT product_id FROM cart_items WHERE tenant_id=? AND cart_id=? AND product_id=?",vendor.id,id,item.productId).isEmpty())
            db.update("INSERT INTO cart_items VALUES(?,?,?,?)",vendor.id,id,item.productId,item.quantity);
        else db.update("UPDATE cart_items SET quantity=? WHERE tenant_id=? AND cart_id=? AND product_id=?",item.quantity,vendor.id,id,item.productId);
        return view(current);
    }
    @Transactional public Cart remove(long customer,long product){
        lockCustomer(customer);var current=active(customer);
        if(current!=null){
            db.update("DELETE FROM cart_items WHERE tenant_id=? AND cart_id=? AND product_id=?",current.get("tenant_id"),current.get("id"),product);
            if(db.queryForList("SELECT product_id FROM cart_items WHERE tenant_id=? AND cart_id=?",current.get("tenant_id"),current.get("id")).isEmpty()){
                db.update("UPDATE carts SET state='ARCHIVED' WHERE customer_id=? AND id=?",customer,current.get("id"));return view(null);
            }
        }
        return view(current);
    }
    @Transactional public Cart switchVendor(long customer,Switch request){
        lockCustomer(customer);var current=active(customer);
        if(current==null||!request.expectedCartId.equals(current.get("id")))throw new ResponseStatusException(CONFLICT,"Cart changed; reload before switching");
        Vendor vendor=vendor(request.vendorSlug);validateProduct(vendor,request.productId);
        db.update("UPDATE carts SET state='ARCHIVED' WHERE customer_id=? AND id=?",customer,current.get("id"));
        // Original items are retained in the archived vendor cart, never silently deleted.
        return put(customer,new Item(vendor.slug,request.productId,request.quantity));
    }
    @Transactional public Cart quote(long customer){
        lockCustomer(customer);var current=active(customer);Cart cart=view(current);
        if(cart.items.isEmpty()||cart.items.stream().anyMatch(item->!item.available))throw new ResponseStatusException(CONFLICT,"Cart is empty or an item is unavailable; reload current prices and stock");
        return cart;
    }
}
