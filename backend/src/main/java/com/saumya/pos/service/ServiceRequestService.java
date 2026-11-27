package com.saumya.pos.service;

import com.saumya.pos.checkout.OrderHistoryService;
import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.shared.EventOutbox;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tenant-owned service cases. The order remains the ownership anchor; the optional
 * installation link is validated against that same tenant and order.
 */
@Service
@Transactional
public class ServiceRequestService {
    private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");
    private static final Set<String> ACTIVE_ASSIGNEE_STATES = Set.of(
        "OPEN", "TRIAGED", "ASSIGNED", "IN_PROGRESS", "WAITING_CUSTOMER", "WAITING_PARTS");

    private final JdbcTemplate db;
    private final AuthorizationContext authorization;
    private final OrderHistoryService orders;
    private final EventOutbox outbox;
    private final Clock clock;

    public ServiceRequestService(JdbcTemplate db, AuthorizationContext authorization,
            OrderHistoryService orders, EventOutbox outbox, Clock clock) {
        this.db = db;
        this.authorization = authorization;
        this.orders = orders;
        this.outbox = outbox;
        this.clock = clock;
    }

    public record EligibleOrder(String orderId, String sellerName, Instant orderedAt) {}
    public record Create(String orderId, String subject, String description, String priority) {}
    public record Assign(long expectedVersion, String assignedTo, String reason) {}
    public record StatusChange(long expectedVersion, String status, String note, boolean customerVisible) {}
    public record VendorRequest(long id, String orderId, Long installationId, String subject,
            String description, String priority, String status, String assignedTo,
            Instant createdAt, Instant updatedAt, Instant resolvedAt, long version) {}
    public record VendorEvent(long id, String eventType, String fromStatus, String toStatus,
            String note, boolean customerVisible, String actorType, String actorId, Instant occurredAt) {}
    public record VendorDetail(VendorRequest serviceRequest, List<VendorEvent> events) {}
    public record CustomerRequest(long id, String orderId, Long installationId, String subject,
            String description, String priority, String status,
            Instant createdAt, Instant updatedAt, Instant resolvedAt) {}
    public record CustomerEvent(long id, String eventType, String toStatus, String note, Instant occurredAt) {}
    public record CustomerDetail(CustomerRequest serviceRequest, List<CustomerEvent> events) {}

    private AuthorizationContext.Context vendor(String permission) {
        var actor = authorization.current();
        actor.requireVendorMembership();
        actor.requirePermission(permission);
        return actor;
    }

    private AuthorizationContext.Context vendorAny(String... permissions) {
        var actor = authorization.current();
        actor.requireVendorMembership();
        if (!actor.hasAnyPermission(permissions))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A service request permission is required");
        return actor;
    }

    @Transactional(readOnly=true)
    public List<EligibleOrder> eligibleVendorOrders() {
        var actor = vendor("SERVICE_CREATE");
        return db.queryForList("""
            SELECT id,created_at
            FROM orders
            WHERE tenant_id=? AND status='PAID' AND customer_id IS NOT NULL
            ORDER BY created_at DESC,id DESC
            LIMIT 100
            """, actor.tenantId()).stream().map(row -> new EligibleOrder(
                (String) row.get("id"), "Customer order", instant(row.get("created_at")))).toList();
    }

    @Transactional(readOnly=true)
    public List<EligibleOrder> eligibleCustomerOrders(String username) {
        return orders.customerPaidOnlineOrderReferences(username).stream()
            .map(order -> new EligibleOrder(order.orderId(), order.vendorName(), null)).toList();
    }

