package com.saumya.pos.checkout.internal;

import java.time.Instant;
import java.util.List;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
public interface SalesReportRepository extends Repository<DraftOrder,String> {
    @Query("""
        select new com.saumya.pos.checkout.internal.SalesAggregate(
          cast(coalesce(o.statusChangedAt,o.createdAt) as LocalDate),o.channel,o.status,count(o),sum(o.total),
          sum(coalesce(o.cashReceived,0)),sum(coalesce(o.changeGiven,0)),sum(case when o.taxInvoice=true then 1 else 0 end))
        from DraftOrder o where o.tenantId=:tenant
          and ((o.channel='IN_STORE' and o.branchId=:branch) or (o.channel='ONLINE' and o.stockBranch=:branch))
          and (:channel='ALL' or o.channel=:channel)
          and coalesce(o.statusChangedAt,o.createdAt)>=:from and coalesce(o.statusChangedAt,o.createdAt)<:until
        group by cast(coalesce(o.statusChangedAt,o.createdAt) as LocalDate),o.channel,o.status
        """)
    List<SalesAggregate> orders(long tenant,long branch,String channel,Instant from,Instant until);
    @Query("""
        select new com.saumya.pos.checkout.internal.SalesLineAggregate(
          cast(coalesce(o.statusChangedAt,o.createdAt) as LocalDate),o.channel,sum(l.tax),sum(l.quantity))
        from DraftLine l join DraftOrder o on o.id=l.id.order and o.tenantId=l.id.tenant
        where o.tenantId=:tenant
          and ((o.channel='IN_STORE' and o.branchId=:branch) or (o.channel='ONLINE' and o.stockBranch=:branch))
          and (:channel='ALL' or o.channel=:channel)
          and coalesce(o.statusChangedAt,o.createdAt)>=:from and coalesce(o.statusChangedAt,o.createdAt)<:until and o.status='PAID'
        group by cast(coalesce(o.statusChangedAt,o.createdAt) as LocalDate),o.channel
        """)
    List<SalesLineAggregate> lines(long tenant,long branch,String channel,Instant from,Instant until);
}
