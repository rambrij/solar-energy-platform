package com.saumya.pos.identity;

import com.saumya.pos.security.AuthorizationContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/v1/platform/identities")
@PreAuthorize("hasAuthority('PLATFORM_IDENTITY_MANAGE')")
public class PlatformIdentityController {
    private final PlatformIdentityQueryService identities;
    private final PlatformMfaService mfa;
    private final AuthorizationContext auth;

    public PlatformIdentityController(PlatformIdentityQueryService identities, PlatformMfaService mfa,
                                      AuthorizationContext auth) {
        this.identities = identities;
        this.mfa = mfa;
        this.auth = auth;
    }

    public record EnrollmentRequest(@NotBlank @Size(max=500) String reason) {}

    private void require() {
        var context = auth.current();
        if (!context.isPlatformUser()) throw new ResponseStatusException(FORBIDDEN, "Platform identity required");
        context.requirePermission("PLATFORM_IDENTITY_MANAGE");
    }

    @GetMapping
    public Object list() {
        require();
        return identities.list();
    }

    @PostMapping("/{username}/mfa-enrollment")
    public ResponseEntity<?> issueEnrollment(@PathVariable String username,
                                               @Valid @RequestBody EnrollmentRequest request) {
        require();
        var result = mfa.issueEnrollment(username, request.reason());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}
