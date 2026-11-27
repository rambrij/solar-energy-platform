package com.saumya.pos.warranty;

import com.saumya.pos.checkout.OrderHistoryService;
import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.shared.EventOutbox;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Immutable, tenant-owned warranty registrations anchored to paid order items. */
@Service
@Transactional
public class WarrantyService {
    private final JdbcTemplate db;
    private final AuthorizationContext authorization;
    private final OrderHistoryService orders;
    private final EventOutbox outbox;
    private final Clock clock;

    public WarrantyService(JdbcTemplate db, AuthorizationContext authorization,
            OrderHistoryService orders, EventOutbox outbox, Clock clock) {
        this.db=db; this.authorization=authorization; this.orders=orders; this.outbox=outbox; this.clock=clock;
    }

    public record Register(String orderId,long productId,LocalDate coverageStartDate,
            LocalDate coverageEndDate,String termsSummary,String reason) {}
    public record Warranty(long id,String orderId,long productId,String productName,
            Long installationId,LocalDate coverageStartDate,LocalDate coverageEndDate,
            String termsSummary,String registeredBy,Instant registeredAt) {}
    public record CustomerWarranty(long id,String orderId,String productName,Long installationId,
            LocalDate coverageStartDate,LocalDate coverageEndDate,String termsSummary,
            Instant registeredAt,String vendorName) {}
    public record EligibleOrder(String orderId,Instant orderedAt) {}
    public record EligibleItem(long productId,String productName,int quantity) {}

    private AuthorizationContext.Context vendor(String permission) {
        var actor=authorization.current();
        actor.requireVendorMembership();
        actor.requirePermission(permission);
        return actor;
    }

    @Transactional(readOnly=true)
    public List<Warranty> vendorList() {
        var actor=vendor("SERVICE_VIEW");
        return db.queryForList("""
            SELECT w.id,w.order_id,w.product_id,oi.name AS product_name,w.installation_id,
                   w.coverage_start_date,w.coverage_end_date,w.terms_summary,w.registered_by,w.registered_at
            FROM warranty_records w
            JOIN order_items oi ON oi.tenant_id=w.tenant_id AND oi.order_id=w.order_id
                              AND oi.product_id=w.product_id
            WHERE w.tenant_id=?
            ORDER BY w.registered_at DESC,w.id DESC LIMIT 200
            """,actor.tenantId()).stream().map(this::warranty).toList();
    }

    @Transactional(readOnly=true)
    public List<EligibleOrder> eligibleOrders() {
        var actor=vendor("SERVICE_CREATE");
        return db.queryForList("""
            SELECT id,created_at FROM orders
            WHERE tenant_id=? AND sales_channel='ONLINE' AND status='PAID' AND customer_id IS NOT NULL
            ORDER BY created_at DESC,id DESC LIMIT 100
            """,actor.tenantId()).stream().map(row->new EligibleOrder(
                (String)row.get("id"),instant(row.get("created_at")))).toList();
    }

    @Transactional(readOnly=true)
    public List<EligibleItem> eligibleItems(String orderId) {
        var actor=vendor("SERVICE_CREATE");
        String reference=clean(orderId,36,"Order reference");
        List<Map<String,Object>> order=db.queryForList("""
            SELECT id FROM orders WHERE tenant_id=? AND id=? AND status='PAID'
              AND sales_channel='ONLINE' AND customer_id IS NOT NULL
            """,actor.tenantId(),reference);
        if(order.isEmpty()) throw missing();
        return db.queryForList("""
            SELECT product_id,name,quantity FROM order_items
            WHERE tenant_id=? AND order_id=? ORDER BY name,product_id
            """,actor.tenantId(),reference).stream().map(row->new EligibleItem(
                ((Number)row.get("product_id")).longValue(),(String)row.get("name"),
                ((Number)row.get("quantity")).intValue())).toList();
    }

