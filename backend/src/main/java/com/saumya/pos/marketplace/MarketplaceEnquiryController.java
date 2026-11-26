package com.saumya.pos.marketplace;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
public class MarketplaceEnquiryController {
    private final MarketplaceEnquiryService service;
    public MarketplaceEnquiryController(MarketplaceEnquiryService service){this.service=service;}

    public record Request(@NotBlank @Size(max=120) String contactName,
            @NotBlank @Email @Size(max=254) String contactEmail,
            @Size(max=30) @Pattern(regexp="[+0-9() .-]*") String contactPhone,
            @NotBlank @Size(max=2000) String message,
            @Positive Long productId,@AssertTrue boolean consent,boolean linkToAccount) {}

    @GetMapping("/api/v1/marketplace/csrf")
    @PreAuthorize("permitAll()")
    public Map<String,String> csrf(CsrfToken token){return Map.of("token",token.getToken());}

    @PostMapping("/api/v1/marketplace/vendors/{slug}/enquiries")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("permitAll()")
    public MarketplaceEnquiryService.Receipt submit(@PathVariable String slug,@Valid @RequestBody Request request,
            Principal actor,HttpServletRequest servletRequest) {
        return service.submit(slug,new MarketplaceEnquiryService.Submission(request.contactName(),
            request.contactEmail(),request.contactPhone(),request.message(),request.productId(),
            request.consent(),request.linkToAccount()),servletRequest.getRemoteAddr(),
            actor==null?null:actor.getName());
    }
}
