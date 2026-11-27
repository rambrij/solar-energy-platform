package com.saumya.pos.documents;

import com.saumya.pos.documents.QuotationPdfData;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies that the saved quote revision can be rendered without catalogue/payment dependencies. */
class QuotationPdfRendererTest {
    private final QuotationPdfRenderer renderer=new QuotationPdfRenderer();
    private final Instant sent=Instant.parse("2026-11-27T10:00:00Z");
    private final QuotationPdfData quotation=new QuotationPdfData(
        42,2,"SENT","INR",sent.plusSeconds(86_400),sent,
        new BigDecimal("12000.00"),new BigDecimal("2160.00"),new BigDecimal("14160.00"),
        "Installation subject to site inspection",
        List.of(new QuotationPdfData.Line(9,"Solar Panel 550W",1,
            new BigDecimal("13000.00"),new BigDecimal("12000.00"),new BigDecimal("7.69"),
            new BigDecimal("18.00"),new BigDecimal("2160.00"),new BigDecimal("14160.00")))
    );

    @Test
    void rendersPdfFromTheSavedRevisionSnapshots() {
        byte[] bytes=renderer.render(quotation,"doc-reference-42",sent.plusSeconds(30));
        assertThat(bytes).startsWith("%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertThat(bytes.length).isGreaterThan(500);
        assertThat(bytes.length).isLessThan(2_000_000);
    }

    @Test
    void rejectsMissingOrUnboundedRevisionLines() {
        var empty=new QuotationPdfData(42,2,"SENT","INR",sent,sent,
            BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,null,List.of());
        assertThatThrownBy(()->renderer.render(empty,"doc-reference-42",sent))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("saved quotation revision");
    }
}
