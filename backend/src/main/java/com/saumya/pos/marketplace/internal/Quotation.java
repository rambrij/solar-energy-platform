package com.saumya.pos.marketplace.internal;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "quotations")
public class Quotation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false, updatable = false) private long tenantId;
    @Column(name = "enquiry_id", nullable = false, updatable = false) private long enquiryId;
    @Column(name = "customer_id", nullable = false, updatable = false) private long customerId;
    @Column(name = "latest_revision_number", nullable = false) private int latestRevisionNumber;
    @Column(name = "accepted_revision_number") private Integer acceptedRevisionNumber;
    @Column(nullable = false, length = 16) private String status;
    @Column(name = "accepted_by_customer_id") private Long acceptedByCustomerId;
    @Column(name = "accepted_at") private Instant acceptedAt;
    @Column(name = "converted_order_id", length = 36) private String convertedOrderId;
    @Column(name = "created_by", nullable = false, length = 100, updatable = false) private String createdBy;
    @Column(name = "updated_by", nullable = false, length = 100) private String updatedBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected Quotation() {}

    public Quotation(long tenantId, long enquiryId, long customerId, String actor, Instant now) {
        this.tenantId = tenantId;
        this.enquiryId = enquiryId;
        this.customerId = customerId;
        this.status = "OPEN";
        this.createdBy = actor;
        this.updatedBy = actor;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public int addRevision(String actor, Instant now) {
        if (!"OPEN".equals(status)) throw new IllegalStateException("Only an open quotation can be revised");
        latestRevisionNumber++;
        updatedBy = actor;
        updatedAt = now;
        return latestRevisionNumber;
    }

    public void accept(int revisionNumber, long customerId, Instant now) {
        if (!"OPEN".equals(status) || customerId != this.customerId || revisionNumber != latestRevisionNumber)
            throw new IllegalStateException("Quotation is not available for acceptance");
        status = "ACCEPTED";
        acceptedRevisionNumber = revisionNumber;
        acceptedByCustomerId = customerId;
        acceptedAt = now;
        updatedAt = now;
    }

    public void converted(String orderId, Instant now) {
        if (!"ACCEPTED".equals(status) || acceptedRevisionNumber == null || orderId == null || orderId.isBlank())
            throw new IllegalStateException("Only an accepted quotation can be converted");
        status = "CONVERTED";
        convertedOrderId = orderId;
        updatedAt = now;
    }

    public Long id() { return id; }
    public long tenantId() { return tenantId; }
    public long enquiryId() { return enquiryId; }
    public long customerId() { return customerId; }
    public int latestRevisionNumber() { return latestRevisionNumber; }
    public Integer acceptedRevisionNumber() { return acceptedRevisionNumber; }
    public String status() { return status; }
    public Long acceptedByCustomerId() { return acceptedByCustomerId; }
    public Instant acceptedAt() { return acceptedAt; }
    public String convertedOrderId() { return convertedOrderId; }
    public String createdBy() { return createdBy; }
    public String updatedBy() { return updatedBy; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
