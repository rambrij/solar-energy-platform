package com.saumya.pos;

import com.saumya.pos.retail.BranchProvisioningServiceImpl;
import com.saumya.pos.retail.internal.BranchConfigurationRepository;
import com.saumya.pos.retail.internal.RetailSequenceRepository;
import com.saumya.pos.security.AuthorizationContext;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BranchProvisioningServiceTest {
    @Test void directInitialBranchProvisioningRequiresPlatformTenantCreateBeforeSequenceOrWrite(){
        var branches=mock(BranchConfigurationRepository.class);
        var sequences=mock(RetailSequenceRepository.class);
        var authorization=mock(AuthorizationContext.class);
        when(authorization.current()).thenReturn(new AuthorizationContext.Context("vendor-admin", 7L, 12L, String.valueOf(7L) + ":" + ("vendor-admin"), java.util.Set.of("POS_VIEW"), false, false, true));
        var service=new BranchProvisioningServiceImpl(branches,sequences,authorization);
        assertThatThrownBy(()->service.createInitialBranch(7,"Main Branch"))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(branches,sequences);
    }
}
