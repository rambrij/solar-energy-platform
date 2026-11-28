package com.saumya.pos.checkout.internal;

import java.sql.Timestamp;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Tenant-scoped projections over the immutable invoice and payment snapshots owned by checkout. */
public interface VendorInvoiceRegisterRepository extends Repository<InvoiceSnapshot, InvoiceSnapshot.Key> {
    @Query(value = "SELECT vi.order_id, vi.invoice_number, vi.seller_name, vi.outlet_name, vi.total, vi.issued_at, o.sales_channel, o.payment_method, o.payment_reference, COALESCE(pay.confirmed_amount, 0) AS confirmed_amount FROM vendor_invoices vi JOIN orders o ON o.tenant_id=vi.tenant_id AND o.id=vi.order_id LEFT JOIN (SELECT tenant_id, order_id, SUM(CASE WHEN state='CONFIRMED' THEN amount ELSE 0 END) AS confirmed_amount FROM vendor_payment_attempts GROUP BY tenant_id, order_id) pay ON pay.tenant_id=vi.tenant_id AND pay.order_id=vi.order_id WHERE vi.tenant_id=:tenant AND vi.issued_at>=:fromAt AND vi.issued_at<:untilAt AND o.sales_channel=COALESCE(NULLIF(:channel,'ALL'),o.sales_channel) ORDER BY vi.issued_at DESC, vi.invoice_number DESC LIMIT :limit OFFSET :offset", nativeQuery = true)
    @SuppressWarnings("rawtypes")
    List page(
        @Param("tenant") long tenant,
        @Param("fromAt") Timestamp fromAt,
        @Param("untilAt") Timestamp untilAt,
        @Param("channel") String channel,
        @Param("limit") int limit,
        @Param("offset") int offset);

    @Query(value = "SELECT COUNT(*), COALESCE(SUM(vi.total),0), COALESCE(SUM(COALESCE(pay.confirmed_amount,0)),0), COALESCE(SUM(CASE WHEN o.sales_channel='ONLINE' THEN 1 ELSE 0 END),0), COALESCE(SUM(CASE WHEN o.sales_channel='IN_STORE' THEN 1 ELSE 0 END),0) FROM vendor_invoices vi JOIN orders o ON o.tenant_id=vi.tenant_id AND o.id=vi.order_id LEFT JOIN (SELECT tenant_id, order_id, SUM(CASE WHEN state='CONFIRMED' THEN amount ELSE 0 END) AS confirmed_amount FROM vendor_payment_attempts GROUP BY tenant_id, order_id) pay ON pay.tenant_id=vi.tenant_id AND pay.order_id=vi.order_id WHERE vi.tenant_id=:tenant AND vi.issued_at>=:fromAt AND vi.issued_at<:untilAt AND o.sales_channel=COALESCE(NULLIF(:channel,'ALL'),o.sales_channel)", nativeQuery = true)
    @SuppressWarnings("rawtypes")
    List totals(
        @Param("tenant") long tenant,
        @Param("fromAt") Timestamp fromAt,
        @Param("untilAt") Timestamp untilAt,
        @Param("channel") String channel);
}
