package com.saumya.pos.cart.internal;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
@Entity @Table(name="cart_items")
public class CartItem {
    @Embeddable public static class Key implements Serializable {
        @Column(name="tenant_id") private long tenantId;
        @Column(name="cart_id",length=36) private String cartId;
        @Column(name="product_id") private long productId;
        public Key() {}
        public Key(long tenant,String cart,long product){tenantId=tenant;cartId=cart;productId=product;}
        public long productId(){return productId;}
        @Override public boolean equals(Object o){return o instanceof Key k&&tenantId==k.tenantId&&productId==k.productId&&Objects.equals(cartId,k.cartId);}
        @Override public int hashCode(){return Objects.hash(tenantId,cartId,productId);}
    }
    @EmbeddedId private Key id;
    @Column(nullable=false) private int quantity;
    protected CartItem() {}
    public CartItem(Key id,int quantity){this.id=id;this.quantity=quantity;}
    public long productId(){return id.productId();} public int quantity(){return quantity;}
    public void quantity(int value){quantity=value;}
}
