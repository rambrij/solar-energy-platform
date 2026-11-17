package com.saumya.pos;

import com.saumya.pos.cart.CartService;
import com.saumya.pos.cart.internal.CartItemRepository;
import com.saumya.pos.cart.internal.CartRepository;
import com.saumya.pos.catalog.CatalogService;
import com.saumya.pos.identity.CustomerAccess;
import com.saumya.pos.inventory.InventoryService;
import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.shared.Pricing;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CartServiceAuthorizationBoundaryTest {
    @Test
    void everyPublicCartMethodRejectsVendorBeforeCustomerOrCartDataAccess() {
        var carts=mock(CartRepository.class);
        var items=mock(CartItemRepository.class);
        var customers=mock(CustomerAccess.class);
        var catalogue=mock(CatalogService.class);
        var stock=mock(InventoryService.class);
        var pricing=mock(Pricing.class);
        var authorization=mock(AuthorizationContext.class);
        when(authorization.current()).thenReturn(new AuthorizationContext.Context(
            "vendor",7L,12L,Set.of("POS_VIEW"),false,false,true));
        var service=new CartService(carts,items,customers,catalogue,stock,pricing);
        ReflectionTestUtils.setField(service,"authorization",authorization);

        denied(()->service.cart(101L));
        denied(()->service.put(101L,new CartService.Item("vendor-seven",11L,1)));
        denied(()->service.remove(101L,11L));
        denied(()->service.switchVendor(101L,new CartService.Switch("cart-1","vendor-eight",21L,1)));
        denied(()->service.quote(101L));
        denied(()->service.archivePurchased(101L,7L,Map.of(11L,1)));
        denied(()->service.history(101L));
        denied(()->service.restore(101L,new CartService.Restore("archived-cart",null)));

        verify(authorization,times(8)).current();
        verifyNoInteractions(carts,items,customers,catalogue,stock,pricing);
    }

    private static void denied(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status",HttpStatus.FORBIDDEN);
    }
}
