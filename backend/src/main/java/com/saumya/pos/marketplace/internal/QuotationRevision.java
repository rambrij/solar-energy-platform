package com.saumya.pos.marketplace.internal;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "quotation_revisions",
    uniqueConstraints = @UniqueConstraint(name = "uk_quotation_revision_tenant_number",
        columnNames = {"tenant_id", "quotation_id", "revision_number"}))
public class QuotationRevision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false, updatable = false) private long tenantId;
    @Column(name = "quotation_id", nullable = false, updatable = false) private long quotationId;
    @Column(name = "revision_number", nullable = false, updatable = false) private int revisionNumber;
    @Column(nullable = false, length = 16) private String status;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "valid_until", nullable = false, updatable = false) private Instant validUntil;
    @Column(nullable = false, precision = 19, scale = 2, updatable = false) private BigDecimal subtotal;
    @Column(name = "tax_total", nullable = false, precision = 19, scale = 2, updatable = false) private BigDecimal taxTotal;
    @Column(nullable = false, precision = 19, scale = 2, updatable = false) private BigDecimal total;
    @Column(length = 4000, updatable = false) private String terms;
    @Column(name = "created_by", nullable = false, length = 100, updatable = false) private String createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "sent_at") private Instant sentAt;
    @Column(name = "decided_by_customer_id") private Long decidedByCustomerId;
    @Column(name = "decision_at") private Instant decisionAt;
    @Column(name = "decision_reason", length = 500) private String decisionReason;
    @Version @Column(nullable = false) private long version;

    protected QuotationRevision() {}

    public QuotationRevision(long tenantId, long quotationId, int revisionNumber, Instant validUntil,
            String currency, BigDecimal subtotal, BigDecimal taxTotal, BigDecimal total,
            String terms, String actor, Instant createdAt) {
        this.tenantId = tenantId;
        this.quotationId = quotationId;
        this.revisionNumber = revisionNumber;
        this.status = "DRAFT";
        this.validUntil = validUntil;
        this.currency = currency;
        this.subtotal = subtotal;
        this.taxTotal = taxTotal;
        this.total = total;
        this.terms = terms;
        this.createdBy = actor;
        this.createdAt = createdAt;
    }

    public void send(Instant now) {
        if (!"DRAFT".equals(status)) throw new IllegalStateException("Only a draft revision can be sent");
        status = "SENT";
        sentAt = now;
    }

    public void supersede() {
        if (!"SENT".equals(status)) throw new IllegalStateException("Only a sent revision can be superseded");
        status = "SUPERSEDED";
    }

    public void expire(Instant now) {
        if ("SENT".equals(status) && !validUntil.isAfter(now)) status = "EXPIRED";
    }

    public void decide(String decision, long customerId, String reason, Instant now) {
        if (!"SENT".equals(status)) throw new IllegalStateException("Only a sent revision can be decided");
        if (!"ACCEPTED".equals(decision) && !"REJECTED".equals(decision))
            throw new IllegalArgumentException("Unsupported quotation decision");
        status = decision;
        decidedByCustomerId = customerId;
        decisionReason = reason;
        decisionAt = now;
    }

    public Long id() { return id; }
    public long tenantId() { return tenantId; }
    public long quotationId() { return quotationId; }
    public int revisionNumber() { return revisionNumber; }
    public String status() { return status; }
    public String currency() { return currency; }
    public Instant validUntil() { return validUntil; }
    public BigDecimal subtotal() { return subtotal; }
    public BigDecimal taxTotal() { return taxTotal; }
    public BigDecimal total() { return total; }
    public String terms() { return terms; }
    public String createdBy() { return createdBy; }
    public Instant createdAt() { return createdAt; }
    public Instant sentAt() { return sentAt; }
    public Long decidedByCustomerId() { return decidedByCustomerId; }
    public Instant decisionAt() { return decisionAt; }
    public String decisionReason() { return decisionReason; }
    public long version() { return version; }
}
