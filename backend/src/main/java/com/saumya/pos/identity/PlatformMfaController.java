package com.saumya.pos.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/auth/platform-mfa/enrollment")
public class PlatformMfaController {
    private final PlatformMfaService service;
    public PlatformMfaController(PlatformMfaService service){this.service=service;}
    public record StartRequest(@NotBlank String enrollmentToken){}
    public record CompleteRequest(@NotBlank String enrollmentToken,@NotBlank @Pattern(regexp="[0-9]{6}") String code){}
    @PostMapping("/start")
    public ResponseEntity<?> start(@Valid @RequestBody StartRequest request){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.start(request.enrollmentToken()));
    }
    @PostMapping("/complete")
    public ResponseEntity<?> complete(@Valid @RequestBody CompleteRequest request){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.complete(request.enrollmentToken(),request.code()));
    }
    @GetMapping("/status") @PreAuthorize("hasAuthority('PLATFORM_AUDIT_VIEW') or hasAuthority('PLATFORM_TENANT_VIEW')")
    public Object status(java.security.Principal actor){return service.status(actor.getName());}
}
