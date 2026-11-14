package com.saumya.pos.returns.internal;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
public interface ReturnItemRepository extends JpaRepository<ReturnItem,ReturnItem.Key> {
 @Lock(LockModeType.PESSIMISTIC_READ) @Query("select i from ReturnItem i where i.id.tenant=:tenant and i.id.returnId=:id order by i.id.product") List<ReturnItem> items(long tenant,String id);
 @Lock(LockModeType.PESSIMISTIC_READ) @Query("select i from ReturnItem i join ReturnCase r on r.tenant=i.id.tenant and r.id=i.id.returnId where i.id.tenant=:tenant and i.orderId=:order and r.state<>'REJECTED' order by i.id.product") List<ReturnItem> used(long tenant,String order);
}
