package com.saumya.pos.cart.internal;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface CartItemRepository extends JpaRepository<CartItem,CartItem.Key> {
    @Query("select i from CartItem i where i.id.tenantId=:tenant and i.id.cartId=:cart order by i.id.productId")
    List<CartItem> items(long tenant,String cart);
    @Query("select i from CartItem i where i.id.tenantId=:tenant and i.id.cartId=:cart and i.id.productId=:product")
    Optional<CartItem> item(long tenant,String cart,long product);
}
