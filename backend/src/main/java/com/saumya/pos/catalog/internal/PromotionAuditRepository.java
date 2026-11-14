package com.saumya.pos.catalog.internal;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import com.saumya.pos.catalog.PromotionHistoryService.Change;
public interface PromotionAuditRepository extends JpaRepository<PromotionAudit,String>{
 @Query(value="select new com.saumya.pos.catalog.PromotionHistoryService$Change(a.id,a.previous,a.next,a.actor,a.reason,a.created) from PromotionAudit a where a.tenant=:tenant and a.product=:product order by a.created desc,a.id desc",
 countQuery="select count(a) from PromotionAudit a where a.tenant=:tenant and a.product=:product")
 Page<Change> history(long tenant,long product,Pageable page);
}
