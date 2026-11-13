package com.saumya.pos.returns.internal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
public interface RefundReportRepository extends Repository<CashRefund,String> {
 @Query("""
  select new com.saumya.pos.returns.internal.RefundHeaderAggregate(cast(r.paid as LocalDate),r.cashier,count(r),sum(r.amount))
  from CashRefund r where r.tenant=:tenant and r.branch=:branch and r.paid>=:from and r.paid<:until
  group by cast(r.paid as LocalDate),r.cashier
  """)
 List<RefundHeaderAggregate> headers(long tenant,long branch,Instant from,Instant until);
 @Query("""
  select new com.saumya.pos.returns.internal.RefundLineAggregate(cast(r.paid as LocalDate),r.cashier,sum(i.net),sum(i.tax),sum(i.quantity))
  from CashRefundItem i join CashRefund r on r.id=i.id.refund and r.tenant=i.id.tenant
  where r.tenant=:tenant and r.branch=:branch and r.paid>=:from and r.paid<:until
  group by cast(r.paid as LocalDate),r.cashier
  """)
 List<RefundLineAggregate> lines(long tenant,long branch,Instant from,Instant until);
}
