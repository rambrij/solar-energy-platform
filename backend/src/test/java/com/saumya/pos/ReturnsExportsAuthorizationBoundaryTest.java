package com.saumya.pos;

import com.saumya.pos.reporting.ReportExportService;
import com.saumya.pos.reporting.internal.ReportExportAuditRepository;
import com.saumya.pos.returns.CashRefundService;
import com.saumya.pos.returns.RefundHistoryService;
import com.saumya.pos.returns.ReturnService;
import com.saumya.pos.returns.internal.*;
import com.saumya.pos.retail.PosService;
import com.saumya.pos.inventory.InventoryService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ReturnsExportsAuthorizationBoundaryTest {
    private PosService deniedRetail() {
        return new PosService(null, null, null) {
            @Override public Scope scope() { return new Scope(7L, 12L, "cashier"); }
            @Override public void authorize(Scope scope, String expectedActor, String... permissions) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Denied before data access");
            }
        };
    }

    @Test
    void allCashRefundEntryPointsDenyBeforeRefundOrReturnRepositories() {
        var refunds=mock(CashRefundRepository.class);
        var amounts=mock(CashRefundItemRepository.class);
        var returns=mock(ReturnRepository.class);
        var returnedItems=mock(ReturnItemRepository.class);
        var events=mock(ReturnEventRepository.class);
        var sales=mock(ReturnSaleAccess.class);
        var allocation=mock(RefundAllocation.class);
        var rules=mock(ReturnRules.class);
        var service=new CashRefundService(refunds,amounts,returns,returnedItems,events,sales,
            deniedRetail(),allocation,rules,Clock.systemUTC());
        var payment=new CashRefundService.Pay("return-1","session-1","Refund approved",
            "0".repeat(64),"CASH_HANDED_TO_BUYER");

        denied(()->service.quote("return-1","session-1"));
        denied(()->service.pay("refund-key-123",payment));
        denied(()->service.get("refund-1"));
        denied(()->service.byReturn("return-1"));
        denied(service::list);
        denied(()->service.copy("refund-1"));

        verifyNoInteractions(refunds,amounts,returns,returnedItems,events,sales,allocation,rules);
    }

    @Test
    void allReturnWorkflowEntryPointsDenyBeforeSaleOrReturnRepositoriesAndInventory() {
        var cases=mock(ReturnRepository.class);
        var items=mock(ReturnItemRepository.class);
        var events=mock(ReturnEventRepository.class);
        var sales=mock(ReturnSaleAccess.class);
        var stock=mock(InventoryService.class);
        var rules=mock(ReturnRules.class);
        var service=new ReturnService(cases,items,events,sales,deniedRetail(),stock,rules,Clock.systemUTC());
        var create=new ReturnService.Create("order-1","session-1",
            List.of(new ReturnService.Item(11L,1)),"Return reason","ORIGINAL_RECEIPT","Proof checked");
        var review=new ReturnService.Review("APPROVED","Independent review");
        var inspect=new ReturnService.Inspect(List.of(
            new ReturnService.Inspection(11L,"SELLABLE","RESTOCK","Checked")));

        denied(()->service.eligibility("order-1"));
        denied(()->service.create("return-key-123",create));
        denied(()->service.review("return-1",review));
        denied(()->service.inspect("return-1",inspect));
        denied(()->service.get("return-1"));
        denied(()->service.list("REQUESTED"));

        verifyNoInteractions(cases,items,events,sales,stock,rules);
    }

    @Test
    void refundHistoryDeniesBeforeRefundRepositoryReads() {
        var refunds=mock(CashRefundRepository.class);
        var service=new RefundHistoryService(refunds,deniedRetail(),Clock.systemUTC());

        denied(()->service.history(LocalDate.now(),LocalDate.now(),"","",0,20));

        verifyNoInteractions(refunds);
    }

    @Test
    void salesAndRefundExportsDenyBeforeReportsAuditOrCsvGeneration() {
        var sales=mock(com.saumya.pos.checkout.SalesReportService.class);
        var refunds=mock(com.saumya.pos.returns.RefundReportService.class);
        var audits=mock(ReportExportAuditRepository.class);
        var csv=mock(com.saumya.pos.reporting.CsvWriter.class);
        var service=new ReportExportService(sales,refunds,deniedRetail(),audits,csv,Clock.systemUTC());
        var from=LocalDate.of(2026,11,1);
        var to=LocalDate.of(2026,11,17);

        denied(()->service.sales("cashier",from,to,"IN_STORE","Export requested"));
        denied(()->service.refunds("cashier",from,to,"Refund export requested"));

        verifyNoInteractions(sales,refunds,audits,csv);
    }

    private static void denied(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation)
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
    }
}
