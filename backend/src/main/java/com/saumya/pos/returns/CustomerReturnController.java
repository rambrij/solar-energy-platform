package com.saumya.pos.returns;

import java.security.Principal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customer/orders/{orderId}/returns")
@PreAuthorize("hasAuthority('CUSTOMER')")
public class CustomerReturnController {
    private final CustomerReturnHistoryService history;

    public CustomerReturnController(CustomerReturnHistoryService history) {
        this.history = history;
    }

    @GetMapping
    public CustomerReturnHistoryService.History read(Principal actor, @PathVariable String orderId) {
        return history.history(actor.getName(), orderId);
    }
}
