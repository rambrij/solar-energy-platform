package com.saumya.pos.identity;

import com.saumya.pos.security.AuthorizationContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import com.saumya.pos.identity.internal.GlobalUserRepository;
import com.saumya.pos.identity.internal.PlatformGrantRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@RestController @RequestMapping("/api/v1/platform/identities")
@PreAuthorize("hasAuthority('PLATFORM_IDENTITY_MANAGE')")
public class PlatformIdentityController {
    private final GlobalUserRepository users;private final PlatformGrantRepository grants;
    private final PlatformMfaService mfa;private final AuthorizationContext auth;
    public PlatformIdentityController(GlobalUserRepository users,PlatformGrantRepository grants,PlatformMfaService mfa,AuthorizationContext auth){
        this.users=users;this.grants=grants;this.mfa=mfa;this.auth=auth;
    }
    public record Identity(String username,boolean mfaEnrolled,int failedMfaAttempts,String lockedUntil,List<String> permissions){}
    public record EnrollmentRequest(@NotBlank @Size(max=500) String reason){}
    private AuthorizationContext.Context require(){var c=auth.current();if(!c.isPlatformUser())throw new ResponseStatusException(FORBIDDEN,"Platform identity required");c.requirePermission("PLATFORM_IDENTITY_MANAGE");return c;}
    @GetMapping public Object list(){
        require();
        return grants.usernames().stream().map(username->{
            var user=users.findByUsername(username).orElseThrow();
            return new Identity(username,user.platformMfaEnrolled(),user.platformMfaFailedAttempts(),
                user.platformMfaLockedUntil()==null?null:user.platformMfaLockedUntil().toString(),grants.permissions(username));
        }).toList();
    }
    @PostMapping("/{username}/mfa-enrollment")
    public ResponseEntity<?> issueEnrollment(@PathVariable String username,@Valid @RequestBody EnrollmentRequest request){
        require();
        var result=mfa.issueEnrollment(username,request.reason());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}
