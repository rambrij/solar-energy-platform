package com.saumya.pos.inventory.internal;
import java.util.List;
import org.springframework.data.jpa.repository.*;
public interface ReservationRepository extends JpaRepository<StockReservation,StockReservation.Key> {
    @Query("select r from StockReservation r where r.id.tenantId=:tenant and r.id.orderId=:order and r.state='HELD' order by r.id.productId")
    List<StockReservation> held(long tenant,String order);
}
