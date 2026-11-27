package com.saumya.pos.marketplace.internal;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "quotation_lines",
    uniqueConstraints = @UniqueConstraint(name = "uk_quotation_line_tenant_revision_line",
        columnNames = {"tenant_id", "quotation_revision_id", "line_number"}))
public class QuotationLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false, updatable = false) private long tenantId;
    @Column(name = "quotation_revision_id", nullable = false, updatable = false) private long revisionId;
    @Column(name = "quotation_id", nullable = false, updatable = false) private long quotationId;
    @Column(name = "revision_number", nullable = false, updatable = false) private int revisionNumber;
    @Column(name = "line_number", nullable = false, updatable = false) private int lineNumber;
    @Column(name = "product_id", nullable = false, updatable = false) private long productId;
    @Column(name = "product_name_snapshot", nullable = false, length = 160, updatable = false) private String productNameSnapshot;
    @Column(nullable = false, updatable = false) private int quantity;
    @Column(name = "list_price", nullable = false, precision = 19, scale = 2, updatable = false) private BigDecimal listPrice;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2, updatable = false) private BigDecimal unitPrice;
    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2, updatable = false) private BigDecimal discountPercent;
    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2, updatable = false) private BigDecimal taxRate;
    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 2, updatable = false) private BigDecimal taxAmount;
    @Column(name = "line_total", nullable = false, precision = 19, scale = 2, updatable = false) private BigDecimal lineTotal;

    protected QuotationLine() {}

    public QuotationLine(long tenantId, long revisionId, long quotationId, int revisionNumber,
            int lineNumber, long productId, String productNameSnapshot, int quantity,
            BigDecimal listPrice, BigDecimal unitPrice, BigDecimal discountPercent,
            BigDecimal taxRate, BigDecimal taxAmount, BigDecimal lineTotal) {
        this.tenantId = tenantId;
        this.revisionId = revisionId;
        this.quotationId = quotationId;
        this.revisionNumber = revisionNumber;
        this.lineNumber = lineNumber;
        this.productId = productId;
        this.productNameSnapshot = productNameSnapshot;
        this.quantity = quantity;
        this.listPrice = listPrice;
        this.unitPrice = unitPrice;
        this.discountPercent = discountPercent;
        this.taxRate = taxRate;
        this.taxAmount = taxAmount;
        this.lineTotal = lineTotal;
    }

    public Long id() { return id; }
    public long tenantId() { return tenantId; }
    public long revisionId() { return revisionId; }
    public long quotationId() { return quotationId; }
    public int revisionNumber() { return revisionNumber; }
    public int lineNumber() { return lineNumber; }
    public long productId() { return productId; }
    public String productNameSnapshot() { return productNameSnapshot; }
    public int quantity() { return quantity; }
    public BigDecimal listPrice() { return listPrice; }
    public BigDecimal unitPrice() { return unitPrice; }
    public BigDecimal discountPercent() { return discountPercent; }
    public BigDecimal taxRate() { return taxRate; }
    public BigDecimal taxAmount() { return taxAmount; }
    public BigDecimal lineTotal() { return lineTotal; }
}
