package com.saumya.pos.checkout.internal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.repository.Repository;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.Query;
public interface CashierSalesRepository extends Repository<DraftOrder,String> {
 @Query(value="""
  select new com.saumya.pos.checkout.internal.CashierSalesHeader(o.cashier,count(o),sum(o.total),sum(coalesce(o.cashReceived,0)),sum(coalesce(o.changeGiven,0)),sum(case when o.taxInvoice=true then 1 else 0 end))
  from DraftOrder o where o.tenantId=:tenant and o.branchId=:branch and o.channel='IN_STORE' and o.status='PAID'
  and coalesce(o.statusChangedAt,o.createdAt)>=:from and coalesce(o.statusChangedAt,o.createdAt)<:until
  group by o.cashier order by o.cashier
  """,countQuery="""
  select count(distinct o.cashier) from DraftOrder o where o.tenantId=:tenant and o.branchId=:branch and o.channel='IN_STORE' and o.status='PAID'
  and coalesce(o.statusChangedAt,o.createdAt)>=:from and coalesce(o.statusChangedAt,o.createdAt)<:until
  """)
 Page<CashierSalesHeader> headers(long tenant,long branch,Instant from,Instant until,Pageable pageable);
 @Query("""
  select new com.saumya.pos.checkout.internal.CashierSalesLines(o.cashier,sum(l.tax),sum(l.quantity),sum(l.discount))
  from DraftLine l join DraftOrder o on o.id=l.id.order and o.tenantId=l.id.tenant
  where o.tenantId=:tenant and o.branchId=:branch and o.channel='IN_STORE' and o.status='PAID' and o.cashier in :cashiers
  and coalesce(o.statusChangedAt,o.createdAt)>=:from and coalesce(o.statusChangedAt,o.createdAt)<:until
  group by o.cashier
  """)
 List<CashierSalesLines> lines(long tenant,long branch,Instant from,Instant until,List<String> cashiers);
}
