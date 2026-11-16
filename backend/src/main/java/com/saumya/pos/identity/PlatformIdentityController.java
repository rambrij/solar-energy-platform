package com.saumya.pos.identity;

import com.saumya.pos.security.AuthorizationContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@RestController @RequestMapping("/api/v1/platform/identities")
@PreAuthorize("hasAuthority('PLATFORM_IDENTITY_MANAGE')")
public class PlatformIdentityController {
    private final JdbcTemplate db;private final PlatformMfaService mfa;private final AuthorizationContext auth;
    public PlatformIdentityController(JdbcTemplate db,PlatformMfaService mfa,AuthorizationContext auth){this.db=db;this.mfa=mfa;this.auth=auth;}
    public record Identity(String username,boolean mfaEnrolled,int failedMfaAttempts,String lockedUntil,List<String> permissions){}
    public record EnrollmentRequest(@NotBlank @Size(max=500) String reason){}
    private AuthorizationContext.Context require(){var c=auth.current();if(!c.isPlatformUser())throw new ResponseStatusException(FORBIDDEN,"Platform identity required");c.requirePermission("PLATFORM_IDENTITY_MANAGE");return c;}
    @GetMapping public Object list(){
        require();
        return db.queryForList("""
            SELECT u.username,u.platform_mfa_enrolled_at,u.platform_mfa_failed_attempts,u.platform_mfa_locked_until
              FROM users u JOIN platform_user_permissions p ON p.username=u.username
             GROUP BY u.username,u.platform_mfa_enrolled_at,u.platform_mfa_failed_attempts,u.platform_mfa_locked_until
             ORDER BY u.username
            """).stream().map(row->{
                String username=(String)row.get("username");
                List<String> permissions=db.queryForList("SELECT permission FROM platform_user_permissions WHERE username=? ORDER BY permission",String.class,username);
                Object until=row.get("platform_mfa_locked_until");
                return new Identity(username,row.get("platform_mfa_enrolled_at")!=null,
                    ((Number)row.get("platform_mfa_failed_attempts")).intValue(),until==null?null:until.toString(),permissions);
            }).toList();
    }
    @PostMapping("/{username}/mfa-enrollment")
    public ResponseEntity<?> issueEnrollment(@PathVariable String username,@Valid @RequestBody EnrollmentRequest request){
        require();
        var result=mfa.issueEnrollment(username,request.reason());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}
