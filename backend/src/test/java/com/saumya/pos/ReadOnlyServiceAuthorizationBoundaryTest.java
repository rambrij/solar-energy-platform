package com.saumya.pos;

import com.saumya.pos.checkout.*;
import com.saumya.pos.checkout.internal.*;
import com.saumya.pos.checkout.internal.CashierSalesRepository;
import com.saumya.pos.returns.*;
import com.saumya.pos.returns.internal.*;
import com.saumya.pos.retail.*;
import com.saumya.pos.retail.internal.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReadOnlyServiceAuthorizationBoundaryTest {
    private PosService deniedRetail() {
        var retail=mock(PosService.class);
        var scope=new PosService.Scope(7,12,"cashier");
        when(retail.scope()).thenReturn(scope);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,"Denied before query"))
            .when(retail).authorize(eq(scope),anyString(),any(String[].class));
        return retail;
    }

    @Test
    void salesReportsAndCashierSalesDenyBeforeAggregateRepositories() {
        var salesRows=mock(SalesReportRepository.class);
        var cashierRows=mock(CashierSalesRepository.class);
        var retail=deniedRetail();
        var sales=new SalesReportService(salesRows,retail,Clock.systemUTC());
        var cashier=new CashierSalesService(cashierRows,retail,Clock.systemUTC());
        var from=LocalDate.of(2026,11,1);
        var to=LocalDate.of(2026,11,17);

        denied(()->sales.report("cashier",from,to,"ALL"));
        denied(()->cashier.report("cashier",from,to,0,20));

        verifyNoInteractions(salesRows,cashierRows);
    }

    @Test
    void refundReportsDenyBeforeAggregateRepositoryReads() {
        var rows=mock(RefundReportRepository.class);
        var service=new RefundReportService(rows,deniedRetail(),Clock.systemUTC());

        denied(()->service.report(LocalDate.of(2026,11,1),LocalDate.of(2026,11,17)));

        verifyNoInteractions(rows);
    }

    @Test
    void returnQueueAndAuditHistoryDenyBeforeReturnOrEventRepositoryReads() {
        var returns=mock(ReturnRepository.class);
        var events=mock(ReturnEventRepository.class);
        var retail=deniedRetail();
        var queue=new ReturnQueueService(returns,retail,Clock.systemUTC());
        var audit=new ReturnAuditService(returns,events,retail);

        denied(()->queue.queue("ALL",LocalDate.of(2026,11,1),LocalDate.of(2026,11,17),"",0,20));
        denied(()->audit.history("return-1",0,20));

        verifyNoInteractions(returns,events);
    }

    @Test
    void registerAndShiftHistoryDenyBeforeDirectoryOrHistoryQueries() {
        var registers=mock(RegisterDirectoryRepository.class);
        var shifts=mock(ClosedShiftRepository.class);
        var retail=deniedRetail();
        var directory=new RegisterDirectoryService(registers,retail);
        var history=new ShiftHistoryService(shifts,retail,Clock.systemUTC());

        denied(()->directory.directory("cashier",0,20));
        denied(()->history.history("cashier",LocalDate.of(2026,11,1),
            LocalDate.of(2026,11,17),"",0,20));

        verifyNoInteractions(registers,shifts);
    }

    @Test
    void receiptAndReprintDenyBeforeOrderLineInvoiceTenderOrBuyerReads() {
        var orders=mock(DraftRepository.class);
        var lines=mock(DraftLineRepository.class);
        var invoices=mock(InvoiceSnapshotRepository.class);
        var tenders=mock(PaymentAttemptRepository.class);
        var buyers=mock(BuyerDetailsService.class);
        var retail=deniedRetail();
        var service=new CheckoutReceiptService(orders,lines,invoices,tenders,buyers,retail);
        var requested=new PosService.Scope(7,12,"cashier");

        denied(()->service.receipt(requested,"order-1"));
        denied(()->service.reprint(requested,"order-1"));

        verifyNoInteractions(orders,lines,invoices,tenders,buyers);
    }

    private static void denied(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status",HttpStatus.FORBIDDEN);
    }
}
