package com.saumya.pos;

import com.saumya.pos.retail.PosService;
import com.saumya.pos.security.AuthorizationContext;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PosServiceBoundaryTest {
    @Test void internalLockReceiptAndAuditPrimitivesRequireTrustedOperationPermissionBeforeJdbc(){
        var db=mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var auth=mock(AuthorizationContext.class);
        when(auth.current()).thenReturn(new AuthorizationContext.Context("cashier", 7L, 12L, String.valueOf(7L) + ":" + ("cashier"), java.util.Set.of("PRODUCT_VIEW"), false, false, true));
        var service=new PosService(db,null,auth);
        var scope=new PosService.Scope(7,12,"cashier");
        assertThatThrownBy(()->service.lockOutlet(scope)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.lockInventoryOutlet(scope)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.allocateReceipt(scope)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.draftAudit(scope,"DRAFT_RESERVE","draft-1")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.draftAudit(scope,"SALE_COMPLETE","draft-1")).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(db);
    }

    @Test void supervisorSessionFinalizationRequiresManualVerificationBeforeJdbc(){
        var db=mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var auth=mock(AuthorizationContext.class);
        when(auth.current()).thenReturn(new AuthorizationContext.Context("manager", 7L, 12L, String.valueOf(7L) + ":" + ("manager"), java.util.Set.of("POS_VIEW"), false, false, true));
        var service=new PosService(db,null,auth);
        var supervisorScope=new PosService.Scope(7,12,"manager");

        assertThatThrownBy(()->service.requireOpenForPaymentReview(supervisorScope,"cashier","session-1"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Missing required POS operation permission");

        verifyNoInteractions(db);
    }


    @Test void publicSessionVarianceMovementAndReconciliationMethodsDenyBeforeJdbc(){
        var db=mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var auth=mock(AuthorizationContext.class);
        when(auth.current()).thenReturn(new AuthorizationContext.Context("cashier", 7L, 12L, String.valueOf(7L) + ":" + ("cashier"), java.util.Set.of("PRODUCT_VIEW"), false, false, true));
        var service=new PosService(db,null,auth);
        var scope=new PosService.Scope(7,12,"cashier");
        var open=new PosService.OpenSession(1,java.math.BigDecimal.ZERO);
        var movement=new PosService.CashMovement(PosService.MovementType.CASH_IN,
            java.math.BigDecimal.ONE,"Float");

        assertThatThrownBy(()->service.products(scope)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.activeSessions(scope)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.sessionDetails(scope,"session-1")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.resume(scope,"session-1")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.open(scope,open)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.closeStatus(scope,"session-1")).isInstanceOf(ResponseStatusException.class);

        assertThatThrownBy(()->service.close(scope,"session-1",java.math.BigDecimal.ZERO))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.close(scope,"session-1",java.math.BigDecimal.ZERO,"Counted"))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.close(scope,"session-1",java.math.BigDecimal.ZERO,"Counted","request-1"))
            .isInstanceOf(ResponseStatusException.class);

        assertThatThrownBy(()->service.pendingVariances(scope)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.reviewVariance(scope,"session-1","request-1","Reviewed",
            PosService.ReviewAction.APPROVE)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.cashMovement(scope,"session-1","movement-1",movement))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.reconciliation(scope,"session-1"))
            .isInstanceOf(ResponseStatusException.class);

        verifyNoInteractions(db);
    }


    @Test void tenderMutationSessionGuardRequiresItsExactPermissionBeforeJdbc(){
        var db=mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var auth=mock(AuthorizationContext.class);
        when(auth.current()).thenReturn(new AuthorizationContext.Context("cashier", 7L, 12L, String.valueOf(7L) + ":" + ("cashier"), java.util.Set.of("POS_VIEW"), false, false, true));
        var service=new PosService(db,null,auth);
        var scope=new PosService.Scope(7,12,"cashier");
        assertThatThrownBy(()->service.requireOpenForTender(scope,"session-1","POS_TENDER_EXTERNAL_RECORD"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Missing required POS operation permission");
        verifyNoInteractions(db);
    }

}
