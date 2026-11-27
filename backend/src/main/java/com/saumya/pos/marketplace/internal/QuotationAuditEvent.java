package com.saumya.pos.marketplace.internal;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "quotation_audit_events")
public class QuotationAuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false, updatable = false) private long tenantId;
    @Column(name = "quotation_id", nullable = false, updatable = false) private long quotationId;
    @Column(name = "revision_id", updatable = false) private Long revisionId;
    @Column(name = "actor_type", nullable = false, length = 12, updatable = false) private String actorType;
    @Column(name = "actor_username", nullable = false, length = 100, updatable = false) private String actorUsername;
    @Column(name = "customer_id", updatable = false) private Long customerId;
    @Column(nullable = false, length = 60, updatable = false) private String action;
    @Column(nullable = false, length = 500, updatable = false) private String reason;
    @Column(name = "occurred_at", nullable = false, updatable = false) private Instant occurredAt;

    protected QuotationAuditEvent() {}

    public QuotationAuditEvent(long tenantId, long quotationId, Long revisionId, String actorType,
            String actorUsername, Long customerId, String action, String reason, Instant occurredAt) {
        this.tenantId = tenantId;
        this.quotationId = quotationId;
        this.revisionId = revisionId;
        this.actorType = actorType;
        this.actorUsername = actorUsername;
        this.customerId = customerId;
        this.action = action;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }
}
