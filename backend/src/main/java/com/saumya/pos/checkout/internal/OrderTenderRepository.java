package com.saumya.pos.checkout.internal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OrderTenderRepository extends JpaRepository<OrderTender,String> {
    List<OrderTender> findByTenantAndOrderRefOrderByNumber(long tenant,String orderRef);
}
