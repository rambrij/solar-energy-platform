package com.saumya.pos.cart.internal;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CartRepository extends JpaRepository<ShoppingCart,String> {
    Optional<ShoppingCart> findByCustomerIdAndState(long customer,String state);
    Optional<ShoppingCart> findByIdAndCustomerIdAndState(String id,long customer,String state);
    List<ShoppingCart> findTop20ByCustomerIdAndStateOrderByIdAsc(long customer,String state);
}
