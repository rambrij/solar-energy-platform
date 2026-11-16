package com.saumya.pos.tenant;

import com.saumya.pos.tenant.internal.Tenant;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TenantLifecycleTest {
    @Test void supportsReviewActivationSuspensionAndReactivationButNeverReopensClosedTenants(){
        Instant now=Instant.parse("2026-11-16T00:00:00Z");
        var tenant=new Tenant(9,"solar-nine","Solar Nine","Solar Nine Pvt Ltd",null,null,null,null,"admin@solar.test",now,now);
        assertThat(tenant.lifecycleStatus()).isEqualTo(TenantStatus.PENDING);
        tenant.transitionTo(TenantStatus.UNDER_REVIEW,now.plusSeconds(1));
        tenant.transitionTo(TenantStatus.ACTIVE,now.plusSeconds(2));
        tenant.transitionTo(TenantStatus.SUSPENDED,now.plusSeconds(3));
        tenant.transitionTo(TenantStatus.ACTIVE,now.plusSeconds(4));
        assertThat(tenant.active()).isTrue();
        tenant.transitionTo(TenantStatus.CLOSED,now.plusSeconds(5));
        assertThat(tenant.active()).isFalse();
        assertThatThrownBy(()->tenant.transitionTo(TenantStatus.ACTIVE,now.plusSeconds(6)))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("Invalid tenant lifecycle transition");
    }
    @Test void rejectsSkippingUnderReviewAndRepeatedInvalidTransitions(){
        Instant now=Instant.now();
        var tenant=new Tenant(3,"solar-three","Solar Three","Solar Three Ltd",null,null,null,null,"admin@solar.test",now,now);
        assertThatThrownBy(()->tenant.transitionTo(TenantStatus.ACTIVE,now.plusSeconds(1))).isInstanceOf(IllegalStateException.class);
        tenant.transitionTo(TenantStatus.UNDER_REVIEW,now.plusSeconds(2));
        assertThat(tenant.transitionTo(TenantStatus.UNDER_REVIEW,now.plusSeconds(3))).isFalse();
    }
}
