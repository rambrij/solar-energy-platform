package com.saumya.pos.marketplace;

import com.saumya.pos.documents.QuotationPdfData;
import com.saumya.pos.documents.QuotationPdfRenderer;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotationDocumentServiceTest {
    @Mock QuotationService quotations;
    @Mock QuotationPdfRenderer renderer;
    final Instant now=Instant.parse("2026-11-27T12:00:00Z");
    final QuotationPdfData data=new QuotationPdfData(42,3,"SENT","INR",now.plusSeconds(3600),now,
        new BigDecimal("100.00"),new BigDecimal("18.00"),new BigDecimal("118.00"),"Valid site survey required",
        List.of(new QuotationPdfData.Line(9,"Solar panel",1,new BigDecimal("110.00"),
            new BigDecimal("100.00"),new BigDecimal("9.09"),new BigDecimal("18.00"),
            new BigDecimal("18.00"),new BigDecimal("118.00"))));
    QuotationDocumentService service;

    @BeforeEach void setUp() {
        service=new QuotationDocumentService(quotations,renderer,Clock.fixed(now,ZoneOffset.UTC));
    }

    @Test void vendorDownloadUsesTheAuthorizedVendorRevisionProjection() {
        when(quotations.vendorDocument(42,3)).thenReturn(data);
        when(renderer.render(eq(data),anyString(),eq(now))).thenReturn("%PDF-vendor".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

        var result=service.vendor(42,3);

        assertThat(result.filename()).startsWith("quotation-42-revision-3-").endsWith(".pdf");
        assertThat(result.reference()).isNotBlank();
        assertThat(result.content()).startsWith("%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        verify(quotations).vendorDocument(42,3);
    }

    @Test void customerDownloadUsesTheAuthenticatedCustomerProjection() {
        when(quotations.customerDocument("customer@example.test",42,3)).thenReturn(data);
        when(renderer.render(eq(data),anyString(),eq(now))).thenReturn("%PDF-customer".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

        var result=service.customer("customer@example.test",42,3);

        assertThat(result.filename()).startsWith("quotation-42-revision-3-").endsWith(".pdf");
        assertThat(result.reference()).isNotBlank();
        assertThat(result.content()).startsWith("%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        verify(quotations).customerDocument("customer@example.test",42,3);
    }
}
