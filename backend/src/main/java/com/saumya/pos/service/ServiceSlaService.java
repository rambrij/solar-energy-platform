package com.saumya.pos.service;

import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.shared.EventOutbox;
import com.saumya.pos.tenant.TenantLifecycleService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Tenant-configurable service case response and resolution clocks with durable breach escalation. */
@Service
@Transactional
public class ServiceSlaService {
    private static final Set<String> CASE_TYPES = Set.of("SERVICE_REQUEST", "WARRANTY_CLAIM");
    private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");
    private static final List<String> POLICY_PRIORITIES = List.of("LOW", "NORMAL", "HIGH", "URGENT");
    private static final int MAX_TARGET_MINUTES = 525_600;

    private final JdbcTemplate db;
    private final AuthorizationContext authorization;
    private final EventOutbox outbox;
    private final Clock clock;

    public ServiceSlaService(JdbcTemplate db, AuthorizationContext authorization,
            EventOutbox outbox, Clock clock) {
        this.db = db;
        this.authorization = authorization;
        this.outbox = outbox;
        this.clock = clock;
    }

    public record Policy(String caseType, String priority, Integer responseTargetMinutes,
            Integer resolutionTargetMinutes, boolean configured, long version,
            Instant updatedAt, String updatedBy) {}
    public record PolicyUpdate(String caseType, String priority, int responseTargetMinutes,
            int resolutionTargetMinutes, String reason) {}
    public record CaseSla(String caseType, long caseId, String orderId, String subject,
            String priority, String caseStatus, String assignedTo, Instant openedAt,
            Integer responseTargetMinutes, Integer resolutionTargetMinutes,
            Instant responseDueAt, Instant resolutionDueAt, Instant firstResponseAt,
            Instant closedAt, Instant responseBreachedAt, Instant resolutionBreachedAt,
            String responseState, String resolutionState) {}

