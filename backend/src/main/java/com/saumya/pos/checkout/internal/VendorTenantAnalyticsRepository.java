package com.saumya.pos.checkout.internal;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Independent tenant-wide reporting aggregates. Orders, lines and tenders are queried separately. */
public interface VendorTenantAnalyticsRepository extends Repository<DraftOrder, String> {
    @Query("""
        select new com.saumya.pos.checkout.internal.SalesAggregate(
          cast(coalesce(o.statusChangedAt,o.createdAt) as LocalDate),o.channel,o.status,count(o),sum(o.total),
          sum(coalesce(o.cashReceived,0)),sum(coalesce(o.changeGiven,0)),
          sum(case when o.taxInvoice=true then 1 else 0 end))
        from DraftOrder o
        where o.tenantId=:tenant
          and coalesce(o.statusChangedAt,o.createdAt)>=:fromAt
          and coalesce(o.statusChangedAt,o.createdAt)<:untilAt
        group by cast(coalesce(o.statusChangedAt,o.createdAt) as LocalDate),o.channel,o.status
        """)
    List<SalesAggregate> ordersForTenant(
        @Param("tenant") long tenant, @Param("fromAt") Instant fromAt, @Param("untilAt") Instant untilAt);

    @Query("""
        select new com.saumya.pos.checkout.internal.SalesLineAggregate(
          cast(coalesce(o.statusChangedAt,o.createdAt) as LocalDate),o.channel,
          sum(l.tax),sum(l.quantity),sum(l.discount))
        from DraftLine l join DraftOrder o on o.id=l.id.order and o.tenantId=l.id.tenant
        where o.tenantId=:tenant
          and o.status='PAID'
          and coalesce(o.statusChangedAt,o.createdAt)>=:fromAt
          and coalesce(o.statusChangedAt,o.createdAt)<:untilAt
        group by cast(coalesce(o.statusChangedAt,o.createdAt) as LocalDate),o.channel
        """)
    List<SalesLineAggregate> linesForTenant(
        @Param("tenant") long tenant, @Param("fromAt") Instant fromAt, @Param("untilAt") Instant untilAt);

    @Query("""
        select new com.saumya.pos.checkout.internal.VendorTenantOutletAggregate(
          coalesce(o.receiptOutlet,o.channel),o.channel,count(o),
          sum(case when o.status='PAID' then 1 else 0 end),
          sum(case when o.status='PAID' then o.total else 0 end),
          sum(case when o.status='DRAFT' then 1 else 0 end),
          sum(case when o.status='DRAFT' then o.total else 0 end))
        from DraftOrder o
        where o.tenantId=:tenant
          and coalesce(o.statusChangedAt,o.createdAt)>=:fromAt
          and coalesce(o.statusChangedAt,o.createdAt)<:untilAt
        group by coalesce(o.receiptOutlet,o.channel),o.channel
        order by sum(case when o.status='PAID' then o.total else 0 end) desc
        """)
    List<VendorTenantOutletAggregate> outletsForTenant(
        @Param("tenant") long tenant, @Param("fromAt") Instant fromAt, @Param("untilAt") Instant untilAt);

    @Query(value = """
        select new com.saumya.pos.checkout.internal.VendorTenantProductAggregate(
          l.id.product,l.name,sum(l.quantity),sum(l.total),sum(l.discount))
        from DraftLine l join DraftOrder o on o.id=l.id.order and o.tenantId=l.id.tenant
        where o.tenantId=:tenant
          and o.status='PAID'
          and coalesce(o.statusChangedAt,o.createdAt)>=:fromAt
          and coalesce(o.statusChangedAt,o.createdAt)<:untilAt
        group by l.id.product,l.name
        order by sum(l.total) desc
        """,
        countQuery = """
        select count(distinct l.id.product)
        from DraftLine l join DraftOrder o on o.id=l.id.order and o.tenantId=l.id.tenant
        where o.tenantId=:tenant
          and o.status='PAID'
          and coalesce(o.statusChangedAt,o.createdAt)>=:fromAt
          and coalesce(o.statusChangedAt,o.createdAt)<:untilAt
        """)
    Page<VendorTenantProductAggregate> topProductsForTenant(
        @Param("tenant") long tenant, @Param("fromAt") Instant fromAt,
        @Param("untilAt") Instant untilAt, Pageable pageable);

    @Query("""
        select new com.saumya.pos.checkout.internal.VendorTenantTenderAggregate(
          o.channel,a.method,a.origin,count(a),sum(a.amount))
        from PaymentAttempt a join DraftOrder o on o.id=a.order and o.tenantId=a.tenant
        where a.tenant=:tenant
          and a.state='CONFIRMED'
          and a.confirmed>=:fromAt and a.confirmed<:untilAt
        group by o.channel,a.method,a.origin
        order by o.channel,a.method,a.origin
        """)
    List<VendorTenantTenderAggregate> confirmedTendersForTenant(
        @Param("tenant") long tenant, @Param("fromAt") Instant fromAt, @Param("untilAt") Instant untilAt);

    @Query("""
        select count(distinct o.customerId)
        from DraftOrder o
        where o.tenantId=:tenant and o.status='PAID' and o.customerId is not null
          and coalesce(o.statusChangedAt,o.createdAt)>=:fromAt
          and coalesce(o.statusChangedAt,o.createdAt)<:untilAt
        """)
    long distinctPaidCustomersForTenant(
        @Param("tenant") long tenant, @Param("fromAt") Instant fromAt, @Param("untilAt") Instant untilAt);
}
