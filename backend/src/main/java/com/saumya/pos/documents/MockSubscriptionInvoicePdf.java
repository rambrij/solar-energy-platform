package com.saumya.pos.documents;

import com.saumya.pos.subscription.TenantSubscriptionService.InvoiceView;
import java.io.ByteArrayOutputStream;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Font;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;

public final class MockSubscriptionInvoicePdf {
    private MockSubscriptionInvoicePdf() {}

    public static byte[] render(InvoiceView invoice) {
        var output = new ByteArrayOutputStream();
        var document = new Document();
        try {
            PdfWriter.getInstance(document, output);
            document.addTitle("Mock subscription invoice " + invoice.invoiceNumber());
            document.open();
            document.add(new Paragraph("MOCK SUBSCRIPTION INVOICE", new Font(Font.HELVETICA, 18, Font.BOLD)));
            document.add(new Paragraph("Simulation only. No funds collected. Not a GST or tax invoice."));
            document.add(new Paragraph("Invoice: " + invoice.invoiceNumber()
                + "\nTenant: " + invoice.tenantId()
                + "\nSubscription: " + invoice.subscriptionId()
                + "\nBilling period: " + invoice.periodStart() + " to " + invoice.periodEnd()
                + "\nIssued (UTC): " + invoice.issuedAt()
                + "\nDue (UTC): " + invoice.dueAt()
                + "\nStatus at download: " + invoice.status()));
            document.add(new Paragraph("Subtotal: " + invoice.currency() + " " + invoice.subtotal().toPlainString()
                + "\nSimulated tax: " + invoice.currency() + " " + invoice.taxAmount().toPlainString()
                + "\nTotal: " + invoice.currency() + " " + invoice.total().toPlainString()));
        } catch (DocumentException e) {
            throw new IllegalStateException("Mock subscription invoice PDF generation failed", e);
        } finally {
            document.close();
        }
        return output.toByteArray();
    }
}