    public Warranty register(Register request) {
        var actor=vendor("SERVICE_CREATE");
        if(request==null || request.productId()<=0 || request.coverageStartDate()==null
                || request.coverageEndDate()==null) throw bad("Warranty registration details are required");
        String orderId=clean(request.orderId(),36,"Order reference");
        if(request.coverageEndDate().isBefore(request.coverageStartDate()))
            throw bad("Coverage end date must be on or after its start date");
        String terms=clean(request.termsSummary(),2000,"Warranty terms");
        String reason=clean(request.reason(),500,"Registration reason");
        Map<String,Object> order=db.queryForList("""
            SELECT status,sales_channel,customer_id FROM orders
            WHERE tenant_id=? AND id=? FOR UPDATE
            """,actor.tenantId(),orderId).stream().findFirst().orElseThrow(this::missing);
        if(!"PAID".equals(order.get("status")) || !"ONLINE".equals(order.get("sales_channel"))
                || order.get("customer_id")==null) throw missing();
        List<Map<String,Object>> item=db.queryForList("""
            SELECT name FROM order_items WHERE tenant_id=? AND order_id=? AND product_id=?
            """,actor.tenantId(),orderId,request.productId());
        if(item.isEmpty()) throw missing();
        Long installationId=db.queryForList("""
            SELECT id FROM installation_jobs WHERE tenant_id=? AND order_id=? AND status<>'CANCELLED'
            ORDER BY created_at DESC,id DESC LIMIT 1
            """,actor.tenantId(),orderId).stream().map(row->((Number)row.get("id")).longValue())
            .findFirst().orElse(null);
        Instant now=clock.instant();
        try {
            db.update("""
                INSERT INTO warranty_records(tenant_id,order_id,product_id,installation_id,
                    coverage_start_date,coverage_end_date,terms_summary,registration_reason,
                    registered_by,registered_at)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """,actor.tenantId(),orderId,request.productId(),installationId,
                Date.valueOf(request.coverageStartDate()),Date.valueOf(request.coverageEndDate()),
                terms,reason,actor.username(),Timestamp.from(now));
        } catch(org.springframework.dao.DuplicateKeyException duplicate) {
            throw conflict("Warranty coverage is already registered for this order item");
        }
        Long id=db.queryForObject("""
            SELECT id FROM warranty_records WHERE tenant_id=? AND order_id=? AND product_id=?
            """,Long.class,actor.tenantId(),orderId,request.productId());
        if(id==null) throw new IllegalStateException("Warranty registration ID was not generated");
        db.update("""
            INSERT INTO warranty_events(tenant_id,warranty_id,event_type,actor_type,actor_id,note,occurred_at)
            VALUES(?,?,'REGISTERED','VENDOR',?,?,?)
            """,actor.tenantId(),id,actor.username(),reason,Timestamp.from(now));
        outbox.append(actor.tenantId(),actor.username(),"WARRANTY",Long.toString(id),
            "WARRANTY.REGISTERED",null,Map.of("warrantyId",id,"orderId",orderId,
                "productId",request.productId(),"coverageStartDate",request.coverageStartDate().toString(),
                "coverageEndDate",request.coverageEndDate().toString(),"occurredAt",now.toString()));
        return db.queryForList("""
            SELECT w.id,w.order_id,w.product_id,oi.name AS product_name,w.installation_id,
                   w.coverage_start_date,w.coverage_end_date,w.terms_summary,w.registered_by,w.registered_at
            FROM warranty_records w
            JOIN order_items oi ON oi.tenant_id=w.tenant_id AND oi.order_id=w.order_id
                              AND oi.product_id=w.product_id
            WHERE w.tenant_id=? AND w.id=?
            """,actor.tenantId(),id).stream().map(this::warranty).findFirst()
            .orElseThrow(()->new IllegalStateException("Registered warranty could not be reloaded"));
    }

    @Transactional(readOnly=true)
    public List<CustomerWarranty> customerList(String username) {
        List<CustomerWarranty> result=new java.util.ArrayList<>();
        for(var order:orders.customerPaidOnlineOrderReferences(username)) {
            db.queryForList("""
                SELECT w.id,w.order_id,w.product_id,oi.name AS product_name,w.installation_id,
                       w.coverage_start_date,w.coverage_end_date,w.terms_summary,w.registered_by,w.registered_at
                FROM warranty_records w
                JOIN order_items oi ON oi.tenant_id=w.tenant_id AND oi.order_id=w.order_id
                                  AND oi.product_id=w.product_id
                WHERE w.tenant_id=? AND w.order_id=?
                ORDER BY w.id
                """,order.tenantId(),order.orderId()).stream()
                .map(this::warranty).map(warranty->customerWarranty(warranty,order.vendorName()))
                .forEach(result::add);
        }
        return List.copyOf(result);
    }

    @Transactional(readOnly=true)
    public CustomerWarranty customerGet(String username,long id) {
        if(id<=0) throw missing();
        for(var order:orders.customerPaidOnlineOrderReferences(username)) {
            List<Map<String,Object>> rows=db.queryForList("""
                SELECT w.id,w.order_id,w.product_id,oi.name AS product_name,w.installation_id,
                       w.coverage_start_date,w.coverage_end_date,w.terms_summary,w.registered_by,w.registered_at
                FROM warranty_records w
                JOIN order_items oi ON oi.tenant_id=w.tenant_id AND oi.order_id=w.order_id
                                  AND oi.product_id=w.product_id
                WHERE w.tenant_id=? AND w.order_id=? AND w.id=?
                """,order.tenantId(),order.orderId(),id);
            if(!rows.isEmpty()) return customerWarranty(warranty(rows.getFirst()),order.vendorName());
        }
        throw missing();
    }

    private CustomerWarranty customerWarranty(Warranty warranty,String vendorName) {
        return new CustomerWarranty(warranty.id(),warranty.orderId(),warranty.productName(),
            warranty.installationId(),warranty.coverageStartDate(),warranty.coverageEndDate(),
            warranty.termsSummary(),warranty.registeredAt(),vendorName);
    }

    private Warranty warranty(Map<String,Object> row) {
        return new Warranty(((Number)row.get("id")).longValue(),(String)row.get("order_id"),
            ((Number)row.get("product_id")).longValue(),(String)row.get("product_name"),
            row.get("installation_id")==null?null:((Number)row.get("installation_id")).longValue(),
            date(row.get("coverage_start_date")),date(row.get("coverage_end_date")),
            (String)row.get("terms_summary"),(String)row.get("registered_by"),instant(row.get("registered_at")));
    }
    private LocalDate date(Object value) {
        if(value instanceof Date date)return date.toLocalDate();
        if(value instanceof LocalDate date)return date;
        throw new IllegalStateException("Unsupported warranty date representation");
    }
    private Instant instant(Object value) {
        if(value instanceof Timestamp timestamp)return timestamp.toInstant();
        if(value instanceof Instant instant)return instant;
        if(value instanceof LocalDateTime local)return local.toInstant(ZoneOffset.UTC);
        throw new IllegalStateException("Unsupported warranty timestamp representation");
    }
    private String clean(String value,int max,String label) {
        String normalized=value==null?"":value.strip();
        if(normalized.isEmpty()||normalized.length()>max||normalized.codePoints().anyMatch(Character::isISOControl))
            throw bad(label+" is invalid");
        return normalized;
    }
    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    private ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Eligible paid order item unavailable");}
}
