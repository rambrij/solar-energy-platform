package com.saumya.pos.returns;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
class OnlineOrderController {
 private final OnlineOrderService service;
 OnlineOrderController(OnlineOrderService service){this.service=service;}
 record ItemRequest(@Positive long productId,@Min(1) @Max(10000) int quantity){}
 record CreateRequest(@NotBlank @Size(max=36) String orderId,@NotEmpty @Size(max=100) List<@NotNull @Valid ItemRequest> items,@NotBlank @Size(max=250) String reason){}
 record DecisionRequest(@Min(0) long expectedVersion,@NotBlank @Pattern(regexp="APPROVED|REJECTED") String decision,@NotBlank @Size(max=500) String reason,@NotBlank @Size(max=500) String customerMessage){}
 record PolicyRequest(@Min(0) long expectedVersion,@Min(0) @Max(365) int returnWindowDays,@NotBlank @Size(max=500) String reason){}
 @GetMapping("/api/v1/customer/online-returns") @PreAuthorize("hasAuthority('CUSTOMER')")
 OnlineOrderService.Workspace customerWorkspace(Principal actor){return service.customerWorkspace(actor.getName());}
 @PostMapping("/api/v1/customer/online-returns") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('CUSTOMER')")
 OnlineOrderService.ReturnView createCustomer(Principal actor,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody CreateRequest request){
  return service.createCustomer(actor.getName(),key,new OnlineOrderService.Create(request.orderId(),request.items().stream().map(i->new OnlineOrderService.RequestItem(i.productId(),i.quantity())).toList(),request.reason()));
 }
 @GetMapping("/api/v1/vendor/online-returns") @PreAuthorize("hasAuthority('RETURN_VIEW')")
 List<OnlineOrderService.ReturnView> vendorList(){return service.vendorList();}
 @PostMapping("/api/v1/vendor/online-returns/{id}/decision") @PreAuthorize("hasAuthority('RETURN_APPROVE')")
 OnlineOrderService.ReturnView vendorDecision(@PathVariable String id,@Valid @RequestBody DecisionRequest r){return service.decide(id,new OnlineOrderService.Decision(r.expectedVersion(),r.decision(),r.reason(),r.customerMessage()));}
 @GetMapping("/api/v1/vendor/online-returns/policy") @PreAuthorize("hasAuthority('RETURN_VIEW')")
 OnlineOrderService.Policy vendorPolicy(){return service.policy();}
 @PutMapping("/api/v1/vendor/online-returns/policy") @PreAuthorize("hasAuthority('RETURN_APPROVE')")
 OnlineOrderService.Policy updatePolicy(@Valid @RequestBody PolicyRequest r){return service.updatePolicy(new OnlineOrderService.PolicyChange(r.expectedVersion(),r.returnWindowDays(),r.reason()));}
}
