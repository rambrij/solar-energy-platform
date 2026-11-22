package com.saumya.pos;

import com.saumya.pos.catalog.CatalogService;
import com.saumya.pos.catalog.internal.ProductRepository;
import com.saumya.pos.security.AuthorizationContext;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CatalogServicePaymentReviewAuthorizationTest {
    @Test
    void paymentReviewProductLockRequiresManualVerifyBeforeRepositoryLookup() {
        var products = mock(ProductRepository.class);
        var auth = mock(AuthorizationContext.class);
        when(auth.current()).thenReturn(new AuthorizationContext.Context("cashier", 7L, 12L, String.valueOf(7L) + ":" + ("cashier"), java.util.Set.of("POS_VIEW"), false, false, true));
        var service = new CatalogService(null, products, null);
        ReflectionTestUtils.setField(service, "authorization", auth);

        assertThatThrownBy(() -> service.lockForSaleForPaymentReview(7L, 11L))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);

        verifyNoInteractions(products);
    }

    @Test
    void saleLockRequiresSaleGrantAndRejectsForgedTenantBeforeProductOrStockAccess() {
        var products=mock(ProductRepository.class);
        var stock=mock(com.saumya.pos.inventory.InventoryService.class);
        var auth=mock(AuthorizationContext.class);
        when(auth.current()).thenReturn(new AuthorizationContext.Context(
            "cashier",7L,12L,"7:cashier",java.util.Set.of("POS_VIEW"),false,false,true));
        var service=new CatalogService(null,products,stock);
        ReflectionTestUtils.setField(service,"authorization",auth);

        assertThatThrownBy(()->service.lockForSale(7L,11L))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status",HttpStatus.FORBIDDEN);
        assertThatThrownBy(()->service.requirePosStockLocation(8L,12L))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status",HttpStatus.FORBIDDEN);

        verifyNoInteractions(products,stock);
    }

    @Test
    void posCatalogueAndStockScopeRequirePosViewBeforeAnyProductOrInventoryRead() {
        var products=mock(ProductRepository.class);
        var stock=mock(com.saumya.pos.inventory.InventoryService.class);
        var auth=mock(AuthorizationContext.class);
        when(auth.current()).thenReturn(new AuthorizationContext.Context(
            "cashier",7L,12L,"7:cashier",java.util.Set.of("PRODUCT_VIEW"),false,false,true));
        var service=new CatalogService(null,products,stock);
        ReflectionTestUtils.setField(service,"authorization",auth);

        assertThatThrownBy(()->service.requirePosStockScope(7L,12L))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status",HttpStatus.FORBIDDEN);
        assertThatThrownBy(()->service.posProducts(7L,12L))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status",HttpStatus.FORBIDDEN);

        verifyNoInteractions(products,stock);
    }
}
