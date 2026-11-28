package com.saumya.pos.service;

import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.shared.EventOutbox;
import com.saumya.pos.tenant.TenantLifecycleService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Tenant-owned escalation rules, durable escalation queue, and acknowledgement history. */
@Service
@Transactional
public class ServiceSlaEscalationService {
    private static final Set<String> TYPES = Set.of("SERVICE_REQUEST", "WARRANTY_CLAIM");
    private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");
    private static final Set<String> BREACHES = Set.of("RESPONSE", "RESOLUTION");
    private static final Set<String> RECIPIENT_ROLES = Set.of("VENDOR_ADMIN", "SERVICE_MANAGER", "SERVICE_LEAD");
    private final JdbcTemplate db;
    private final AuthorizationContext authorization;
    private final EventOutbox outbox;
    private final Clock clock;

    public ServiceSlaEscalationService(JdbcTemplate db, AuthorizationContext authorization,
            EventOutbox outbox, Clock clock) {
        this.db = db; this.authorization = authorization; this.outbox = outbox; this.clock = clock;
    }

    public record Policy(Long id, String caseType, String priority, String breachType,
            int escalationLevel, int afterMinutes, String recipientRoleKey,
            boolean enabled, long version) {}
    public record PolicyUpdate(String caseType, String priority, String breachType,
            int escalationLevel, int afterMinutes, String recipientRoleKey, boolean enabled, String reason) {}
    public record Escalation(long id, long slaId, String caseType, long caseId,
            String breachType, int escalationLevel, String recipientRoleKey,
            String status, Instant escalatedAt, String acknowledgedBy,
            Instant acknowledgedAt, String acknowledgementNote) {}
    public record Acknowledge(String note) {}

