package com.saumya.pos;

import com.saumya.pos.checkout.ReturnSaleAccess;
import com.saumya.pos.checkout.internal.DraftLineRepository;
import com.saumya.pos.checkout.internal.DraftRepository;
import com.saumya.pos.retail.BranchPolicyService;
import com.saumya.pos.security.AuthorizationContext;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReturnSaleAccessAuthorizationBoundaryTest {
    private record Fixture(
        ReturnSaleAccess service,
        DraftRepository orders,
        DraftLineRepository items,
        BranchPolicyService policies
    ) {}

    private Fixture fixture(AuthorizationContext.Context actor) {
        var authorization = mock(AuthorizationContext.class);
        when(authorization.current()).thenReturn(actor);
        return fixture(authorization);
    }

    private Fixture fixture(AuthorizationContext authorization) {
        var orders = mock(DraftRepository.class);
        var items = mock(DraftLineRepository.class);
        var policies = mock(BranchPolicyService.class);
        var service = new ReturnSaleAccess(orders, items, policies, authorization);
        return new Fixture(service, orders, items, policies);
    }

    private static AuthorizationContext.Context vendor(Set<String> permissions) {
        return new AuthorizationContext.Context("cashier", 7L, 12L, permissions, false, false, true);
    }

    @Test
    void directReadRejectsMissingReturnPermissionBeforeOrderOrPolicyLookup() {
        var f = fixture(vendor(Set.of("POS_VIEW", "POS_RETURN_VIEW")));

        forbidden(() -> f.service().read(7, 12, "order-1"));
        verifyNoInteractions(f.orders(), f.items(), f.policies());
    }

    @Test
    void directLockRejectsMissingOperationPermissionBeforeOrderOrPolicyLookup() {
        var f = fixture(vendor(Set.of("POS_VIEW", "POS_RETURN_VIEW")));

        forbidden(() -> f.service().lock(7, 12, "order-1"));
        verifyNoInteractions(f.orders(), f.items(), f.policies());
    }

    @Test
    void cashSaleRequiresAllRefundPermissionsBeforeOrderOrPolicyLookup() {
        var f = fixture(vendor(Set.of("POS_VIEW", "POS_REFUND_VIEW", "POS_REFUND_APPROVE")));

        forbidden(() -> f.service().cashSale(7, 12, "order-1"));
        verifyNoInteractions(f.orders(), f.items(), f.policies());
    }

    @Test
    void callerSuppliedTenantAndBranchCannotSelectAnotherVendorScope() {
        var f = fixture(vendor(Set.of(
            "POS_VIEW", "POS_RETURN_VIEW", "POS_RETURN_CREATE",
            "POS_REFUND_VIEW", "POS_REFUND_APPROVE", "POS_REFUND_CASH_RECORD"
        )));

        forbidden(() -> f.service().read(8, 12, "order-1"));
        forbidden(() -> f.service().lock(7, 13, "order-1"));
        forbidden(() -> f.service().cashSale(8, 12, "order-1"));
        verifyNoInteractions(f.orders(), f.items(), f.policies());
    }

    @Test
    void customerAndPlatformActorsCannotUseVendorReturnDataPlane() {
        var customer = fixture(new AuthorizationContext.Context(
            "buyer", null, null,
            Set.of("CUSTOMER", "POS_VIEW", "POS_RETURN_VIEW", "POS_RETURN_CREATE"),
            false, true, false
        ));
        forbidden(() -> customer.service().read(7, 12, "order-1"));
        forbidden(() -> customer.service().lock(7, 12, "order-1"));

        var platform = fixture(new AuthorizationContext.Context(
            "platform-admin", null, null,
            Set.of("PLATFORM_TENANT_VIEW", "POS_VIEW", "POS_RETURN_VIEW", "POS_RETURN_CREATE"),
            true, false, false
        ));
        forbidden(() -> platform.service().read(7, 12, "order-1"));
        forbidden(() -> platform.service().cashSale(7, 12, "order-1"));

        verifyNoInteractions(
            customer.orders(), customer.items(), customer.policies(),
            platform.orders(), platform.items(), platform.policies()
        );
    }

    private static void forbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
            .isInstanceOfSatisfying(ResponseStatusException.class, error ->
                assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
