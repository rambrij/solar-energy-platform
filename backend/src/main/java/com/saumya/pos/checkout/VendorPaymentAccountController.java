package com.saumya.pos.checkout;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/vendor/payment-accounts")
public class VendorPaymentAccountController {
    private final VendorPaymentAccountService accounts;
    public VendorPaymentAccountController(VendorPaymentAccountService accounts){this.accounts=accounts;}

    public record SubmitRequest(@NotBlank @Size(max=40) String providerKey,
        @NotBlank @Size(max=160) String merchantAccountReference,
        @PositiveOrZero long expectedVersion,@NotBlank @Size(max=500) String reason){}

    @GetMapping
    @PreAuthorize("hasAuthority('TENANT_PROFILE_VIEW')")
    Object list(){return accounts.list();}

    @PutMapping
    @PreAuthorize("hasAuthority('TENANT_PROFILE_UPDATE')")
    Object submit(@Valid @RequestBody SubmitRequest request){
        return accounts.submit(new VendorPaymentAccountService.Submit(request.providerKey(),
            request.merchantAccountReference(),request.expectedVersion(),request.reason()));
    }
}
