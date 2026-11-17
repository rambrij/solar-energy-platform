package com.saumya.pos;

import com.saumya.pos.retail.BranchPolicyService;
import com.saumya.pos.retail.PosService;
import com.saumya.pos.retail.internal.BranchConfigurationRepository;
import com.saumya.pos.retail.internal.BranchSettingsEventRepository;
import com.saumya.pos.retail.internal.RegisterDirectoryRepository;
import com.saumya.pos.retail.internal.RetailSequenceRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class BranchPolicyServiceAuthorizationBoundaryTest {
    @Test
    void allBranchPolicyEntryPointsDenyBeforeRepositoryEventOrSequenceAccess() {
        var branches=mock(BranchConfigurationRepository.class);
        var retail=mock(PosService.class);
        var events=mock(BranchSettingsEventRepository.class);
        var sequences=mock(RetailSequenceRepository.class);
        var registers=mock(RegisterDirectoryRepository.class);
        var scope=new PosService.Scope(7,12,"cashier");
        when(retail.scope()).thenReturn(scope);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,"POS settings authorization denied"))
            .when(retail).authorize(eq(scope),eq("cashier"),any(String[].class));
        var service=new BranchPolicyService(branches,retail,events,sequences,registers,java.time.Clock.systemUTC());
        var settings=new BranchPolicyService.Settings("Outlet",0,false,null,null,false,14,
            false,false,false,false,"INR","Approved settings change");

        denied(()->service.find(7,12));
        denied(()->service.lock(7,12));
        denied(()->service.current("cashier"));
        denied(()->service.update("cashier",settings));
        denied(()->service.createRegister("cashier","Approved register creation"));

        verifyNoInteractions(branches,events,sequences,registers);
    }

    private static void denied(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status",HttpStatus.FORBIDDEN);
    }
}
