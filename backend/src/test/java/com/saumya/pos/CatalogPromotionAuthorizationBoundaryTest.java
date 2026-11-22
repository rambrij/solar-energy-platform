package com.saumya.pos;

import com.saumya.pos.catalog.*;
import com.saumya.pos.catalog.internal.*;
import com.saumya.pos.checkout.SaleDiscountService;
import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.shared.BillPricing;
import com.saumya.pos.shared.PromotionPricing;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CatalogPromotionAuthorizationBoundaryTest {
    @AfterEach void clearSecurity(){SecurityContextHolder.clearContext();}

    private static AuthorizationContext vendorWithout(Set<String> permissions) {
        var auth=mock(AuthorizationContext.class);
        when(auth.current()).thenReturn(new AuthorizationContext.Context("cashier", 7L, 12L, String.valueOf(7L) + ":" + ("cashier"), permissions, false, false, true));
        return auth;
    }

    @Test
    void discountPolicyReadAndWriteRequireTheirOwnGrantBeforeRepositoryOrPricingAccess() {
        var policies=mock(DiscountPolicyRepository.class);
        var pricing=mock(BillPricing.class);
        var auth=vendorWithout(Set.of());
        var service=new DiscountPolicyService(policies,pricing,auth);

        denied(()->service.maximum(7L));
        denied(()->service.configure(7L,new BigDecimal("10")));

        verifyNoInteractions(policies,pricing);
    }

    @Test
    void discountPolicyRejectsCrossTenantReadAndWriteBeforeRepositoryOrPricingAccess() {
        var policies=mock(DiscountPolicyRepository.class);
        var pricing=mock(BillPricing.class);
        var auth=vendorWithout(Set.of("POS_VIEW","POS_DISCOUNT_OVERRIDE"));
        var service=new DiscountPolicyService(policies,pricing,auth);

        denied(()->service.maximum(8L));
        denied(()->service.configure(8L,new BigDecimal("10")));

        verifyNoInteractions(policies,pricing);
    }

    @Test
    void promotionApprovalRejectsMissingGrantsBeforeProductPromotionOrAuditRepositories() {
        var promotions=mock(PromotionRepository.class);
        var audit=mock(PromotionAuditRepository.class);
        var products=mock(ProductRepository.class);
        var pricing=mock(PromotionPricing.class);
        var auth=vendorWithout(Set.of("POS_VIEW"));
        var service=new PromotionService(promotions,audit,products,pricing,Clock.systemUTC());
        org.springframework.test.util.ReflectionTestUtils.setField(service,"authorization",auth);

        denied(()->service.approve(7L,"cashier",11L,
            new PromotionService.Change(new BigDecimal("10"),"Approved reason")));

        verifyNoInteractions(promotions,audit,products,pricing);
    }

    @Test
    void promotionHistoryRejectsMissingApprovalGrantsBeforeProductOrAuditLookup() {
        var audit=mock(PromotionAuditRepository.class);
        var products=mock(ProductRepository.class);
        var auth=vendorWithout(Set.of("POS_VIEW","CATALOG_PROMOTION_MANAGE"));
        var service=new PromotionHistoryService(audit,products);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"authorization",auth);

        denied(()->service.history(7L,11L,0,20));

        verifyNoInteractions(audit,products);
    }

    @Test
    void applyingDiscountWithoutPosDiscountGrantStopsBeforePolicyLookup() {
        var policy=mock(DiscountPolicyService.class);
        var pricing=mock(BillPricing.class);
        var service=new SaleDiscountService(policy,pricing);
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("cashier",null,List.of()));

        assertThatThrownBy(()->service.calculate(7L,
            List.of(new BillPricing.Input(11L,BigDecimal.TEN,1,BigDecimal.ZERO,BigDecimal.ZERO)),
            new SaleDiscountService.Request(Map.of(),new BigDecimal("10"),"Approved reason")))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status",HttpStatus.FORBIDDEN);

        verifyNoInteractions(policy);
    }

    private static void denied(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status",HttpStatus.FORBIDDEN);
    }
}
