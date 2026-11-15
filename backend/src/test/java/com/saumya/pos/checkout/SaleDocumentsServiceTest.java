package com.saumya.pos.checkout;
import com.saumya.pos.checkout.internal.*;
import com.saumya.pos.catalog.CatalogService;
import com.saumya.pos.retail.PosService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class SaleDocumentsServiceTest {
    private DraftOrder order() {
        return new DraftOrder("order", 1, null, 2L, "alice", "shift", "IN_STORE", "key", "hash", new BigDecimal("110"), 2, "owner", Instant.now(), false);
    }
    
    @Test
    void unpaidOrWrongOutletCannotIssueFinancialDocuments() {
        var order = order();
        var service = new SaleDocumentsService(null, null, null);
        assertThatThrownBy(() -> service.issue(new PosService.Scope(1, 2, "alice"), order, new PosService.ReceiptAllocation(3, false, "Outlet"), false, false)).hasMessageContaining("does not match");
        order.pay(new BigDecimal("120"), 3, false, Instant.now());
        assertThatThrownBy(() -> service.issue(new PosService.Scope(1, 9, "alice"), order, new PosService.ReceiptAllocation(3, false, "Outlet"), false, false)).hasMessageContaining("does not match");
    }
    
    @Test
    void issuanceSnapshotsIdentityAndSavesOneImmutableInvoice() {
        var saved = new java.util.ArrayList<InvoiceSnapshot>();
        var repo = (InvoiceSnapshotRepository) java.lang.reflect.Proxy.newProxyInstance(
                InvoiceSnapshotRepository.class.getClassLoader(), new Class<?>[] { InvoiceSnapshotRepository.class
                }, (p, m, a) -> {
                    if (m.getName().equals("findById")) return java.util.Optional.empty();
                    assertThat(m.getName()).isEqualTo("saveAndFlush");
                    saved.add((InvoiceSnapshot) a[0]);
                    return a[0];
                }
        );
        var catalog = new CatalogService(null, null, null) {
            public VendorInfo vendor(long tenant) {
                return new VendorInfo(1, "vendor", "Saved seller", true);
            }
        };
        var actions = new java.util.ArrayList<String>();
        var retail = new PosService(null, null, null) {
            public void draftAudit(Scope scope, String action, String id) {
                actions.add(action);
            }
        };
        var order = order();
        order.pay(new BigDecimal("120"), 3, true, Instant.now());
        var service = new SaleDocumentsService(catalog, repo, retail);
        service.issue(new PosService.Scope(1, 2, "alice"), order, new PosService.ReceiptAllocation(3, false, "Saved outlet"), true, false);
        assertThat(order.receiptSeller()).isEqualTo("Saved seller");
        assertThat(saved.getFirst().number()).isEqualTo("INV-2-3");
        assertThat(saved.getFirst().total()).isEqualByComparingTo("110");
        assertThat(actions).containsExactly("INVOICE_ISSUE", "SALE_COMPLETE");
        assertThatThrownBy(() -> service.issue(new PosService.Scope(1, 2, "alice"), order, new PosService.ReceiptAllocation(4, false, "Changed outlet"), true, false)).hasMessageContaining("immutable");
        assertThat(saved).hasSize(1);
    }
}
