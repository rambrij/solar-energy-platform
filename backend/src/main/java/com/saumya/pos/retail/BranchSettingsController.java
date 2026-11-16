package com.saumya.pos.retail;
import java.security.Principal;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/vendor/pos")
public class BranchSettingsController {
 private final BranchPolicyService policies;
 public BranchSettingsController(BranchPolicyService policies){this.policies=policies;}
 @GetMapping("/settings") @PreAuthorize("hasAuthority('POS_VIEW')") public Object current(Principal actor){return policies.current(actor.getName());}
 @PostMapping("/settings") @PreAuthorize("hasAuthority('POS_SETTINGS_MANAGE')") public Object update(Principal actor,@Valid @RequestBody BranchPolicyService.Settings request){return policies.update(actor.getName(),request);}
 public record Register(@NotBlank @Size(max=250) String reason){}
 @PostMapping("/registers") @PreAuthorize("hasAuthority('POS_SETTINGS_MANAGE')") public Object create(Principal actor,@Valid @RequestBody Register request){return Map.of("id",policies.createRegister(actor.getName(),request.reason()));}
}
