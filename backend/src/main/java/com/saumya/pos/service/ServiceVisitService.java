package com.saumya.pos.service;

import com.saumya.pos.checkout.OrderHistoryService;
import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.shared.EventOutbox;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Shared engineer visits for both warranty claims and general service requests.
 * The source case remains authoritative; every visit is owned by one tenant and
 * points to exactly one case type.
 */
@Service
@Transactional
public class ServiceVisitService {
    private static final Set<String> CASE_TYPES = Set.of("WARRANTY_CLAIM", "SERVICE_REQUEST");
    private final JdbcTemplate db;
    private final AuthorizationContext authorization;
    private final OrderHistoryService orders;
    private final EventOutbox outbox;
    private final Clock clock;

    public ServiceVisitService(JdbcTemplate db, AuthorizationContext authorization,
            OrderHistoryService orders, EventOutbox outbox, Clock clock) {
        this.db=db;
        this.authorization=authorization;
        this.orders=orders;
        this.outbox=outbox;
        this.clock=clock;
    }

    public record Create(String caseType,long caseId,String siteAddress,Instant scheduledAt,
            String assignedTo,String reason) {}
    public record Assignment(long expectedVersion,String assignedTo,Instant scheduledAt,String reason) {}
    public record StatusChange(long expectedVersion,String status,String note) {}
    public record VisitNote(long expectedVersion,String note,boolean customerVisible) {}
    public record VendorVisit(long id,String caseType,long caseId,String orderId,String caseSubject,
            String siteAddress,Instant scheduledAt,String status,String assignedTo,
            Instant createdAt,Instant updatedAt,Instant completedAt,long version) {}
    public record CustomerVisit(long id,String caseType,long caseId,String orderId,String caseSubject,
            String siteAddress,Instant scheduledAt,String status,Instant createdAt,
            Instant updatedAt,Instant completedAt) {}
    public record VendorEvent(long id,String eventType,String fromStatus,String toStatus,String note,
            String reason,boolean customerVisible,String actorUsername,Instant occurredAt) {}
    public record CustomerEvent(long id,String eventType,String toStatus,String note,Instant occurredAt) {}
    public record VendorDetail(VendorVisit visit,List<VendorEvent> events) {}
    public record CustomerDetail(CustomerVisit visit,List<CustomerEvent> events) {}

    private AuthorizationContext.Context vendor(String permission) {
        var actor=authorization.current();
        actor.requireVendorMembership();
        actor.requirePermission(permission);
        return actor;
    }

    private void customer(String username) {
        var actor=authorization.current();
        if(!actor.isCustomer()||actor.isVendorEmployee()||actor.isPlatformUser()
                ||!Objects.equals(actor.username(),username))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Authenticated customer identity required");
    }

