package com.saumya.pos.documents;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Neutral document projection: the documents module does not depend on marketplace persistence/services. */
public record QuotationPdfData(long quotationId,int revisionNumber,String status,String currency,
        Instant validUntil,Instant sentAt,BigDecimal subtotal,BigDecimal taxTotal,BigDecimal total,
        String terms,List<Line> items) {
    public record Line(long productId,String name,int quantity,BigDecimal listPrice,BigDecimal unitPrice,
            BigDecimal discountPercent,BigDecimal taxRate,BigDecimal tax,BigDecimal total) {}
}