    private AuthorizationContext.Context vendor(String permission) {
        var actor = authorization.current();
        actor.requireVendorMembership();
        actor.requirePermission(permission);
        return actor;
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private String caseType(String value) {
        String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        if (!CASE_TYPES.contains(normalized)) throw bad("Unsupported service SLA case type");
        return normalized;
    }

    private String priority(String value) {
        String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        if (!PRIORITIES.contains(normalized)) throw bad("Unsupported service SLA priority");
        return normalized;
    }

    private String requiredReason(String value) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty() || normalized.length() > 500
                || normalized.codePoints().anyMatch(Character::isISOControl))
            throw bad("A policy change reason of at most 500 characters is required");
        return normalized;
    }

    private void target(int value, String label) {
        if (value < 1 || value > MAX_TARGET_MINUTES)
            throw bad(label + " must be between 1 and 525600 elapsed minutes");
    }

    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof Instant result) return result;
        throw new IllegalStateException("Unsupported service SLA timestamp representation");
    }

    private static long longValue(Object value) {
        return ((Number) value).longValue();
    }

    private static Integer integer(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    @Transactional(readOnly = true)
    public List<Policy> policies() {
        var actor = vendor("SERVICE_VIEW");
        Map<String, Map<String, Object>> configured = new HashMap<>();
        for (Map<String, Object> row : db.queryForList("""
                SELECT case_type,priority,response_target_minutes,resolution_target_minutes,
                       version,updated_at,updated_by
                FROM service_sla_policies WHERE tenant_id=?
                ORDER BY case_type,priority
                """, actor.tenantId())) {
            configured.put(row.get("case_type") + ":" + row.get("priority"), row);
        }
        List<Policy> result = new ArrayList<>();
        for (String type : List.of("SERVICE_REQUEST", "WARRANTY_CLAIM")) {
            for (String tier : POLICY_PRIORITIES) {
                if ("WARRANTY_CLAIM".equals(type) && !"NORMAL".equals(tier)) continue;
                Map<String, Object> row = configured.get(type + ":" + tier);
                result.add(row == null
                    ? new Policy(type, tier, null, null, false, 0, null, null)
                    : new Policy(type, tier, integer(row.get("response_target_minutes")),
                        integer(row.get("resolution_target_minutes")), true,
                        longValue(row.get("version")), instant(row.get("updated_at")),
                        (String) row.get("updated_by")));
            }
        }
        return List.copyOf(result);
    }

    public Policy updatePolicy(PolicyUpdate input) {
        var actor = vendor("SERVICE_ASSIGN");
        if (input == null) throw bad("Service SLA policy details are required");
        String type = caseType(input.caseType());
        String tier = priority(input.priority());
        if ("WARRANTY_CLAIM".equals(type) && !"NORMAL".equals(tier))
            throw bad("Warranty claims use the NORMAL priority policy until claim priority is introduced");
        target(input.responseTargetMinutes(), "Response target");
        target(input.resolutionTargetMinutes(), "Resolution target");
        if (input.resolutionTargetMinutes() < input.responseTargetMinutes())
            throw bad("Resolution target must be greater than or equal to the response target");
        String reason = requiredReason(input.reason());
        Instant now = clock.instant();

        List<Map<String, Object>> existing = db.queryForList("""
            SELECT response_target_minutes,resolution_target_minutes,version
            FROM service_sla_policies
            WHERE tenant_id=? AND case_type=? AND priority=? FOR UPDATE
            """, actor.tenantId(), type, tier);
        Integer previousResponse = existing.isEmpty() ? null
            : integer(existing.getFirst().get("response_target_minutes"));
        Integer previousResolution = existing.isEmpty() ? null
            : integer(existing.getFirst().get("resolution_target_minutes"));
        String changeType;
        if (existing.isEmpty()) {
            db.update("""
                INSERT INTO service_sla_policies(tenant_id,case_type,priority,
                    response_target_minutes,resolution_target_minutes,updated_by,updated_reason,
                    created_at,updated_at,version)
                VALUES(?,?,?,?,?,?,?,?,?,0)
                """, actor.tenantId(), type, tier, input.responseTargetMinutes(),
                input.resolutionTargetMinutes(), actor.username(), reason,
                Timestamp.from(now), Timestamp.from(now));
            changeType = "CREATED";
        } else {
            db.update("""
                UPDATE service_sla_policies
                SET response_target_minutes=?,resolution_target_minutes=?,updated_by=?,
                    updated_reason=?,updated_at=?,version=version+1
                WHERE tenant_id=? AND case_type=? AND priority=?
                """, input.responseTargetMinutes(), input.resolutionTargetMinutes(),
                actor.username(), reason, Timestamp.from(now), actor.tenantId(), type, tier);
            changeType = "UPDATED";
        }

        db.update("""
            INSERT INTO service_sla_policy_audit(tenant_id,case_type,priority,change_type,
                previous_response_target_minutes,previous_resolution_target_minutes,
                response_target_minutes,resolution_target_minutes,actor_username,reason,occurred_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?)
            """, actor.tenantId(), type, tier, changeType, previousResponse, previousResolution,
            input.responseTargetMinutes(), input.resolutionTargetMinutes(), actor.username(),
            reason, Timestamp.from(now));

        outbox.append(actor.tenantId(), actor.username(), "SERVICE_SLA_POLICY",
            type + ":" + tier, "SERVICE_SLA_POLICY." + changeType, null,
            Map.of("caseType", type, "priority", tier,
                "responseTargetMinutes", input.responseTargetMinutes(),
                "resolutionTargetMinutes", input.resolutionTargetMinutes(),
                "reason", reason, "occurredAt", now.toString()));

        backfillUntrackedCases(actor, type, tier, input.responseTargetMinutes(),
            input.resolutionTargetMinutes(), now);
        return policies().stream().filter(p -> p.caseType().equals(type) && p.priority().equals(tier))
            .findFirst().orElseThrow();
    }

    private void backfillUntrackedCases(AuthorizationContext.Context actor, String type,
            String tier, int responseMinutes, int resolutionMinutes, Instant now) {
        if ("SERVICE_REQUEST".equals(type)) {
            while (true) {
                List<Map<String, Object>> cases = db.queryForList("""
                    SELECT r.id FROM service_requests r
                    WHERE r.tenant_id=? AND r.priority=? AND r.status NOT IN ('RESOLVED','CLOSED')
                      AND NOT EXISTS (SELECT 1 FROM service_case_sla s
                        WHERE s.tenant_id=r.tenant_id AND s.service_request_id=r.id)
                    ORDER BY r.created_at,r.id LIMIT 500
                    """, actor.tenantId(), tier);
                for (Map<String, Object> row : cases) {
                    insertClock(actor.tenantId(), type, longValue(row.get("id")), null, tier,
                        now, responseMinutes, resolutionMinutes,
                        "SYSTEM", "sla-policy-backfill", now);
                }
                if (cases.size() < 500) break;
            }
        } else {
            while (true) {
                List<Map<String, Object>> cases = db.queryForList("""
                    SELECT c.id FROM warranty_claims c
                    WHERE c.tenant_id=? AND c.status NOT IN ('REJECTED','RESOLVED')
                      AND NOT EXISTS (SELECT 1 FROM service_case_sla s
                        WHERE s.tenant_id=c.tenant_id AND s.warranty_claim_id=c.id)
                    ORDER BY c.submitted_at,c.id LIMIT 500
                    """, actor.tenantId());
                for (Map<String, Object> row : cases) {
                    insertClock(actor.tenantId(), type, null, longValue(row.get("id")), tier,
                        now, responseMinutes, resolutionMinutes,
                        "SYSTEM", "sla-policy-backfill", now);
                }
                if (cases.size() < 500) break;
            }
        }
    }

    /** Called inside the already-authorized service-request creation transaction. */
    public void startForServiceRequest(long tenantId, long requestId, String tier,
            String actorType, String actorId, Instant openedAt) {
        String normalizedPriority = priority(tier);
        List<Map<String, Object>> policies = db.queryForList("""
            SELECT response_target_minutes,resolution_target_minutes
            FROM service_sla_policies
            WHERE tenant_id=? AND case_type='SERVICE_REQUEST' AND priority=?
            """, tenantId, normalizedPriority);
        if (policies.isEmpty()) return;
        Map<String, Object> policy = policies.getFirst();
        insertClock(tenantId, "SERVICE_REQUEST", requestId, null, normalizedPriority,
            openedAt, integer(policy.get("response_target_minutes")),
            integer(policy.get("resolution_target_minutes")), actorType, actorId, openedAt);
    }

    /** Warranty claims currently use NORMAL because their aggregate has no priority field. */
    public void startForWarrantyClaim(long tenantId, long claimId, String actorId, Instant openedAt) {
        List<Map<String, Object>> policies = db.queryForList("""
            SELECT response_target_minutes,resolution_target_minutes
            FROM service_sla_policies
            WHERE tenant_id=? AND case_type='WARRANTY_CLAIM' AND priority='NORMAL'
            """, tenantId);
        if (policies.isEmpty()) return;
        Map<String, Object> policy = policies.getFirst();
        insertClock(tenantId, "WARRANTY_CLAIM", null, claimId, "NORMAL", openedAt,
            integer(policy.get("response_target_minutes")),
            integer(policy.get("resolution_target_minutes")), "CUSTOMER", actorId, openedAt);
    }

    private void insertClock(long tenantId, String type, Long requestId, Long claimId,
            String tier, Instant openedAt, int responseMinutes, int resolutionMinutes,
            String actorType, String actorId, Instant occurredAt) {
        Instant responseDue = openedAt.plus(responseMinutes, ChronoUnit.MINUTES);
        Instant resolutionDue = openedAt.plus(resolutionMinutes, ChronoUnit.MINUTES);
        db.update("""
            INSERT INTO service_case_sla(tenant_id,case_type,service_request_id,warranty_claim_id,
                priority,response_target_minutes,resolution_target_minutes,opened_at,
                response_due_at,resolution_due_at,created_at,updated_at,version)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,0)
            """, tenantId, type, requestId, claimId, tier, responseMinutes, resolutionMinutes,
            Timestamp.from(openedAt), Timestamp.from(responseDue), Timestamp.from(resolutionDue),
            Timestamp.from(occurredAt), Timestamp.from(occurredAt));
        Long caseId = "SERVICE_REQUEST".equals(type) ? requestId : claimId;
        Long slaId = db.queryForObject("""
            SELECT id FROM service_case_sla
            WHERE tenant_id=? AND case_type=? AND COALESCE(service_request_id,warranty_claim_id)=?
            """, Long.class, tenantId, type, caseId);
        if (slaId == null) throw new IllegalStateException("Service SLA clock ID was not generated");
        db.update("""
            INSERT INTO service_sla_events(tenant_id,service_case_sla_id,event_type,
                actor_type,actor_id,detail,occurred_at)
            VALUES(?,?,'SLA_STARTED',?,?,?,?)
            """, tenantId, slaId, actorType, actorId,
            "Response and resolution targets were snapshotted for this case", Timestamp.from(occurredAt));
    }

    public void recordFirstResponse(AuthorizationContext.Context actor, String type,
            long caseId, Instant occurredAt) {
        requireServiceActor(actor);
        String normalized = caseType(type);
        List<Map<String, Object>> rows;
        if ("SERVICE_REQUEST".equals(normalized)) {
            rows = db.queryForList("""
                SELECT id,response_due_at,first_response_at,response_breached_at,closed_at
                FROM service_case_sla WHERE tenant_id=? AND case_type='SERVICE_REQUEST'
                  AND service_request_id=? FOR UPDATE
                """, actor.tenantId(), caseId);
        } else {
            rows = db.queryForList("""
                SELECT id,response_due_at,first_response_at,response_breached_at,closed_at
                FROM service_case_sla WHERE tenant_id=? AND case_type='WARRANTY_CLAIM'
                  AND warranty_claim_id=? FOR UPDATE
                """, actor.tenantId(), caseId);
        }
        if (rows.isEmpty()) return;
        Map<String, Object> row = rows.getFirst();
        if (row.get("first_response_at") != null || row.get("closed_at") != null) return;
        Instant due = instant(row.get("response_due_at"));
        long slaId = longValue(row.get("id"));
        if (row.get("response_breached_at") == null && !occurredAt.isBefore(due))
            markResponseBreach(actor, slaId, normalized, caseId, occurredAt);
        db.update("""
            UPDATE service_case_sla SET first_response_at=?,updated_at=?,version=version+1
            WHERE tenant_id=? AND id=? AND first_response_at IS NULL
            """, Timestamp.from(occurredAt), Timestamp.from(occurredAt), actor.tenantId(), slaId);
        appendVendorEvent(actor, slaId, "FIRST_RESPONSE_RECORDED",
            "First vendor service action recorded", occurredAt);
    }

    public void closeCase(AuthorizationContext.Context actor, String type,
            long caseId, Instant occurredAt) {
        actor.requireVendorMembership();
        actor.requirePermission("SERVICE_RESOLVE");
        String normalized = caseType(type);
        List<Map<String, Object>> rows;
        if ("SERVICE_REQUEST".equals(normalized)) {
            rows = db.queryForList("""
                SELECT id,resolution_due_at,closed_at,resolution_breached_at
                FROM service_case_sla WHERE tenant_id=? AND case_type='SERVICE_REQUEST'
                  AND service_request_id=? FOR UPDATE
                """, actor.tenantId(), caseId);
        } else {
            rows = db.queryForList("""
                SELECT id,resolution_due_at,closed_at,resolution_breached_at
                FROM service_case_sla WHERE tenant_id=? AND case_type='WARRANTY_CLAIM'
                  AND warranty_claim_id=? FOR UPDATE
                """, actor.tenantId(), caseId);
        }
        if (rows.isEmpty()) return;
        Map<String, Object> row = rows.getFirst();
        if (row.get("closed_at") != null) return;
        Instant due = instant(row.get("resolution_due_at"));
        long slaId = longValue(row.get("id"));
        if (row.get("resolution_breached_at") == null && !occurredAt.isBefore(due))
            markResolutionBreach(actor, slaId, normalized, caseId, occurredAt);
        db.update("""
            UPDATE service_case_sla SET closed_at=?,updated_at=?,version=version+1
            WHERE tenant_id=? AND id=? AND closed_at IS NULL
            """, Timestamp.from(occurredAt), Timestamp.from(occurredAt), actor.tenantId(), slaId);
        appendVendorEvent(actor, slaId, "CASE_CLOSED", "Service case reached a terminal state", occurredAt);
    }

    private void requireServiceActor(AuthorizationContext.Context actor) {
        actor.requireVendorMembership();
        if (!actor.hasAnyPermission("SERVICE_ASSIGN", "SERVICE_UPDATE", "SERVICE_RESOLVE"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A service action is required to record SLA response");
    }

    @Transactional(readOnly = true)
    public List<CaseSla> dashboard() {
        var actor = vendor("SERVICE_VIEW");
        List<CaseSla> result = new ArrayList<>();
        List<Map<String, Object>> requests;
        if (actor.hasPermission("SERVICE_ASSIGN")) {
            requests = db.queryForList("""
                SELECT 'SERVICE_REQUEST' case_type,r.id case_id,r.order_id,r.subject,r.priority,
                       r.status case_status,r.assigned_to,r.created_at opened_at,
                       s.response_target_minutes,s.resolution_target_minutes,s.response_due_at,
                       s.resolution_due_at,s.first_response_at,s.closed_at,s.response_breached_at,
                       s.resolution_breached_at
                FROM service_requests r
                LEFT JOIN service_case_sla s ON s.tenant_id=r.tenant_id AND s.service_request_id=r.id
                WHERE r.tenant_id=? AND r.status NOT IN ('RESOLVED','CLOSED')
                ORDER BY r.created_at DESC,r.id DESC LIMIT 200
                """, actor.tenantId());
        } else {
            requests = db.queryForList("""
                SELECT 'SERVICE_REQUEST' case_type,r.id case_id,r.order_id,r.subject,r.priority,
                       r.status case_status,r.assigned_to,r.created_at opened_at,
                       s.response_target_minutes,s.resolution_target_minutes,s.response_due_at,
                       s.resolution_due_at,s.first_response_at,s.closed_at,s.response_breached_at,
                       s.resolution_breached_at
                FROM service_requests r
                LEFT JOIN service_case_sla s ON s.tenant_id=r.tenant_id AND s.service_request_id=r.id
                WHERE r.tenant_id=? AND r.assigned_to=? AND r.status NOT IN ('RESOLVED','CLOSED')
                ORDER BY r.created_at DESC,r.id DESC LIMIT 200
                """, actor.tenantId(), actor.username());
        }
        requests.stream().map(row -> toCaseSla(row, clock.instant())).forEach(result::add);

        List<Map<String, Object>> claims;
        if (actor.hasPermission("SERVICE_ASSIGN")) {
            claims = db.queryForList("""
                SELECT 'WARRANTY_CLAIM' case_type,c.id case_id,w.order_id,c.subject,
                       'NORMAL' priority,c.status case_status,c.assigned_to,c.submitted_at opened_at,
                       s.response_target_minutes,s.resolution_target_minutes,s.response_due_at,
                       s.resolution_due_at,s.first_response_at,s.closed_at,s.response_breached_at,
                       s.resolution_breached_at
                FROM warranty_claims c
                JOIN warranty_records w ON w.tenant_id=c.tenant_id AND w.id=c.warranty_id
                LEFT JOIN service_case_sla s ON s.tenant_id=c.tenant_id AND s.warranty_claim_id=c.id
                WHERE c.tenant_id=? AND c.status NOT IN ('REJECTED','RESOLVED')
                ORDER BY c.submitted_at DESC,c.id DESC LIMIT 200
                """, actor.tenantId());
        } else {
            claims = db.queryForList("""
                SELECT 'WARRANTY_CLAIM' case_type,c.id case_id,w.order_id,c.subject,
                       'NORMAL' priority,c.status case_status,c.assigned_to,c.submitted_at opened_at,
                       s.response_target_minutes,s.resolution_target_minutes,s.response_due_at,
                       s.resolution_due_at,s.first_response_at,s.closed_at,s.response_breached_at,
                       s.resolution_breached_at
                FROM warranty_claims c
                JOIN warranty_records w ON w.tenant_id=c.tenant_id AND w.id=c.warranty_id
                LEFT JOIN service_case_sla s ON s.tenant_id=c.tenant_id AND s.warranty_claim_id=c.id
                WHERE c.tenant_id=? AND c.assigned_to=? AND c.status NOT IN ('REJECTED','RESOLVED')
                ORDER BY c.submitted_at DESC,c.id DESC LIMIT 200
                """, actor.tenantId(), actor.username());
        }
        claims.stream().map(row -> toCaseSla(row, clock.instant())).forEach(result::add);
        return result.stream().sorted((a, b) -> b.openedAt().compareTo(a.openedAt())).toList();
    }

    private CaseSla toCaseSla(Map<String, Object> row, Instant now) {
        Integer responseMinutes = integer(row.get("response_target_minutes"));
        Integer resolutionMinutes = integer(row.get("resolution_target_minutes"));
        Instant responseDue = instant(row.get("response_due_at"));
        Instant resolutionDue = instant(row.get("resolution_due_at"));
        Instant firstResponse = instant(row.get("first_response_at"));
        Instant closedAt = instant(row.get("closed_at"));
        Instant responseBreach = instant(row.get("response_breached_at"));
        Instant resolutionBreach = instant(row.get("resolution_breached_at"));
        return new CaseSla((String) row.get("case_type"), longValue(row.get("case_id")),
            (String) row.get("order_id"), (String) row.get("subject"), (String) row.get("priority"),
            (String) row.get("case_status"), (String) row.get("assigned_to"), instant(row.get("opened_at")),
            responseMinutes, resolutionMinutes, responseDue, resolutionDue, firstResponse, closedAt,
            responseBreach, resolutionBreach,
            state(responseDue, firstResponse, responseBreach, now),
            state(resolutionDue, closedAt, resolutionBreach, now));
    }

    private String state(Instant due, Instant completed, Instant breached, Instant now) {
        if (due == null) return "NOT_CONFIGURED";
        if (breached != null || (completed != null && completed.isAfter(due))
                || (completed == null && !now.isBefore(due))) return "BREACHED";
        return completed == null ? "ON_TRACK" : "MET";
    }

    /** Runs one bounded, tenant-qualified batch. */
    public void processDueForTenant(TenantLifecycleService.TenantSummary tenant, Instant now) {
        List<Map<String, Object>> due = db.queryForList("""
            SELECT id,case_type,service_request_id,warranty_claim_id,priority,
                   first_response_at,closed_at,response_due_at,resolution_due_at,
                   response_breached_at,resolution_breached_at
            FROM service_case_sla
            WHERE tenant_id=? AND closed_at IS NULL
              AND ((first_response_at IS NULL AND response_due_at<=? AND response_breached_at IS NULL)
                OR (resolution_due_at<=? AND resolution_breached_at IS NULL))
            ORDER BY LEAST(response_due_at,resolution_due_at),id
            LIMIT 100
            """, tenant.id(), Timestamp.from(now), Timestamp.from(now));
        for (Map<String, Object> row : due) {
            long slaId = longValue(row.get("id"));
            String type = (String) row.get("case_type");
            long caseId = longValue("SERVICE_REQUEST".equals(type)
                ? row.get("service_request_id") : row.get("warranty_claim_id"));
            if (row.get("first_response_at") == null && row.get("response_breached_at") == null
                    && !now.isBefore(instant(row.get("response_due_at"))))
                markResponseBreach(tenant, slaId, type, caseId, now);
            if (row.get("resolution_breached_at") == null
                    && !now.isBefore(instant(row.get("resolution_due_at"))))
                markResolutionBreach(tenant, slaId, type, caseId, now);
        }
    }

    private void markResponseBreach(AuthorizationContext.Context actor, long slaId,
            String type, long caseId, Instant now) {
        int changed = db.update("""
            UPDATE service_case_sla SET response_breached_at=?,updated_at=?,version=version+1
            WHERE tenant_id=? AND id=? AND closed_at IS NULL AND first_response_at IS NULL
              AND response_due_at<=? AND response_breached_at IS NULL
            """, Timestamp.from(now), Timestamp.from(now), actor.tenantId(), slaId, Timestamp.from(now));
        if (changed == 1) {
            appendVendorEvent(actor, slaId, "RESPONSE_BREACHED", "First response target was exceeded", now);
            outbox.append(actor.tenantId(), actor.username(), "SERVICE_CASE_SLA", Long.toString(slaId),
                "SERVICE_SLA.RESPONSE_BREACHED", null,
                Map.of("slaId", slaId, "caseType", type, "caseId", caseId, "breachedAt", now.toString()));
        }
    }

    private void markResolutionBreach(AuthorizationContext.Context actor, long slaId,
            String type, long caseId, Instant now) {
        int changed = db.update("""
            UPDATE service_case_sla SET resolution_breached_at=?,updated_at=?,version=version+1
            WHERE tenant_id=? AND id=? AND closed_at IS NULL
              AND resolution_due_at<=? AND resolution_breached_at IS NULL
            """, Timestamp.from(now), Timestamp.from(now), actor.tenantId(), slaId, Timestamp.from(now));
        if (changed == 1) {
            appendVendorEvent(actor, slaId, "RESOLUTION_BREACHED", "Resolution target was exceeded", now);
            outbox.append(actor.tenantId(), actor.username(), "SERVICE_CASE_SLA", Long.toString(slaId),
                "SERVICE_SLA.RESOLUTION_BREACHED", null,
                Map.of("slaId", slaId, "caseType", type, "caseId", caseId, "breachedAt", now.toString()));
        }
    }

    private void markResponseBreach(TenantLifecycleService.TenantSummary tenant,
            long slaId, String type, long caseId, Instant now) {
        int changed = db.update("""
            UPDATE service_case_sla SET response_breached_at=?,updated_at=?,version=version+1
            WHERE tenant_id=? AND id=? AND closed_at IS NULL AND first_response_at IS NULL
              AND response_due_at<=? AND response_breached_at IS NULL
            """, Timestamp.from(now), Timestamp.from(now), tenant.id(), slaId, Timestamp.from(now));
        if (changed == 1) {
            appendSystemEvent(tenant, slaId, "RESPONSE_BREACHED", "First response target was exceeded", now);
            outbox.append(tenant.id(), "service-sla-worker", "SERVICE_CASE_SLA", Long.toString(slaId),
                "SERVICE_SLA.RESPONSE_BREACHED", null,
                Map.of("slaId", slaId, "caseType", type, "caseId", caseId, "breachedAt", now.toString()));
        }
    }

    private void markResolutionBreach(TenantLifecycleService.TenantSummary tenant,
            long slaId, String type, long caseId, Instant now) {
        int changed = db.update("""
            UPDATE service_case_sla SET resolution_breached_at=?,updated_at=?,version=version+1
            WHERE tenant_id=? AND id=? AND closed_at IS NULL
              AND resolution_due_at<=? AND resolution_breached_at IS NULL
            """, Timestamp.from(now), Timestamp.from(now), tenant.id(), slaId, Timestamp.from(now));
        if (changed == 1) {
            appendSystemEvent(tenant, slaId, "RESOLUTION_BREACHED", "Resolution target was exceeded", now);
            outbox.append(tenant.id(), "service-sla-worker", "SERVICE_CASE_SLA", Long.toString(slaId),
                "SERVICE_SLA.RESOLUTION_BREACHED", null,
                Map.of("slaId", slaId, "caseType", type, "caseId", caseId, "breachedAt", now.toString()));
        }
    }

    private void appendVendorEvent(AuthorizationContext.Context actor, long slaId,
            String type, String detail, Instant now) {
        db.update("""
            INSERT INTO service_sla_events(tenant_id,service_case_sla_id,event_type,
                actor_type,actor_id,detail,occurred_at)
            VALUES(?,?,?,'VENDOR',?,?,?)
            """, actor.tenantId(), slaId, type, actor.username(), detail, Timestamp.from(now));
    }

    private void appendSystemEvent(TenantLifecycleService.TenantSummary tenant, long slaId,
            String type, String detail, Instant now) {
        db.update("""
            INSERT INTO service_sla_events(tenant_id,service_case_sla_id,event_type,
                actor_type,actor_id,detail,occurred_at)
            VALUES(?,?,?,'SYSTEM','service-sla-worker',?,?)
            """, tenant.id(), slaId, type, detail, Timestamp.from(now));
    }
}