    private ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,"Engineer visit unavailable");
    }
    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);
    }
    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT,message);
    }
    private String clean(String value,int max,String label) {
        String result=value==null?"":value.strip();
        if(result.isEmpty()||result.length()>max||result.codePoints().anyMatch(Character::isISOControl))
            throw bad(label+" is invalid");
        return result;
    }
    private String caseType(String value) {
        String result=value==null?"":value.strip().toUpperCase(Locale.ROOT);
        if(!CASE_TYPES.contains(result)) throw bad("Case type must be WARRANTY_CLAIM or SERVICE_REQUEST");
        return result;
    }
    private Instant requiredSchedule(Instant value) {
        if(value==null) throw bad("A scheduled date and time are required");
        return value;
    }
    private Instant instant(Object value) {
        if(value instanceof Timestamp timestamp) return timestamp.toInstant();
        if(value instanceof Instant result) return result;
        throw new IllegalStateException("Unsupported service visit timestamp");
    }

    @Transactional(readOnly=true)
    public List<String> eligibleAssignees() {
        var actor=vendor("SERVICE_ASSIGN");
        return db.queryForList("""
            SELECT DISTINCT e.username
            FROM employees e
            JOIN employee_role_assignments a
              ON a.tenant_id=e.tenant_id AND a.username=e.username AND a.active=TRUE
            JOIN vendor_roles r
              ON r.tenant_id=a.tenant_id AND r.id=a.role_id AND r.active=TRUE
            JOIN vendor_role_permissions p
              ON p.tenant_id=r.tenant_id AND p.role_id=r.id AND p.permission='SERVICE_VIEW'
            WHERE e.tenant_id=? AND e.enabled=TRUE
            ORDER BY e.username
            """,String.class,actor.tenantId());
    }

    @Transactional(readOnly=true)
    public List<VendorVisit> vendorList() {
        var actor=vendor("SERVICE_VIEW");
        List<Map<String,Object>> rows=actor.hasPermission("SERVICE_ASSIGN")
            ?db.queryForList("""
SELECT v.id,v.case_type,
               COALESCE(v.warranty_claim_id,v.service_request_id) AS case_id,
               COALESCE(wr.order_id,sr.order_id) AS order_id,
               COALESCE(wc.subject,sr.subject) AS case_subject,
               v.site_address,v.scheduled_at,v.status,v.assigned_to,
               v.created_at,v.updated_at,v.completed_at,v.version
        FROM service_visits v
        LEFT JOIN warranty_claims wc
          ON v.case_type='WARRANTY_CLAIM' AND wc.tenant_id=v.tenant_id AND wc.id=v.warranty_claim_id
        LEFT JOIN warranty_records wr
          ON wr.tenant_id=wc.tenant_id AND wr.id=wc.warranty_id
        LEFT JOIN service_requests sr
          ON v.case_type='SERVICE_REQUEST' AND sr.tenant_id=v.tenant_id AND sr.id=v.service_request_id
        WHERE v.tenant_id=?
        ORDER BY v.scheduled_at DESC,v.id DESC LIMIT 200
        """,actor.tenantId())
            :db.queryForList("""
SELECT v.id,v.case_type,
               COALESCE(v.warranty_claim_id,v.service_request_id) AS case_id,
               COALESCE(wr.order_id,sr.order_id) AS order_id,
               COALESCE(wc.subject,sr.subject) AS case_subject,
               v.site_address,v.scheduled_at,v.status,v.assigned_to,
               v.created_at,v.updated_at,v.completed_at,v.version
        FROM service_visits v
        LEFT JOIN warranty_claims wc
          ON v.case_type='WARRANTY_CLAIM' AND wc.tenant_id=v.tenant_id AND wc.id=v.warranty_claim_id
        LEFT JOIN warranty_records wr
          ON wr.tenant_id=wc.tenant_id AND wr.id=wc.warranty_id
        LEFT JOIN service_requests sr
          ON v.case_type='SERVICE_REQUEST' AND sr.tenant_id=v.tenant_id AND sr.id=v.service_request_id
        WHERE v.tenant_id=? AND v.assigned_to=?
        ORDER BY v.scheduled_at DESC,v.id DESC LIMIT 200
        """,actor.tenantId(),actor.username());
        return rows.stream().map(this::vendorVisit).toList();
    }

    @Transactional(readOnly=true)
    public VendorDetail vendorGet(long id) {
        var actor=vendor("SERVICE_VIEW");
        Map<String,Object> row=loadVendor(actor.tenantId(),id);
        requireVisitScope(actor,(String)row.get("assigned_to"));
        return new VendorDetail(vendorVisit(row),vendorEvents(actor.tenantId(),id));
    }

    public VendorVisit create(Create input) {
        var actor=vendor("SERVICE_ASSIGN");
        if(input==null||input.caseId()<=0) throw bad("Engineer visit details are required");
        String type=caseType(input.caseType());
        String address=clean(input.siteAddress(),1000,"Visit site address");
        Instant scheduled=requiredSchedule(input.scheduledAt());
        String assignee=clean(input.assignedTo(),100,"Assigned service engineer");
        String reason=clean(input.reason(),500,"Scheduling reason");

        Map<String,Object> source=lockCase(actor.tenantId(),type,input.caseId());
        String caseStatus=(String)source.get("status");
        if("WARRANTY_CLAIM".equals(type)&&Set.of("REJECTED","RESOLVED").contains(caseStatus))
            throw conflict("A rejected or resolved warranty claim cannot receive another visit");
        if("SERVICE_REQUEST".equals(type)&&Set.of("RESOLVED","CLOSED").contains(caseStatus))
            throw conflict("A resolved or closed service request cannot receive another visit");
        requireActiveAssignee(actor,assignee);

        Instant now=clock.instant();
        String visitKey=UUID.randomUUID().toString();
        if("WARRANTY_CLAIM".equals(type)) {
            db.update("""
                INSERT INTO service_visits(visit_key,tenant_id,case_type,warranty_claim_id,site_address,
                    scheduled_at,status,assigned_to,created_by,updated_by,created_at,updated_at,version)
                VALUES(?,?,'WARRANTY_CLAIM',?,?,?,'SCHEDULED',?,?,?,?,?,0)
                """,visitKey,actor.tenantId(),input.caseId(),address,Timestamp.from(scheduled),
                assignee,actor.username(),actor.username(),Timestamp.from(now),Timestamp.from(now));
        } else {
            db.update("""
                INSERT INTO service_visits(visit_key,tenant_id,case_type,service_request_id,site_address,
                    scheduled_at,status,assigned_to,created_by,updated_by,created_at,updated_at,version)
                VALUES(?,?,'SERVICE_REQUEST',?,?,?,'SCHEDULED',?,?,?,?,?,0)
                """,visitKey,actor.tenantId(),input.caseId(),address,Timestamp.from(scheduled),
                assignee,actor.username(),actor.username(),Timestamp.from(now),Timestamp.from(now));
        }
        Long id=db.queryForObject("SELECT id FROM service_visits WHERE tenant_id=? AND visit_key=?",
            Long.class,actor.tenantId(),visitKey);
        if(id==null) throw new IllegalStateException("Service visit ID was not generated");
        appendEvent(actor.tenantId(),id,actor.username(),"SCHEDULED",null,"SCHEDULED",
            "Engineer visit scheduled",reason,true,now);
        appendEvent(actor.tenantId(),id,actor.username(),"ASSIGNED","SCHEDULED","SCHEDULED",
            "Engineer assigned to visit",reason,true,now);
        publish(actor,id,type,input.caseId(),"SERVICE_VISIT.SCHEDULED","SCHEDULED",now);
        return findVendor(actor,id);
    }

    public VendorVisit assign(long id,Assignment input) {
        var actor=vendor("SERVICE_ASSIGN");
        if(id<=0||input==null) throw bad("Visit assignment details are required");
        String assignee=clean(input.assignedTo(),100,"Assigned service engineer");
        Instant scheduled=requiredSchedule(input.scheduledAt());
        String reason=clean(input.reason(),500,"Assignment reason");
        Map<String,Object> row=lockVisit(actor.tenantId(),id);
        String current=(String)row.get("status");
        if(Set.of("COMPLETED","CANCELLED").contains(current))
            throw conflict("A completed or cancelled visit cannot be reassigned");
        checkVersion(row,input.expectedVersion());
        requireActiveAssignee(actor,assignee);
        Instant oldSchedule=instant(row.get("scheduled_at"));
        String oldAssignee=(String)row.get("assigned_to");
        String type=(String)row.get("case_type");
        long caseId=caseId(row);
        Instant now=clock.instant();
        int changed=db.update("""
            UPDATE service_visits SET assigned_to=?,scheduled_at=?,updated_by=?,updated_at=?,version=version+1
            WHERE tenant_id=? AND id=? AND version=?
            """,assignee,Timestamp.from(scheduled),actor.username(),Timestamp.from(now),
            actor.tenantId(),id,input.expectedVersion());
        if(changed!=1) throw conflict("Visit changed; reload before saving");
        boolean rescheduled=!oldSchedule.equals(scheduled);
        String eventType=rescheduled?"RESCHEDULED":"ASSIGNED";
        appendEvent(actor.tenantId(),id,actor.username(),eventType,current,current,
            rescheduled?"Engineer visit rescheduled":"Engineer assignment updated",reason,true,now);
        publish(actor,id,type,caseId,rescheduled?"SERVICE_VISIT.RESCHEDULED":"SERVICE_VISIT.ASSIGNED",current,now);
        return findVendor(actor,id);
    }

    public VendorVisit updateStatus(long id,StatusChange input) {
        var actor=authorization.current();
        actor.requireVendorMembership();
        if(id<=0||input==null) throw bad("Visit status details are required");
        String next=input.status()==null?"":input.status().strip().toUpperCase(Locale.ROOT);
        String note=clean(input.note(),1000,"Visit progress note");
        Map<String,Object> row=lockVisit(actor.tenantId(),id);
        String current=(String)row.get("status");
        requireVisitScope(actor,(String)row.get("assigned_to"));
        checkVersion(row,input.expectedVersion());

        String eventType;
        if("IN_PROGRESS".equals(next)&&"SCHEDULED".equals(current)) {
            actor.requirePermission("SERVICE_UPDATE");
            eventType="STATUS_CHANGED";
        } else if("COMPLETED".equals(next)&&"IN_PROGRESS".equals(current)) {
            actor.requirePermission("SERVICE_RESOLVE");
            eventType="COMPLETED";
        } else if("CANCELLED".equals(next)&&Set.of("SCHEDULED","IN_PROGRESS").contains(current)) {
            actor.requirePermission("SERVICE_ASSIGN");
            eventType="CANCELLED";
        } else {
            throw conflict("Invalid engineer visit transition: "+current+" to "+next);
        }

        Instant now=clock.instant();
        int changed=db.update("""
            UPDATE service_visits SET status=?,completed_at=?,updated_by=?,updated_at=?,version=version+1
            WHERE tenant_id=? AND id=? AND version=?
            """,next,"COMPLETED".equals(next)?Timestamp.from(now):null,actor.username(),
            Timestamp.from(now),actor.tenantId(),id,input.expectedVersion());
        if(changed!=1) throw conflict("Visit changed; reload before saving");
        appendEvent(actor.tenantId(),id,actor.username(),eventType,current,next,note,"",true,now);
        publish(actor,id,(String)row.get("case_type"),caseId(row),"SERVICE_VISIT."+eventType,next,now);
        return findVendor(actor,id);
    }

    public VendorVisit recordNote(long id,VisitNote input) {
        var actor=vendor("SERVICE_UPDATE");
        if(id<=0||input==null) throw bad("Visit note details are required");
        String note=clean(input.note(),1000,"Visit note");
        Map<String,Object> row=lockVisit(actor.tenantId(),id);
        requireVisitScope(actor,(String)row.get("assigned_to"));
        checkVersion(row,input.expectedVersion());
        if(Set.of("COMPLETED","CANCELLED").contains(row.get("status")))
            throw conflict("Notes cannot be added to a completed or cancelled visit");
        Instant now=clock.instant();
        int changed=db.update("""
            UPDATE service_visits SET updated_by=?,updated_at=?,version=version+1
            WHERE tenant_id=? AND id=? AND version=?
            """,actor.username(),Timestamp.from(now),actor.tenantId(),id,input.expectedVersion());
        if(changed!=1) throw conflict("Visit changed; reload before saving");
        String status=(String)row.get("status");
        appendEvent(actor.tenantId(),id,actor.username(),"NOTE_RECORDED",status,status,note,"",
            input.customerVisible(),now);
        publish(actor,id,(String)row.get("case_type"),caseId(row),"SERVICE_VISIT.NOTE_RECORDED",status,now);
        return findVendor(actor,id);
    }

    @Transactional(readOnly=true)
    public List<CustomerVisit> customerList(String username) {
        customer(username);
        List<CustomerVisit> result=new ArrayList<>();
        for(var order:orders.customerPaidOnlineOrderReferences(username)) {
            result.addAll(db.queryForList("""
SELECT v.id,v.case_type,
               COALESCE(v.warranty_claim_id,v.service_request_id) AS case_id,
               COALESCE(wr.order_id,sr.order_id) AS order_id,
               COALESCE(wc.subject,sr.subject) AS case_subject,
               v.site_address,v.scheduled_at,v.status,v.assigned_to,
               v.created_at,v.updated_at,v.completed_at,v.version
        FROM service_visits v
        LEFT JOIN warranty_claims wc
          ON v.case_type='WARRANTY_CLAIM' AND wc.tenant_id=v.tenant_id AND wc.id=v.warranty_claim_id
        LEFT JOIN warranty_records wr
          ON wr.tenant_id=wc.tenant_id AND wr.id=wc.warranty_id
        LEFT JOIN service_requests sr
          ON v.case_type='SERVICE_REQUEST' AND sr.tenant_id=v.tenant_id AND sr.id=v.service_request_id
        WHERE v.tenant_id=? AND ((v.case_type='WARRANTY_CLAIM' AND wr.order_id=?)
                  OR (v.case_type='SERVICE_REQUEST' AND sr.order_id=?))
        ORDER BY v.scheduled_at DESC,v.id DESC
        """,order.tenantId(),order.orderId(),order.orderId()).stream()
                .map(this::customerVisit).toList());
        }
        return result.stream().collect(java.util.stream.Collectors.toMap(
            CustomerVisit::id,v->v,(first,ignored)->first))
            .values().stream().sorted(Comparator.comparing(CustomerVisit::scheduledAt).reversed()
                .thenComparing(Comparator.comparingLong(CustomerVisit::id).reversed())).limit(200).toList();
    }

    @Transactional(readOnly=true)
    public CustomerDetail customerGet(String username,long id) {
        customer(username);
        if(id<=0) throw missing();
        for(var order:orders.customerPaidOnlineOrderReferences(username)) {
            var rows=db.queryForList("""
SELECT v.id,v.case_type,
               COALESCE(v.warranty_claim_id,v.service_request_id) AS case_id,
               COALESCE(wr.order_id,sr.order_id) AS order_id,
               COALESCE(wc.subject,sr.subject) AS case_subject,
               v.site_address,v.scheduled_at,v.status,v.assigned_to,
               v.created_at,v.updated_at,v.completed_at,v.version
        FROM service_visits v
        LEFT JOIN warranty_claims wc
          ON v.case_type='WARRANTY_CLAIM' AND wc.tenant_id=v.tenant_id AND wc.id=v.warranty_claim_id
        LEFT JOIN warranty_records wr
          ON wr.tenant_id=wc.tenant_id AND wr.id=wc.warranty_id
        LEFT JOIN service_requests sr
          ON v.case_type='SERVICE_REQUEST' AND sr.tenant_id=v.tenant_id AND sr.id=v.service_request_id
        WHERE v.tenant_id=? AND v.id=? AND ((v.case_type='WARRANTY_CLAIM' AND wr.order_id=?)
                  OR (v.case_type='SERVICE_REQUEST' AND sr.order_id=?))
        
        """,order.tenantId(),id,order.orderId(),order.orderId());
            if(!rows.isEmpty()) return new CustomerDetail(customerVisit(rows.getFirst()),
                customerEvents(order.tenantId(),id));
        }
        throw missing();
    }

    private Map<String,Object> lockCase(long tenantId,String type,long caseId) {
        if("WARRANTY_CLAIM".equals(type)) {
            return db.queryForList("""
                SELECT c.id,c.status,w.order_id
                FROM warranty_claims c
                JOIN warranty_records w ON w.tenant_id=c.tenant_id AND w.id=c.warranty_id
                WHERE c.tenant_id=? AND c.id=? FOR UPDATE
                """,tenantId,caseId).stream().findFirst().orElseThrow(this::missing);
        }
        return db.queryForList("""
            SELECT id,status,order_id FROM service_requests
            WHERE tenant_id=? AND id=? FOR UPDATE
            """,tenantId,caseId).stream().findFirst().orElseThrow(this::missing);
    }

    private void requireActiveAssignee(AuthorizationContext.Context actor,String username) {
        Boolean active=db.queryForObject("""
            SELECT COUNT(*)>0
            FROM employees e
            JOIN employee_role_assignments a
              ON a.tenant_id=e.tenant_id AND a.username=e.username AND a.active=TRUE
            JOIN vendor_roles r
              ON r.tenant_id=a.tenant_id AND r.id=a.role_id AND r.active=TRUE
            JOIN vendor_role_permissions p
              ON p.tenant_id=r.tenant_id AND p.role_id=r.id AND p.permission='SERVICE_VIEW'
            WHERE e.tenant_id=? AND e.username=? AND e.enabled=TRUE
            """,Boolean.class,actor.tenantId(),username);
        if(!Boolean.TRUE.equals(active))
            throw bad("Assigned engineer must be an enabled tenant employee with SERVICE_VIEW");
    }

    private void requireVisitScope(AuthorizationContext.Context actor,String assignedTo) {
        if(!actor.hasPermission("SERVICE_ASSIGN")&&!Objects.equals(actor.username(),assignedTo))
            throw missing();
    }

    private void checkVersion(Map<String,Object> row,long expected) {
        if(expected!=((Number)row.get("version")).longValue())
            throw conflict("Visit changed; reload before saving");
    }

    private long caseId(Map<String,Object> row) {
        Object value=row.get("warranty_claim_id")!=null?row.get("warranty_claim_id"):row.get("service_request_id");
        return ((Number)value).longValue();
    }

    private Map<String,Object> lockVisit(long tenantId,long id) {
        return db.queryForList("""
            SELECT id,tenant_id,case_type,warranty_claim_id,service_request_id,status,
                   assigned_to,scheduled_at,version
            FROM service_visits WHERE tenant_id=? AND id=? FOR UPDATE
            """,tenantId,id).stream().findFirst().orElseThrow(this::missing);
    }

    private Map<String,Object> loadVendor(long tenantId,long id) {
        return db.queryForList("""
SELECT v.id,v.case_type,
               COALESCE(v.warranty_claim_id,v.service_request_id) AS case_id,
               COALESCE(wr.order_id,sr.order_id) AS order_id,
               COALESCE(wc.subject,sr.subject) AS case_subject,
               v.site_address,v.scheduled_at,v.status,v.assigned_to,
               v.created_at,v.updated_at,v.completed_at,v.version
        FROM service_visits v
        LEFT JOIN warranty_claims wc
          ON v.case_type='WARRANTY_CLAIM' AND wc.tenant_id=v.tenant_id AND wc.id=v.warranty_claim_id
        LEFT JOIN warranty_records wr
          ON wr.tenant_id=wc.tenant_id AND wr.id=wc.warranty_id
        LEFT JOIN service_requests sr
          ON v.case_type='SERVICE_REQUEST' AND sr.tenant_id=v.tenant_id AND sr.id=v.service_request_id
        WHERE v.tenant_id=? AND v.id=?
        
        """,tenantId,id)
            .stream().findFirst().orElseThrow(this::missing);
    }

    private VendorVisit findVendor(AuthorizationContext.Context actor,long id) {
        Map<String,Object> row=loadVendor(actor.tenantId(),id);
        requireVisitScope(actor,(String)row.get("assigned_to"));
        return vendorVisit(row);
    }

    private void appendEvent(long tenantId,long id,String actor,String eventType,String fromStatus,
            String toStatus,String note,String reason,boolean customerVisible,Instant occurredAt) {
        db.update("""
            INSERT INTO service_visit_events(tenant_id,service_visit_id,actor_username,event_type,
                from_status,to_status,note,reason,customer_visible,occurred_at)
            VALUES(?,?,?,?,?,?,?,?,?,?)
            """,tenantId,id,actor,eventType,fromStatus,toStatus,note,reason,customerVisible,Timestamp.from(occurredAt));
    }

    private List<VendorEvent> vendorEvents(long tenantId,long id) {
        return db.queryForList("""
            SELECT id,event_type,from_status,to_status,note,reason,customer_visible,actor_username,occurred_at
            FROM service_visit_events WHERE tenant_id=? AND service_visit_id=? ORDER BY occurred_at,id
            """,tenantId,id).stream().map(row->new VendorEvent(
                ((Number)row.get("id")).longValue(),(String)row.get("event_type"),
                (String)row.get("from_status"),(String)row.get("to_status"),(String)row.get("note"),
                (String)row.get("reason"),Boolean.TRUE.equals(row.get("customer_visible")),
                (String)row.get("actor_username"),instant(row.get("occurred_at")))).toList();
    }

    private List<CustomerEvent> customerEvents(long tenantId,long id) {
        return db.queryForList("""
            SELECT id,event_type,to_status,note,occurred_at
            FROM service_visit_events
            WHERE tenant_id=? AND service_visit_id=? AND customer_visible=TRUE
            ORDER BY occurred_at,id
            """,tenantId,id).stream().map(row->new CustomerEvent(
                ((Number)row.get("id")).longValue(),(String)row.get("event_type"),
                (String)row.get("to_status"),(String)row.get("note"),instant(row.get("occurred_at")))).toList();
    }

    private void publish(AuthorizationContext.Context actor,long id,String caseType,long caseId,
            String eventType,String status,Instant occurredAt) {
        publish(actor.tenantId(),actor.username(),id,caseType,caseId,eventType,status,occurredAt);
    }

    private void publish(Long tenantId,String actor,long id,String caseType,long caseId,
            String eventType,String status,Instant occurredAt) {
        outbox.append(tenantId,actor,"SERVICE_VISIT",Long.toString(id),eventType,null,
            Map.of("serviceVisitId",id,"caseType",caseType,"caseId",caseId,
                "status",status,"occurredAt",occurredAt.toString()));
    }

    private VendorVisit vendorVisit(Map<String,Object> row) {
        return new VendorVisit(((Number)row.get("id")).longValue(),(String)row.get("case_type"),
            ((Number)row.get("case_id")).longValue(),(String)row.get("order_id"),
            (String)row.get("case_subject"),(String)row.get("site_address"),
            instant(row.get("scheduled_at")),(String)row.get("status"),(String)row.get("assigned_to"),
            instant(row.get("created_at")),instant(row.get("updated_at")),
            row.get("completed_at")==null?null:instant(row.get("completed_at")),
            ((Number)row.get("version")).longValue());
    }

    private CustomerVisit customerVisit(Map<String,Object> row) {
        return new CustomerVisit(((Number)row.get("id")).longValue(),(String)row.get("case_type"),
            ((Number)row.get("case_id")).longValue(),(String)row.get("order_id"),
            (String)row.get("case_subject"),(String)row.get("site_address"),
            instant(row.get("scheduled_at")),(String)row.get("status"),instant(row.get("created_at")),
            instant(row.get("updated_at")),row.get("completed_at")==null?null:instant(row.get("completed_at")));
    }
}
