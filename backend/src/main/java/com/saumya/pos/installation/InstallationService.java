package com.saumya.pos.installation;

import com.saumya.pos.checkout.OrderHistoryService;
import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.shared.EventOutbox;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tenant-owned installation workflow. Orders remain the commercial source of truth;
 * this module stores only the site/assignment/progress aggregate.
 */
@Service
@Transactional
public class InstallationService {
    private final JdbcTemplate db;
    private final AuthorizationContext authorization;
    private final OrderHistoryService orders;
    private final EventOutbox events;
    private final Clock clock;

    public InstallationService(JdbcTemplate db,AuthorizationContext authorization,
            OrderHistoryService orders,EventOutbox events,Clock clock) {
        this.db=db;
        this.authorization=authorization;
        this.orders=orders;
        this.events=events;
        this.clock=clock;
    }

    public record EligibleOrder(String orderId,Instant orderedAt,BigDecimal total) {}
    public record Create(String orderId,String siteAddress,Instant scheduledAt,String assignedTo,String reason) {}
    public record Assign(long expectedVersion,String assignedTo,Instant scheduledAt,String reason) {}
    public record StatusChange(long expectedVersion,String status,String note) {}
    public record Job(long id,String orderId,String siteAddress,Instant scheduledAt,String status,
            String assignedTo,Instant createdAt,Instant updatedAt,Instant completedAt,long version) {}
    public record Event(long id,String type,String status,String note,String reason,
            String actorUsername,Instant occurredAt) {}
    public record Detail(Job job,List<Event> events) {}
    public record CustomerJob(long id,String orderId,String siteAddress,Instant scheduledAt,
            String status,Instant createdAt,Instant updatedAt,Instant completedAt) {}
    public record CustomerEvent(long id,String type,String status,String note,Instant occurredAt) {}
    public record CustomerDetail(CustomerJob job,List<CustomerEvent> events) {}

    private AuthorizationContext.Context vendor(String permission) {
        var actor=authorization.current();
        actor.requireVendorMembership();
        actor.requirePermission(permission);
        return actor;
    }

    @Transactional(readOnly=true)
    public List<EligibleOrder> eligibleOrders() {
        var actor=vendor("INSTALLATION_ASSIGN");
        return db.queryForList("""
            SELECT o.id AS order_id,o.created_at,o.total
            FROM orders o
            LEFT JOIN installation_jobs j
              ON j.tenant_id=o.tenant_id AND j.order_id=o.id
            WHERE o.tenant_id=?
              AND o.sales_channel='ONLINE'
              AND o.status='PAID'
              AND o.customer_id IS NOT NULL
              AND j.id IS NULL
            ORDER BY o.created_at DESC,o.id DESC
            LIMIT 100
            """,actor.tenantId()).stream().map(row->new EligibleOrder(
                (String)row.get("order_id"),instant(row.get("created_at")),(BigDecimal)row.get("total")
            )).toList();
    }

    @Transactional(readOnly=true)
    public List<Job> vendorList() {
        var actor=vendor("INSTALLATION_VIEW");
        if(actor.hasPermission("INSTALLATION_ASSIGN")) {
            return db.queryForList("""
                SELECT id,order_id,site_address,scheduled_at,status,assigned_to,
                       created_at,updated_at,completed_at,version
                FROM installation_jobs
                WHERE tenant_id=?
                ORDER BY scheduled_at DESC,id DESC
                LIMIT 100
                """,actor.tenantId()).stream().map(this::job).toList();
        }
        return db.queryForList("""
            SELECT id,order_id,site_address,scheduled_at,status,assigned_to,
                   created_at,updated_at,completed_at,version
            FROM installation_jobs
            WHERE tenant_id=? AND assigned_to=?
            ORDER BY scheduled_at DESC,id DESC
            LIMIT 100
            """,actor.tenantId(),actor.username()).stream().map(this::job).toList();
    }

    @Transactional(readOnly=true)
    public List<String> eligibleEngineers() {
        var actor=vendor("INSTALLATION_ASSIGN");
        return db.queryForList("""
            SELECT DISTINCT e.username
            FROM employees e
            JOIN employee_role_assignments a
              ON a.tenant_id=e.tenant_id AND a.username=e.username AND a.active=TRUE
            JOIN vendor_roles r
              ON r.tenant_id=a.tenant_id AND r.id=a.role_id AND r.active=TRUE
            JOIN vendor_role_permissions p
              ON p.tenant_id=r.tenant_id AND p.role_id=r.id
             AND p.permission='INSTALLATION_VIEW'
            WHERE e.tenant_id=? AND e.enabled=TRUE
            ORDER BY e.username
            """,String.class,actor.tenantId());
    }

