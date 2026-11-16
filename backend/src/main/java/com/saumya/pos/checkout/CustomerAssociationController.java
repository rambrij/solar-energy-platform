package com.saumya.pos.checkout;
import java.security.Principal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
public class CustomerAssociationController {
 private final CustomerAssociationService associations;
 public CustomerAssociationController(CustomerAssociationService associations){this.associations=associations;}
 public record Requested(@AssertTrue boolean customerRequested){}
 public record Token(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token){}
 public record Approval(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token,@AssertTrue boolean consent){}
 @PostMapping("/api/v1/vendor/pos/sales/{id}/customer-association") @PreAuthorize("hasAuthority('POS_SALE_CREATE')")
 public Object create(Principal actor,@PathVariable String id,@Valid @RequestBody Requested request){return associations.create(actor.getName(),id,request.customerRequested());}
 @PostMapping("/api/v1/customer/pos-association/preview") @PreAuthorize("hasAuthority('CUSTOMER')")
 public Object preview(Principal actor,@Valid @RequestBody Token request){return associations.preview(actor.getName(),request.token());}
 @PostMapping("/api/v1/customer/pos-association/approve") @PreAuthorize("hasAuthority('CUSTOMER')")
 public Object approve(Principal actor,@Valid @RequestBody Approval request){return associations.approve(actor.getName(),request.token(),request.consent());}
}