    private AuthorizationContext.Context actor(String permission) {
        var current = authorization.current();
        current.requireVendorMembership();
        current.requirePermission(permission);
        return current;
    }
    private String normalize(String value, Set<String> allowed, String label) {
        String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported " + label);
        return normalized;
    }
    private String role(String value) {
        String normalized = normalize(value, RECIPIENT_ROLES, "escalation recipient role");
        return normalized;
    }
    private String note(String value) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty() || normalized.length() > 500
                || normalized.codePoints().anyMatch(Character::isISOControl))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A note of at most 500 characters is required");
        return normalized;
    }
    private static long number(Object value) { return ((Number)value).longValue(); }
    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp t) return t.toInstant();
        if (value instanceof Instant i) return i;
        throw new IllegalStateException("Unsupported escalation timestamp");
    }

    @Transactional(readOnly = true)
    public List<Policy> policies() {
        var current = actor("SERVICE_VIEW");
        return db.query("""
            SELECT id,case_type,priority,breach_type,escalation_level,after_minutes,
                   recipient_role_key,enabled,version
            FROM service_sla_escalation_policies WHERE tenant_id=?
            ORDER BY case_type,priority,breach_type,escalation_level
            """, (rs, n) -> new Policy(rs.getLong("id"), rs.getString("case_type"),
                rs.getString("priority"), rs.getString("breach_type"), rs.getInt("escalation_level"),
                rs.getInt("after_minutes"), rs.getString("recipient_role_key"),
                rs.getBoolean("enabled"), rs.getLong("version")), current.tenantId());
    }

    public Policy savePolicy(PolicyUpdate input) {
        var current = actor("SERVICE_ASSIGN");
        if (input == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escalation policy is required");
        String type = normalize(input.caseType(), TYPES, "case type");
        String priority = normalize(input.priority(), PRIORITIES, "priority");
        String breach = normalize(input.breachType(), BREACHES, "breach type");
        String recipient = role(input.recipientRoleKey());
        String reason = note(input.reason());
        if ("WARRANTY_CLAIM".equals(type) && !"NORMAL".equals(priority))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Warranty claims currently use NORMAL priority");
        if (input.escalationLevel() < 1 || input.escalationLevel() > 5
                || input.afterMinutes() < 0 || input.afterMinutes() > 525600)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escalation level must be 1-5 and delay 0-525600 minutes");
        Instant now = clock.instant();
        List<Map<String,Object>> existing = db.queryForList("""
            SELECT id,version,after_minutes,recipient_role_key,enabled FROM service_sla_escalation_policies
            WHERE tenant_id=? AND case_type=? AND priority=? AND breach_type=? AND escalation_level=? FOR UPDATE
            """, current.tenantId(), type, priority, breach, input.escalationLevel());
        if (existing.isEmpty()) {
            db.update("""
                INSERT INTO service_sla_escalation_policies(tenant_id,case_type,priority,breach_type,
                    escalation_level,after_minutes,recipient_role_key,enabled,created_by,updated_by,created_at,updated_at,updated_reason,version)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,0)
                """, current.tenantId(), type, priority, breach, input.escalationLevel(),
                input.afterMinutes(), recipient, input.enabled(), current.username(), current.username(),
                Timestamp.from(now), Timestamp.from(now), reason);
        } else {
            db.update("""
                UPDATE service_sla_escalation_policies SET after_minutes=?,recipient_role_key=?,
                    enabled=?,updated_by=?,updated_at=?,updated_reason=?,version=version+1
                WHERE tenant_id=? AND id=?
                """, input.afterMinutes(), recipient, input.enabled(), current.username(),
                Timestamp.from(now), reason, current.tenantId(), existing.getFirst().get("id"));
        }
        Long auditPolicyId = db.queryForObject("""
            SELECT id FROM service_sla_escalation_policies
            WHERE tenant_id=? AND case_type=? AND priority=? AND breach_type=? AND escalation_level=?
            """, Long.class, current.tenantId(), type, priority, breach, input.escalationLevel());
        if (auditPolicyId == null) throw new IllegalStateException("Escalation policy ID was not generated");
        String changeType = existing.isEmpty() ? "CREATED" : "UPDATED";
        Map<String,Object> previous = existing.isEmpty() ? Map.of() : existing.getFirst();
        db.update("""
            INSERT INTO service_sla_escalation_policy_audit(tenant_id,policy_id,case_type,priority,
                breach_type,escalation_level,change_type,previous_after_minutes,
                previous_recipient_role_key,previous_enabled,after_minutes,recipient_role_key,
                enabled,actor_username,reason,occurred_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, current.tenantId(), auditPolicyId, type, priority, breach, input.escalationLevel(),
            changeType, previous.get("after_minutes"), previous.get("recipient_role_key"),
            previous.get("enabled"), input.afterMinutes(), recipient, input.enabled(),
            current.username(), reason, Timestamp.from(now));
        outbox.append(current.tenantId(), current.username(), "SERVICE_SLA_ESCALATION_POLICY",
            type + ":" + priority + ":" + breach + ":" + input.escalationLevel(),
            "SERVICE_SLA_ESCALATION_POLICY.SAVED", null,
            Map.of("caseType", type, "priority", priority, "breachType", breach,
                "level", input.escalationLevel(), "afterMinutes", input.afterMinutes(),
                "recipientRoleKey", recipient, "enabled", input.enabled(), "reason", reason));
        Long policyId = db.queryForObject("""
            SELECT id FROM service_sla_escalation_policies
            WHERE tenant_id=? AND case_type=? AND priority=? AND breach_type=? AND escalation_level=?
            """, Long.class, current.tenantId(), type, priority, breach, input.escalationLevel());
        long version = existing.isEmpty() ? 0 : ((Number) existing.getFirst().get("version")).longValue() + 1;
        return new Policy(policyId, type, priority, breach, input.escalationLevel(),
            input.afterMinutes(), recipient, input.enabled(), version);
    }

    /** Idempotent scheduled pass. Unique escalation identity prevents duplicate notifications across workers. */
    public void processDueEscalationsForTenant(TenantLifecycleService.TenantSummary tenant, Instant now) {
        List<Map<String,Object>> breaches = db.queryForList("""
            SELECT s.id,s.case_type,s.service_request_id,s.warranty_claim_id,s.priority,
                   s.response_breached_at,s.resolution_breached_at
            FROM service_case_sla s
            WHERE s.tenant_id=? AND (
              (s.response_breached_at IS NOT NULL AND s.first_response_at IS NULL)
              OR (s.resolution_breached_at IS NOT NULL AND s.closed_at IS NULL))
            ORDER BY s.id LIMIT 200
            """, tenant.id());
        for (Map<String,Object> row : breaches) {
            String type = (String) row.get("case_type");
            String priority = (String) row.get("priority");
            long slaId = number(row.get("id"));
            Long rawCaseId = (Long) ("SERVICE_REQUEST".equals(type)
                ? row.get("service_request_id") : row.get("warranty_claim_id"));
            if (rawCaseId == null) continue;
            long caseId = rawCaseId;
            if (row.get("response_breached_at") != null && row.get("first_response_at") == null)
                createDueEscalations(tenant.id(), slaId, type, priority, caseId, "RESPONSE",
                    instant(row.get("response_breached_at")), now);
            if (row.get("resolution_breached_at") != null && row.get("closed_at") == null)
                createDueEscalations(tenant.id(), slaId, type, priority, caseId, "RESOLUTION",
                    instant(row.get("resolution_breached_at")), now);
        }
    }

    private void createDueEscalations(long tenantId, long slaId, String type, String priority,
            long caseId, String breach, Instant breachedAt, Instant now) {
        List<Map<String,Object>> rules = db.queryForList("""
            SELECT escalation_level,after_minutes,recipient_role_key
            FROM service_sla_escalation_policies
            WHERE tenant_id=? AND case_type=? AND priority=? AND breach_type=? AND enabled=TRUE
            ORDER BY escalation_level
            """, tenantId, type, priority, breach);
        for (Map<String,Object> rule : rules) {
            int level = ((Number)rule.get("escalation_level")).intValue();
            int delay = ((Number)rule.get("after_minutes")).intValue();
            if (now.isBefore(breachedAt.plus(delay, ChronoUnit.MINUTES))) continue;
            String recipient = (String)rule.get("recipient_role_key");
            try {
                db.update("""
                    INSERT INTO service_sla_escalations(tenant_id,service_case_sla_id,breach_type,
                        escalation_level,recipient_role_key,status,escalated_at)
                    VALUES(?,?,?,?,?,'OPEN',?)
                    """, tenantId, slaId, breach, level, recipient, Timestamp.from(now));
            } catch (DuplicateKeyException alreadyCreated) {
                continue;
            }
            Long escalationId = db.queryForObject("""
                SELECT id FROM service_sla_escalations
                WHERE tenant_id=? AND service_case_sla_id=? AND breach_type=? AND escalation_level=?
                """, Long.class, tenantId, slaId, breach, level);
            if (escalationId == null) throw new IllegalStateException("Escalation ID was not generated");
            db.update("""
                INSERT INTO service_sla_escalation_events(tenant_id,escalation_id,event_type,
                    actor_type,actor_id,detail,occurred_at)
                VALUES(?,?,'CREATED','SYSTEM','service-sla-worker',?,?)
                """, tenantId, escalationId, "Level " + level + " escalation sent to " + recipient,
                Timestamp.from(now));
            outbox.append(tenantId, "service-sla-worker", "SERVICE_SLA_ESCALATION",
                Long.toString(escalationId), "SERVICE_SLA.ESCALATED", null,
                Map.of("escalationId", escalationId, "slaId", slaId, "caseType", type,
                    "caseId", caseId, "breachType", breach, "level", level,
                    "recipientRoleKey", recipient, "escalatedAt", now.toString()));
        }
    }

    /** Resolves only active, verified email identities assigned the selected role in an active tenant. */
    @Transactional(readOnly = true)
    List<String> resolveEscalationRecipients(long tenantId, String recipientRoleKey) {
        if (tenantId <= 0) throw new IllegalArgumentException("A persisted tenant is required");
        String recipientRole = role(recipientRoleKey);
        List<String> candidates = db.queryForList("""
            SELECT DISTINCT u.username
            FROM tenants t
            JOIN employees e ON e.tenant_id=t.id AND e.enabled=TRUE
            JOIN users u ON u.username=e.username AND u.enabled=TRUE AND u.email_verified=TRUE
            JOIN employee_role_assignments a ON a.tenant_id=e.tenant_id
                AND a.branch_id=e.branch_id AND a.username=e.username AND a.active=TRUE
            JOIN vendor_roles r ON r.tenant_id=a.tenant_id AND r.id=a.role_id AND r.active=TRUE
            WHERE t.id=? AND e.tenant_id=? AND t.lifecycle_status='ACTIVE'
              AND (r.template_key=? OR r.role_key=? OR LOWER(r.role_key)=?)
            ORDER BY u.username LIMIT 50
            """, String.class, tenantId, tenantId, recipientRole, "standard:" + recipientRole,
            recipientRole.toLowerCase(Locale.ROOT));
        return candidates.stream().map(String::strip)
            .filter(ServiceSlaEscalationService::isEmailAddress)
            .distinct().toList();
    }

    private static boolean isEmailAddress(String value) {
        return value != null && value.length() <= 100
            && value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    @Transactional(readOnly = true)
    public List<Escalation> queue() {
        var current = actor("SERVICE_VIEW");
        return db.query("""
            SELECT e.id,e.service_case_sla_id,s.case_type,
              COALESCE(s.service_request_id,s.warranty_claim_id) case_id,
              e.breach_type,e.escalation_level,e.recipient_role_key,e.status,e.escalated_at,
              e.acknowledged_by,e.acknowledged_at,e.acknowledgement_note
            FROM service_sla_escalations e
            JOIN service_case_sla s ON s.tenant_id=e.tenant_id AND s.id=e.service_case_sla_id
            WHERE e.tenant_id=? ORDER BY CASE e.status WHEN 'OPEN' THEN 0 ELSE 1 END,
              e.escalated_at DESC,e.id DESC LIMIT 200
            """, (rs,n) -> new Escalation(rs.getLong("id"), rs.getLong("service_case_sla_id"),
                rs.getString("case_type"), rs.getLong("case_id"), rs.getString("breach_type"),
                rs.getInt("escalation_level"), rs.getString("recipient_role_key"), rs.getString("status"),
                rs.getTimestamp("escalated_at").toInstant(), rs.getString("acknowledged_by"),
                rs.getTimestamp("acknowledged_at") == null ? null : rs.getTimestamp("acknowledged_at").toInstant(),
                rs.getString("acknowledgement_note")), current.tenantId());
    }

    public Escalation acknowledge(long id, Acknowledge request) {
        var current = actor("SERVICE_UPDATE");
        String acknowledgement = note(request == null ? null : request.note());
        List<Map<String,Object>> rows = db.queryForList("""
            SELECT id,status FROM service_sla_escalations
            WHERE tenant_id=? AND id=? FOR UPDATE
            """, current.tenantId(), id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SLA escalation not found");
        if ("ACKNOWLEDGED".equals(rows.getFirst().get("status")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SLA escalation is already acknowledged");
        Instant now = clock.instant();
        int changed = db.update("""
            UPDATE service_sla_escalations SET status='ACKNOWLEDGED',acknowledged_by=?,
              acknowledged_at=?,acknowledgement_note=?
            WHERE tenant_id=? AND id=? AND status='OPEN'
            """, current.username(), Timestamp.from(now), acknowledgement, current.tenantId(), id);
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "SLA escalation changed; refresh and retry");
        db.update("""
            INSERT INTO service_sla_escalation_events(tenant_id,escalation_id,event_type,
              actor_type,actor_id,detail,occurred_at)
            VALUES(?,?,'ACKNOWLEDGED','VENDOR',?,?,?)
            """, current.tenantId(), id, current.username(), acknowledgement, Timestamp.from(now));
        outbox.append(current.tenantId(), current.username(), "SERVICE_SLA_ESCALATION",
            Long.toString(id), "SERVICE_SLA.ESCALATION_ACKNOWLEDGED", null,
            Map.of("escalationId", id, "acknowledgedAt", now.toString(), "note", acknowledgement));
        List<Map<String,Object>> updated = db.queryForList("""
            SELECT e.id,e.service_case_sla_id,s.case_type,
              COALESCE(s.service_request_id,s.warranty_claim_id) case_id,
              e.breach_type,e.escalation_level,e.recipient_role_key,e.status,e.escalated_at,
              e.acknowledged_by,e.acknowledged_at,e.acknowledgement_note
            FROM service_sla_escalations e
            JOIN service_case_sla s ON s.tenant_id=e.tenant_id AND s.id=e.service_case_sla_id
            WHERE e.tenant_id=? AND e.id=?
            """, current.tenantId(), id);
        if (updated.isEmpty()) throw new IllegalStateException("Acknowledged escalation could not be reloaded");
        Map<String,Object> row = updated.getFirst();
        return new Escalation(number(row.get("id")), number(row.get("service_case_sla_id")),
            (String) row.get("case_type"), number(row.get("case_id")), (String) row.get("breach_type"),
            ((Number) row.get("escalation_level")).intValue(), (String) row.get("recipient_role_key"),
            (String) row.get("status"), instant(row.get("escalated_at")), (String) row.get("acknowledged_by"),
            instant(row.get("acknowledged_at")), (String) row.get("acknowledgement_note"));
    }
}