    public Job create(Create request) {
        var actor=vendor("INSTALLATION_ASSIGN");
        if(request==null)throw bad("Installation details are required");
        String orderId=orderId(request.orderId());
        Instant scheduled=requiredSchedule(request.scheduledAt());
        String address=clean(request.siteAddress(),1000,"Installation site address");
        String assignee=clean(request.assignedTo(),100,"Assigned engineer");
        String reason=clean(request.reason(),500,"Reason");

        Map<String,Object> order=db.queryForList("""
            SELECT customer_id,sales_channel,status
            FROM orders WHERE tenant_id=? AND id=? FOR UPDATE
            """,actor.tenantId(),orderId).stream().findFirst().orElseThrow(this::missingOrder);
        if(!"ONLINE".equals(order.get("sales_channel"))||!"PAID".equals(order.get("status"))||order.get("customer_id")==null)
            throw conflict("An installation can be created only from a paid online customer order");

        requireActiveEngineer(actor,assignee);
        Instant now=clock.instant();
        try {
            db.update("""
                INSERT INTO installation_jobs(
                    order_id,tenant_id,site_address,scheduled_at,status,assigned_to,
                    created_by,updated_by,created_at,updated_at,version)
                VALUES(?,?,?,?,'ASSIGNED',?,?,?,?,?,0)
                """,orderId,actor.tenantId(),address,Timestamp.from(scheduled),assignee,
                actor.username(),actor.username(),Timestamp.from(now),Timestamp.from(now));
        } catch(DuplicateKeyException duplicate) {
            throw conflict("This order already has an installation job");
        }
        long id=db.queryForObject("""
            SELECT id FROM installation_jobs WHERE tenant_id=? AND order_id=?
            """,Long.class,actor.tenantId(),orderId);
        append(actor,id,"CREATED","ASSIGNED","Installation job created",reason,false,now);
        append(actor,id,"ASSIGNED","ASSIGNED","Installation assigned to an engineer",reason,true,now);
        publish(actor,id,orderId,"INSTALLATION.JOB_CREATED","ASSIGNED",now);
        return findVendor(actor,id);
    }

    public Job assign(long id,Assign request) {
        var actor=vendor("INSTALLATION_ASSIGN");
        if(id<=0||request==null)throw bad("Installation ID and assignment are required");
        String assignee=clean(request.assignedTo(),100,"Assigned engineer");
        String reason=clean(request.reason(),500,"Reason");
        Instant schedule=requiredSchedule(request.scheduledAt());
        Map<String,Object> row=lockJob(actor,id);
        String current=(String)row.get("status");
        if("COMPLETED".equals(current)||"CANCELLED".equals(current))
            throw conflict("A completed or cancelled installation cannot be reassigned");
        long version=((Number)row.get("version")).longValue();
        if(version!=request.expectedVersion())throw conflict("Installation changed; reload before saving");
        requireActiveEngineer(actor,assignee);
        Instant now=clock.instant();
        int changed=db.update("""
            UPDATE installation_jobs SET assigned_to=?,scheduled_at=?,updated_by=?,
                updated_at=?,version=version+1
            WHERE tenant_id=? AND id=? AND version=?
            """,assignee,Timestamp.from(schedule),actor.username(),Timestamp.from(now),
            actor.tenantId(),id,request.expectedVersion());
        if(changed!=1)throw conflict("Installation changed; reload before saving");
        String event=instant(row.get("scheduled_at")).equals(schedule)?"ASSIGNED":"RESCHEDULED";
        append(actor,id,event,current,"Installation assignment or schedule updated",reason,true,now);
        publish(actor,id,(String)row.get("order_id"),
            "RESCHEDULED".equals(event)?"INSTALLATION.RESCHEDULED":"INSTALLATION.ASSIGNED",current,now);
        return findVendor(actor,id);
    }

