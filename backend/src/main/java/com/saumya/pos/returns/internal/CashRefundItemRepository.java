package com.saumya.pos.returns.internal;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
public interface CashRefundItemRepository extends JpaRepository<CashRefundItem,CashRefundItem.Key> {
 @Lock(LockModeType.PESSIMISTIC_READ) @Query("select i from CashRefundItem i where i.id.tenant=:tenant and i.id.refund=:refund order by i.id.product") List<CashRefundItem> items(long tenant,String refund);
 @Lock(LockModeType.PESSIMISTIC_READ) @Query("select i from CashRefundItem i where i.id.tenant=:tenant and i.orderId=:order order by i.id.product") List<CashRefundItem> forOrder(long tenant,String order);
}
