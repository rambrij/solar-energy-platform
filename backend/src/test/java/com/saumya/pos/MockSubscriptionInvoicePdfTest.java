package com.saumya.pos;

import com.saumya.pos.documents.MockSubscriptionInvoicePdf;
import com.saumya.pos.subscription.TenantSubscriptionService.InvoiceView;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import static org.assertj.core.api.Assertions.assertThat;

class MockSubscriptionInvoicePdfTest {
    @Test
    void rendersSavedAmountsAndSimulationLabel() throws Exception {
        var invoice=new InvoiceView(1L,7L,12L,"MOCK-SUB-12-2026-11-01",
            LocalDate.parse("2026-11-01"),LocalDate.parse("2026-12-01"),"INR","OPEN",
            new BigDecimal("19.50"),new BigDecimal("0.00"),new BigDecimal("19.50"),
            Instant.EPOCH,Instant.EPOCH,null);

        try(var reader=new PdfReader(MockSubscriptionInvoicePdf.render(invoice))){
            assertThat(new PdfTextExtractor(reader).getTextFromPage(1))
                .contains("MOCK SUBSCRIPTION INVOICE","Not a GST or tax invoice","MOCK-SUB-12-2026-11-01",
                    "2026-11-01 to 2026-12-01","Total: INR 19.50","Status at download: OPEN");
        }
    }
}