    public Job updateStatus(long id,StatusChange request) {
        var actor=vendor("INSTALLATION_UPDATE");
        if(id<=0||request==null)throw bad("Installation ID and status update are required");
        String next=request.status()==null?"":request.status().strip().toUpperCase(Locale.ROOT);
        if(!List.of("IN_PROGRESS","COMPLETED","CANCELLED").contains(next))
            throw bad("Unsupported installation status");
        String note=clean(request.note(),1000,"Customer-visible progress note");
        Map<String,Object> row=lockJob(actor,id);
        String current=(String)row.get("status");
        String assignee=(String)row.get("assigned_to");
        requireJobScope(actor,assignee);
        if(((Number)row.get("version")).longValue()!=request.expectedVersion())
            throw conflict("Installation changed; reload before saving");

        String event;
        if("IN_PROGRESS".equals(next)&&"ASSIGNED".equals(current)) {
            event="STATUS_CHANGED";
        } else if("COMPLETED".equals(next)&&"IN_PROGRESS".equals(current)) {
            actor.requirePermission("INSTALLATION_COMMISSION");
            event="COMMISSIONED";
        } else if("CANCELLED".equals(next)&&List.of("ASSIGNED","IN_PROGRESS").contains(current)) {
            actor.requirePermission("INSTALLATION_ASSIGN");
            event="CANCELLED";
        } else {
            throw conflict("Invalid installation transition: "+current+" to "+next);
        }

        Instant now=clock.instant();
        int changed=db.update("""
            UPDATE installation_jobs
            SET status=?,completed_at=?,updated_by=?,updated_at=?,version=version+1
            WHERE tenant_id=? AND id=? AND version=?
            """,next,"COMPLETED".equals(next)?Timestamp.from(now):null,actor.username(),
            Timestamp.from(now),actor.tenantId(),id,request.expectedVersion());
        if(changed!=1)throw conflict("Installation changed; reload before saving");
        append(actor,id,event,next,note,note,true,now);
        String integrationType="COMPLETED".equals(next)?"INSTALLATION.COMMISSIONED"
            :"CANCELLED".equals(next)?"INSTALLATION.CANCELLED":"INSTALLATION.STATUS_CHANGED";
        publish(actor,id,(String)row.get("order_id"),integrationType,next,now);
        return findVendor(actor,id);
    }

    @Transactional(readOnly=true)
    public List<CustomerJob> customerList(String username) {
        var references=orders.customerPaidOnlineOrderReferences(username);
        List<CustomerJob> result=new ArrayList<>();
        for(var order:references) {
            result.addAll(db.queryForList("""
                SELECT id,order_id,site_address,scheduled_at,status,created_at,updated_at,completed_at
                FROM installation_jobs WHERE tenant_id=? AND order_id=?
                """,order.tenantId(),order.orderId()).stream().map(this::customerJob).toList());
            if(result.size()>=100)break;
        }
        return result.stream().sorted(Comparator.comparing(CustomerJob::scheduledAt).reversed()
            .thenComparing(Comparator.comparingLong(CustomerJob::id).reversed())).limit(100).toList();
    }

    @Transactional(readOnly=true)
    public CustomerDetail customerGet(String username,long id) {
        if(id<=0)throw missing();
        OrderHistoryService.CustomerOrderReference owner=null;
        Map<String,Object> row=null;
        for(var order:orders.customerPaidOnlineOrderReferences(username)) {
            var matches=db.queryForList("""
                SELECT id,order_id,site_address,scheduled_at,status,created_at,updated_at,completed_at
                FROM installation_jobs WHERE tenant_id=? AND order_id=? AND id=?
                """,order.tenantId(),order.orderId(),id);
            if(!matches.isEmpty()) {
                owner=order;
                row=matches.getFirst();
                break;
            }
        }
        if(owner==null||row==null)throw missing();
        return new CustomerDetail(customerJob(row),customerEvents(owner,id));
    }

    @Transactional(readOnly=true)
    public Detail vendorGet(long id) {
        var actor=vendor("INSTALLATION_VIEW");
        return new Detail(findVendor(actor,id),events(actor,id));
    }

    private Job findVendor(AuthorizationContext.Context actor,long id) {
        Map<String,Object> row=db.queryForList("""
            SELECT id,order_id,site_address,scheduled_at,status,assigned_to,
                   created_at,updated_at,completed_at,version
            FROM installation_jobs WHERE tenant_id=? AND id=?
            """,actor.tenantId(),id).stream().findFirst().orElseThrow(this::missing);
        requireJobScope(actor,(String)row.get("assigned_to"));
        return job(row);
    }

    private Map<String,Object> lockJob(AuthorizationContext.Context actor,long id) {
        return db.queryForList("""
            SELECT id,order_id,site_address,scheduled_at,status,assigned_to,
                   created_at,updated_at,completed_at,version
            FROM installation_jobs WHERE tenant_id=? AND id=? FOR UPDATE
            """,actor.tenantId(),id).stream().findFirst().orElseThrow(this::missing);
    }

    private void requireJobScope(AuthorizationContext.Context actor,String assignedTo) {
        if(!actor.hasPermission("INSTALLATION_ASSIGN")&&!Objects.equals(actor.username(),assignedTo))
            throw missing();
    }

