package com.saumya.pos.documents;

import com.saumya.pos.checkout.OnlineReceiptService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Creates an on-demand PDF copy using the existing customer-owned receipt and audit path. */
@Service
public class CustomerReceiptDocumentService {
    private final OnlineReceiptService receipts;
    private final ReceiptPdfRenderer renderer;
    private final Clock clock;

    public CustomerReceiptDocumentService(OnlineReceiptService receipts, ReceiptPdfRenderer renderer, Clock clock) {
        this.receipts = receipts;
        this.renderer = renderer;
        this.clock = clock;
    }

    public record Download(String reference, String filename, byte[] content) {}

    public Download generate(String username, String orderId) {
        // copy() resolves identity from the principal, requires a PAID customer-owned order,
        // loads saved snapshots and records the existing CUSTOMER_RECEIPT_COPY audit event.
        var saved = receipts.copy(username, orderId);
        String reference = UUID.randomUUID().toString();
        byte[] content = renderer.renderCustomerCopy(saved, reference, clock.instant());
        if (content.length < 5 || content.length > 2_000_000) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Receipt PDF exceeds the 2 MB document limit");
        }
        return new Download(reference,
            "receipt-" + saved.orderId() + "-customer-copy-" + reference + ".pdf", content);
    }
}
