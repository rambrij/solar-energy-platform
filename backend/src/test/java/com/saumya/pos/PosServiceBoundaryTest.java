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
        when(auth.current()).thenReturn(new AuthorizationContext.Context("cashier",7L,12L,
            java.util.Set.of("POS_VIEW"),false,false,true));
        var service=new PosService(db,null,auth);
        var scope=new PosService.Scope(7,12,"cashier");
        assertThatThrownBy(()->service.lockOutlet(scope)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.allocateReceipt(scope)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.draftAudit(scope,"DRAFT_RESERVE","draft-1")).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(db);
    }
}