    @Transactional(readOnly=true)
    public List<String> eligibleAssignees() {
        var actor = vendor("SERVICE_ASSIGN");
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
            """, String.class, actor.tenantId());
    }

    @Transactional(readOnly=true)
    public List<VendorRequest> vendorList() {
        var actor = vendor("SERVICE_VIEW");
        List<Map<String,Object>> rows = actor.hasPermission("SERVICE_ASSIGN")
            ? db.queryForList("""
                SELECT id,order_id,installation_id,subject,description,priority,status,assigned_to,
                       created_at,updated_at,resolved_at,version
                FROM service_requests WHERE tenant_id=?
                ORDER BY updated_at DESC,id DESC LIMIT 100
                """, actor.tenantId())
            : db.queryForList("""
                SELECT id,order_id,installation_id,subject,description,priority,status,assigned_to,
                       created_at,updated_at,resolved_at,version
                FROM service_requests WHERE tenant_id=? AND assigned_to=?
                ORDER BY updated_at DESC,id DESC LIMIT 100
                """, actor.tenantId(), actor.username());
        return rows.stream().map(this::vendorRequest).toList();
    }

    @Transactional(readOnly=true)
    public VendorDetail vendorGet(long id) {
        var actor = vendor("SERVICE_VIEW");
        VendorRequest request = findVendor(actor, id);
        return new VendorDetail(request, vendorEvents(actor, id));
    }

    public VendorRequest createForVendor(Create input) {
        var actor = vendor("SERVICE_CREATE");
        if (input == null) throw bad("Service request details are required");
        String orderId = orderId(input.orderId());
        Map<String,Object> order = db.queryForList("""
            SELECT customer_id,status
            FROM orders WHERE tenant_id=? AND id=? FOR UPDATE
            """, actor.tenantId(), orderId).stream().findFirst().orElseThrow(this::missingOrder);
        if (!"PAID".equals(order.get("status")) || order.get("customer_id") == null)
            throw missingOrder();
        return create(actor.tenantId(), actor.username(), "VENDOR", orderId, input);
    }

    public CustomerRequest createForCustomer(String username, Create input) {
        var actor = authorization.current();
        if (!actor.isCustomer() || actor.isVendorEmployee() || actor.isPlatformUser()
                || !Objects.equals(actor.username(), username))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authenticated customer identity required");
        if (input == null) throw bad("Service request details are required");
        String orderId = orderId(input.orderId());
        OrderHistoryService.CustomerOrderReference owner = orders.customerPaidOnlineOrderReferences(username)
            .stream().filter(order -> order.orderId().equals(orderId)).findFirst()
            .orElseThrow(this::missingOrder);
        Map<String,Object> row = db.queryForList("""
            SELECT status,customer_id
            FROM orders WHERE tenant_id=? AND id=?
            """, owner.tenantId(), orderId).stream().findFirst().orElseThrow(this::missingOrder);
        if (!"PAID".equals(row.get("status")) || row.get("customer_id") == null) throw missingOrder();
        return customerRequest(create(owner.tenantId(), username, "CUSTOMER", orderId, input));
    }

    private VendorRequest create(long tenantId, String actorId, String actorType, String orderId, Create input) {
        String subject = clean(input.subject(), 160, "Service request subject");
        String description = clean(input.description(), 2000, "Service request description");
        String priority = priority(input.priority());
        Long installationId = db.queryForList("""
            SELECT id FROM installation_jobs
            WHERE tenant_id=? AND order_id=? AND status<>'CANCELLED'
            ORDER BY created_at DESC,id DESC LIMIT 1
            """, tenantId, orderId).stream().map(row -> ((Number) row.get("id")).longValue())
            .findFirst().orElse(null);
        Instant now = clock.instant();
        String requestKey = UUID.randomUUID().toString();
        db.update("""
            INSERT INTO service_requests(request_key,tenant_id,order_id,installation_id,subject,description,priority,status,
                created_by,created_by_actor,updated_by,updated_by_actor,created_at,updated_at,version)
            VALUES(?,?,?,?,?,?,?,'OPEN',?,?,?,?,?,?,0)
            """, requestKey, tenantId, orderId, installationId, subject, description, priority,
            actorId, actorType, actorId, actorType, Timestamp.from(now), Timestamp.from(now));
        Long generated = db.queryForObject("""
            SELECT id FROM service_requests WHERE tenant_id=? AND request_key=?
            """, Long.class, tenantId, requestKey);
        if (generated == null) throw new IllegalStateException("Service request ID was not generated");
        long id = generated;
        appendEvent(tenantId, id, actorType, actorId, "CREATED", null, "OPEN",
            "Service request submitted", true, now);
        publish(tenantId, actorId, id, orderId, "SERVICE_REQUEST.CREATED", "OPEN", now);
        return db.queryForList("""
            SELECT id,order_id,installation_id,subject,description,priority,status,assigned_to,
                   created_at,updated_at,resolved_at,version
            FROM service_requests WHERE tenant_id=? AND id=?
            """, tenantId, id).stream().map(this::vendorRequest).findFirst().orElseThrow(this::missing);
    }

    public VendorRequest assign(long id, Assign input) {
        var actor = vendor("SERVICE_ASSIGN");
        if (id <= 0 || input == null) throw bad("Service request ID and assignment are required");
        String assignee = clean(input.assignedTo(), 100, "Assigned service employee");
        String reason = clean(input.reason(), 500, "Assignment reason");
        Map<String,Object> row = lockRequest(actor, id);
        String current = (String) row.get("status");
        if (Set.of("RESOLVED", "CLOSED").contains(current))
            throw conflict("A resolved or closed service request cannot be assigned");
        if (input.expectedVersion() != ((Number) row.get("version")).longValue())
            throw conflict("Service request changed; reload before saving");
        requireActiveAssignee(actor, assignee);
        String next = "OPEN".equals(current) || "TRIAGED".equals(current) ? "ASSIGNED" : current;
        Instant now = clock.instant();
        int changed = db.update("""
            UPDATE service_requests
            SET assigned_to=?,status=?,updated_by=?,updated_by_actor='VENDOR',
                updated_at=?,version=version+1
            WHERE tenant_id=? AND id=? AND version=?
            """, assignee, next, actor.username(), Timestamp.from(now),
            actor.tenantId(), id, input.expectedVersion());
        if (changed != 1) throw conflict("Service request changed; reload before saving");
        appendEvent(actor.tenantId(), id, "VENDOR", actor.username(), "ASSIGNED",
            current, next, reason, true, now);
        publish(actor.tenantId(), actor.username(), id, (String) row.get("order_id"),
            "SERVICE_REQUEST.ASSIGNED", next, now);
        return findVendor(actor, id);
    }

    public VendorRequest updateStatus(long id, StatusChange input) {
        var actor = vendorAny("SERVICE_UPDATE", "SERVICE_RESOLVE");
        if (id <= 0 || input == null) throw bad("Service request ID and status update are required");
        String next = input.status() == null ? "" : input.status().strip().toUpperCase(Locale.ROOT);
        String note = clean(input.note(), 1000, "Service progress note");
        Map<String,Object> row = lockRequest(actor, id);
        String current = (String) row.get("status");
        requireRequestScope(actor, (String) row.get("assigned_to"));
        if (input.expectedVersion() != ((Number) row.get("version")).longValue())
            throw conflict("Service request changed; reload before saving");
        validateTransition(actor, current, next);
        Instant now = clock.instant();
        Timestamp resolvedAt = "RESOLVED".equals(next) ? Timestamp.from(now)
            : "CLOSED".equals(next) ? (Timestamp) row.get("resolved_at") : null;
        int changed = db.update("""
            UPDATE service_requests
            SET status=?,resolved_at=?,updated_by=?,updated_by_actor='VENDOR',
                updated_at=?,version=version+1
            WHERE tenant_id=? AND id=? AND version=?
            """, next, resolvedAt, actor.username(), Timestamp.from(now),
            actor.tenantId(), id, input.expectedVersion());
        if (changed != 1) throw conflict("Service request changed; reload before saving");
        appendEvent(actor.tenantId(), id, "VENDOR", actor.username(), "STATUS_CHANGED",
            current, next, note, input.customerVisible(), now);
        publish(actor.tenantId(), actor.username(), id, (String) row.get("order_id"),
            "SERVICE_REQUEST.STATUS_CHANGED", next, now);
        return findVendor(actor, id);
    }

    @Transactional(readOnly=true)
    public List<CustomerRequest> customerList(String username) {
        List<CustomerRequest> result = new ArrayList<>();
        for (var order : orders.customerPaidOnlineOrderReferences(username)) {
            result.addAll(db.queryForList("""
                SELECT id,order_id,installation_id,subject,description,priority,status,
                       created_at,updated_at,resolved_at
                FROM service_requests WHERE tenant_id=? AND order_id=?
                """, order.tenantId(), order.orderId()).stream().map(this::customerRequest).toList());
            if (result.size() >= 100) break;
        }
        return result.stream().sorted(Comparator.comparing(CustomerRequest::updatedAt).reversed()
            .thenComparing(Comparator.comparingLong(CustomerRequest::id).reversed())).limit(100).toList();
    }

    @Transactional(readOnly=true)
    public CustomerDetail customerGet(String username, long id) {
        if (id <= 0) throw missing();
        for (var order : orders.customerPaidOnlineOrderReferences(username)) {
            var rows = db.queryForList("""
                SELECT id,order_id,installation_id,subject,description,priority,status,
                       created_at,updated_at,resolved_at
                FROM service_requests WHERE tenant_id=? AND order_id=? AND id=?
                """, order.tenantId(), order.orderId(), id);
            if (!rows.isEmpty()) {
                return new CustomerDetail(customerRequest(rows.getFirst()), customerEvents(order.tenantId(), id));
            }
        }
        throw missing();
    }

    private VendorRequest findVendor(AuthorizationContext.Context actor, long id) {
        Map<String,Object> row = db.queryForList("""
            SELECT id,order_id,installation_id,subject,description,priority,status,assigned_to,
                   created_at,updated_at,resolved_at,version
            FROM service_requests WHERE tenant_id=? AND id=?
            """, actor.tenantId(), id).stream().findFirst().orElseThrow(this::missing);
        requireRequestScope(actor, (String) row.get("assigned_to"));
        return vendorRequest(row);
    }

    private Map<String,Object> lockRequest(AuthorizationContext.Context actor, long id) {
        return db.queryForList("""
            SELECT id,order_id,installation_id,subject,description,priority,status,assigned_to,
                   created_at,updated_at,resolved_at,version
            FROM service_requests WHERE tenant_id=? AND id=? FOR UPDATE
            """, actor.tenantId(), id).stream().findFirst().orElseThrow(this::missing);
    }

    private void requireRequestScope(AuthorizationContext.Context actor, String assignedTo) {
        if (!actor.hasPermission("SERVICE_ASSIGN") && !Objects.equals(actor.username(), assignedTo))
            throw missing();
    }

    private void requireActiveAssignee(AuthorizationContext.Context actor, String username) {
        Boolean active = db.queryForObject("""
            SELECT COUNT(*)>0
            FROM employees e
            JOIN employee_role_assignments a
              ON a.tenant_id=e.tenant_id AND a.username=e.username AND a.active=TRUE
            JOIN vendor_roles r
              ON r.tenant_id=a.tenant_id AND r.id=a.role_id AND r.active=TRUE
            JOIN vendor_role_permissions p
              ON p.tenant_id=r.tenant_id AND p.role_id=r.id AND p.permission='SERVICE_VIEW'
            WHERE e.tenant_id=? AND e.username=? AND e.enabled=TRUE
            """, Boolean.class, actor.tenantId(), username);
        if (!Boolean.TRUE.equals(active))
            throw bad("Assigned service employee must be enabled and have SERVICE_VIEW");
    }

    private void validateTransition(AuthorizationContext.Context actor, String current, String next) {
        boolean allowed = switch (current) {
            case "OPEN" -> "TRIAGED".equals(next);
            case "TRIAGED" -> "IN_PROGRESS".equals(next);
            case "ASSIGNED" -> "IN_PROGRESS".equals(next);
            case "IN_PROGRESS" -> Set.of("WAITING_CUSTOMER", "WAITING_PARTS", "RESOLVED").contains(next);
            case "WAITING_CUSTOMER", "WAITING_PARTS" -> Set.of("IN_PROGRESS", "RESOLVED").contains(next);
            case "RESOLVED" -> "CLOSED".equals(next);
            default -> false;
        };
        if (!allowed) throw conflict("Invalid service request transition: " + current + " to " + next);
        if (Set.of("RESOLVED", "CLOSED").contains(next)) actor.requirePermission("SERVICE_RESOLVE");
        else actor.requirePermission("SERVICE_UPDATE");
    }

    private void appendEvent(long tenantId, long id, String actorType, String actorId,
            String eventType, String fromStatus, String toStatus, String note,
            boolean customerVisible, Instant occurredAt) {
        db.update("""
            INSERT INTO service_request_events(tenant_id,service_request_id,actor_type,actor_id,event_type,
                from_status,to_status,note,customer_visible,occurred_at)
            VALUES(?,?,?,?,?,?,?,?,?,?)
            """, tenantId, id, actorType, actorId, eventType, fromStatus,
            toStatus, note, customerVisible, Timestamp.from(occurredAt));
    }

    private List<VendorEvent> vendorEvents(AuthorizationContext.Context actor, long id) {
        return db.queryForList("""
            SELECT id,event_type,from_status,to_status,note,customer_visible,
                   actor_type,actor_id,occurred_at
            FROM service_request_events WHERE tenant_id=? AND service_request_id=?
            ORDER BY occurred_at,id
            """, actor.tenantId(), id).stream().map(row -> new VendorEvent(
                ((Number) row.get("id")).longValue(), (String) row.get("event_type"),
                (String) row.get("from_status"), (String) row.get("to_status"),
                (String) row.get("note"), Boolean.TRUE.equals(row.get("customer_visible")),
                (String) row.get("actor_type"), (String) row.get("actor_id"),
                instant(row.get("occurred_at")))).toList();
    }

    private List<CustomerEvent> customerEvents(long tenantId, long id) {
        return db.queryForList("""
            SELECT id,event_type,to_status,note,occurred_at
            FROM service_request_events
            WHERE tenant_id=? AND service_request_id=? AND customer_visible=TRUE
            ORDER BY occurred_at,id
            """, tenantId, id).stream().map(row -> new CustomerEvent(
                ((Number) row.get("id")).longValue(), (String) row.get("event_type"),
                (String) row.get("to_status"), (String) row.get("note"),
                instant(row.get("occurred_at")))).toList();
    }

    private void publish(long tenantId, String actor, long id, String orderId,
            String eventType, String status, Instant occurredAt) {
        outbox.append(tenantId, actor, "SERVICE_REQUEST", Long.toString(id), eventType, null,
            Map.of("serviceRequestId", id, "orderId", orderId,
                "status", status, "occurredAt", occurredAt.toString()));
    }

    private VendorRequest vendorRequest(Map<String,Object> row) {
        return new VendorRequest(((Number) row.get("id")).longValue(), (String) row.get("order_id"),
            row.get("installation_id") == null ? null : ((Number) row.get("installation_id")).longValue(),
            (String) row.get("subject"), (String) row.get("description"), (String) row.get("priority"),
            (String) row.get("status"), (String) row.get("assigned_to"), instant(row.get("created_at")),
            instant(row.get("updated_at")), row.get("resolved_at") == null ? null : instant(row.get("resolved_at")),
            ((Number) row.get("version")).longValue());
    }

    private CustomerRequest customerRequest(Map<String,Object> row) {
        return new CustomerRequest(((Number) row.get("id")).longValue(), (String) row.get("order_id"),
            row.get("installation_id") == null ? null : ((Number) row.get("installation_id")).longValue(),
            (String) row.get("subject"), (String) row.get("description"), (String) row.get("priority"),
            (String) row.get("status"), instant(row.get("created_at")), instant(row.get("updated_at")),
            row.get("resolved_at") == null ? null : instant(row.get("resolved_at")));
    }

    private CustomerRequest customerRequest(VendorRequest row) {
        return new CustomerRequest(row.id(), row.orderId(), row.installationId(), row.subject(),
            row.description(), row.priority(), row.status(), row.createdAt(), row.updatedAt(), row.resolvedAt());
    }

    private String priority(String value) {
        String normalized = value == null || value.isBlank() ? "NORMAL" : value.strip().toUpperCase(Locale.ROOT);
        if (!PRIORITIES.contains(normalized)) throw bad("Unsupported service request priority");
        return normalized;
    }

    private String orderId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9-]{1,36}")) throw missingOrder();
        return value;
    }

    private String clean(String value, int max, String label) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty() || normalized.length() > max
                || normalized.codePoints().anyMatch(Character::isISOControl))
            throw bad(label + " is invalid");
        return normalized;
    }

    private Instant instant(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof Instant instant) return instant;
        if (value instanceof LocalDateTime local) return local.toInstant(ZoneOffset.UTC);
        throw new IllegalStateException("Unsupported service request timestamp representation");
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
    private ResponseStatusException missingOrder() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Eligible paid customer order unavailable");
    }
    private ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Service request unavailable");
    }
}
