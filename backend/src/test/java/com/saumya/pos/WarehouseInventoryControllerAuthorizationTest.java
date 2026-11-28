package com.saumya.pos;

import com.saumya.pos.marketplace.WarehouseInventoryController;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static org.assertj.core.api.Assertions.*;

class WarehouseInventoryControllerAuthorizationTest {
    @Test
    void everyWarehouseReadAndMutationHasItsDedicatedInventoryPermission() {
        List<String> readMethods=List.of("warehouses","warehouseStock","transfers","transfer");
        List<String> mutationMethods=List.of("createWarehouse","updateWarehouse","createTransfer","approve","dispatch","receive");
        for(String name:readMethods)assertPermission(name,"INVENTORY_VIEW");
        for(String name:mutationMethods)assertPermission(name,"INVENTORY_TRANSFER");
    }

    private void assertPermission(String methodName,String expected) {
        var method=java.util.Arrays.stream(WarehouseInventoryController.class.getDeclaredMethods())
            .filter(candidate->candidate.getName().equals(methodName)).findFirst().orElseThrow();
        assertThat(AnnotatedElementUtils.hasAnnotation(method,GetMapping.class)
            ||AnnotatedElementUtils.hasAnnotation(method,PostMapping.class)
            ||AnnotatedElementUtils.hasAnnotation(method,PatchMapping.class)).isTrue();
        PreAuthorize policy=AnnotatedElementUtils.findMergedAnnotation(method,PreAuthorize.class);
        assertThat(policy).as("method policy for %s",methodName).isNotNull();
        assertThat(policy.value()).contains("hasAuthority('"+expected+"')");
    }
}
