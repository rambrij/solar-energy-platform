package com.saumya.pos.cart.internal;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface CartRepository extends JpaRepository<ShoppingCart,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ShoppingCart c where c.customerId=:customer and c.state=:state")
    Optional<ShoppingCart> findByCustomerIdAndState(@Param("customer") long customer,@Param("state") String state);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ShoppingCart c where c.id=:id and c.customerId=:customer and c.state=:state")
    Optional<ShoppingCart> findByIdAndCustomerIdAndState(@Param("id") String id,@Param("customer") long customer,@Param("state") String state);
    List<ShoppingCart> findTop20ByCustomerIdAndStateOrderByIdAsc(long customer,String state);
}
