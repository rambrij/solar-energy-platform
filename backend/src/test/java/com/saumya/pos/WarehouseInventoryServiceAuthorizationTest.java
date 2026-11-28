package com.saumya.pos;

import com.saumya.pos.inventory.WarehouseInventoryService;
import com.saumya.pos.security.AuthorizationContext;
import java.time.Clock;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WarehouseInventoryServiceAuthorizationTest {
    @Test
    void transferMutationWithoutTransferGrantIsDeniedBeforeAnyPersistenceOrStockChange() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        AuthorizationContext authorization=mock(AuthorizationContext.class);
        when(authorization.current()).thenReturn(new AuthorizationContext.Context(
            "alice",1L,10L,"1:alice",Set.of("INVENTORY_VIEW"),false,false,true));
        WarehouseInventoryService service=new WarehouseInventoryService(jdbc,authorization,
            Clock.systemUTC(),mock(org.springframework.context.ApplicationEventPublisher.class));

        assertThatThrownBy(()->service.createWarehouse("MAIN","Main Warehouse","approved setup"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .satisfies(error->assertThat(((org.springframework.web.server.ResponseStatusException)error)
                .getStatusCode().value()).isEqualTo(403));
        assertThatThrownBy(()->service.createTransfer("command-key-1","BRANCH",null,
            "BRANCH",null,20,7,3,"replenishment"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .satisfies(error->assertThat(((org.springframework.web.server.ResponseStatusException)error)
                .getStatusCode().value()).isEqualTo(403));
        verifyNoInteractions(jdbc);
    }

    @Test
    void inventoryTransferIsNotImpliedByInventoryViewOrReceipt() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        AuthorizationContext authorization=mock(AuthorizationContext.class);
        when(authorization.current()).thenReturn(new AuthorizationContext.Context(
            "alice",1L,10L,"1:alice",Set.of("INVENTORY_VIEW","INVENTORY_RECEIVE"),false,false,true));
        WarehouseInventoryService service=new WarehouseInventoryService(jdbc,authorization,
            Clock.systemUTC(),mock(org.springframework.context.ApplicationEventPublisher.class));

        assertThatThrownBy(()->service.createWarehouse("MAIN","Main Warehouse","approved setup"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(jdbc);
    }
}
