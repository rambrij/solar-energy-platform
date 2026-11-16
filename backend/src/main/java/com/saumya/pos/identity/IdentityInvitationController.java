package com.saumya.pos.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/invitations")
public class IdentityInvitationController {
    private final IdentityProvisioningService invitations;
    public IdentityInvitationController(IdentityProvisioningService invitations){this.invitations=invitations;}
    public record AcceptRequest(@NotBlank String token,@NotBlank @Size(min=12,max=72) String password){}
    @PostMapping("/accept")
    public ResponseEntity<?> accept(@Valid @RequestBody AcceptRequest request){
        invitations.acceptInvitation(request.token(),request.password());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("activated",true));
    }
}