    private void requireActiveEngineer(AuthorizationContext.Context actor,String username) {
        Boolean active=db.queryForObject("""
            SELECT COUNT(*)>0
            FROM employees e
            JOIN employee_role_assignments a
              ON a.tenant_id=e.tenant_id AND a.username=e.username AND a.active=TRUE
            JOIN vendor_roles r
              ON r.tenant_id=a.tenant_id AND r.id=a.role_id AND r.active=TRUE
            JOIN vendor_role_permissions p
              ON p.tenant_id=r.tenant_id AND p.role_id=r.id
             AND p.permission='INSTALLATION_VIEW'
            WHERE e.tenant_id=? AND e.username=? AND e.enabled=TRUE
            """,Boolean.class,actor.tenantId(),username);
        if(!Boolean.TRUE.equals(active))
            throw bad("Assigned engineer must be an enabled employee with INSTALLATION_VIEW");
    }

    private void publish(AuthorizationContext.Context actor,long installation,String orderId,
            String eventType,String status,Instant occurredAt) {
        events.append(actor.tenantId(),actor.username(),"INSTALLATION",Long.toString(installation),
            eventType,null,Map.of("installationId",installation,"orderId",orderId,
                "status",status,"occurredAt",occurredAt.toString()));
    }

    private void append(AuthorizationContext.Context actor,long installation,String type,String status,
            String note,String reason,boolean customerVisible,Instant now) {
        db.update("""
            INSERT INTO installation_events(
                installation_id,tenant_id,actor_username,event_type,status,note,reason,customer_visible,occurred_at)
            VALUES(?,?,?,?,?,?,?,?,?)
            """,installation,actor.tenantId(),actor.username(),type,status,note,reason,customerVisible,Timestamp.from(now));
    }

    private List<Event> events(AuthorizationContext.Context actor,long installation) {
        return db.queryForList("""
            SELECT id,event_type,status,note,reason,actor_username,occurred_at
            FROM installation_events WHERE tenant_id=? AND installation_id=? ORDER BY occurred_at,id
            """,actor.tenantId(),installation).stream().map(row->new Event(
                ((Number)row.get("id")).longValue(),(String)row.get("event_type"),
                (String)row.get("status"),(String)row.get("note"),(String)row.get("reason"),
                (String)row.get("actor_username"),instant(row.get("occurred_at")))).toList();
    }

    private List<CustomerEvent> customerEvents(
            OrderHistoryService.CustomerOrderReference order,long installation) {
        return db.queryForList("""
            SELECT id,event_type,status,note,occurred_at
            FROM installation_events
            WHERE tenant_id=? AND installation_id=? AND customer_visible=TRUE
            ORDER BY occurred_at,id
            """,order.tenantId(),installation).stream().map(row->new CustomerEvent(
                ((Number)row.get("id")).longValue(),(String)row.get("event_type"),
                (String)row.get("status"),(String)row.get("note"),
                instant(row.get("occurred_at")))).toList();
    }

    private Job job(Map<String,Object> row) {
        return new Job(((Number)row.get("id")).longValue(),(String)row.get("order_id"),
            (String)row.get("site_address"),instant(row.get("scheduled_at")),(String)row.get("status"),
            (String)row.get("assigned_to"),instant(row.get("created_at")),instant(row.get("updated_at")),
            row.get("completed_at")==null?null:instant(row.get("completed_at")),
            ((Number)row.get("version")).longValue());
    }

    private CustomerJob customerJob(Map<String,Object> row) {
        return new CustomerJob(((Number)row.get("id")).longValue(),(String)row.get("order_id"),
            (String)row.get("site_address"),instant(row.get("scheduled_at")),(String)row.get("status"),
            instant(row.get("created_at")),instant(row.get("updated_at")),
            row.get("completed_at")==null?null:instant(row.get("completed_at")));
    }

    private Instant instant(Object value) {
        if(value instanceof Timestamp timestamp)return timestamp.toInstant();
        if(value instanceof Instant instant)return instant;
        if(value instanceof LocalDateTime local)return local.toInstant(ZoneOffset.UTC);
        throw new IllegalStateException("Unsupported installation timestamp representation");
    }

    private String orderId(String value) {
        if(value==null||!value.matches("[A-Za-z0-9-]{1,36}"))throw missingOrder();
        return value;
    }

    private Instant requiredSchedule(Instant value) {
        if(value==null||value.isBefore(clock.instant()))
            throw bad("Installation schedule must be a future date and time");
        return value;
    }

    private String clean(String value,int max,String label) {
        String normalized=value==null?"":value.strip();
        if(normalized.isEmpty()||normalized.length()>max
                ||normalized.codePoints().anyMatch(Character::isISOControl))
            throw bad(label+" is invalid");
        return normalized;
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);
    }
    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT,message);
    }
    private ResponseStatusException missingOrder() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,"Eligible paid customer order unavailable");
    }
    private ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,"Installation unavailable");
    }
}
