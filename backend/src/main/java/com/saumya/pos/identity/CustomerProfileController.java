package com.saumya.pos.identity;

import java.security.Principal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customer/profile")
@PreAuthorize("hasAuthority('CUSTOMER')")
public class CustomerProfileController {
    private final CustomerProfileService profiles;
    public CustomerProfileController(CustomerProfileService profiles) { this.profiles = profiles; }
    @GetMapping public Object read(Principal actor) { return profiles.read(actor.getName()); }
    @PutMapping public Object update(Principal actor, @Valid @RequestBody CustomerProfileService.Update request) {
        return profiles.update(actor.getName(), request);
    }
}
