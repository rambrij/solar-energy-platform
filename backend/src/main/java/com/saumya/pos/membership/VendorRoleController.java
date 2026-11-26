package com.saumya.pos.membership;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/vendor")
public class VendorRoleController {
    private final VendorRoleService service;
    public VendorRoleController(VendorRoleService service){this.service=service;}
    public record RoleCreate(@NotBlank @Size(min=2,max=80) String roleKey,@NotBlank @Size(max=120) String displayName,
        @NotEmpty @Size(max=100) List<@NotBlank String> permissions,@NotBlank @Size(max=500) String reason, String templateKey, Integer templateVersion){}
    public record RoleUpdate(@PositiveOrZero long expectedVersion,@NotBlank @Size(max=120) String displayName,
        @NotEmpty @Size(max=100) List<@NotBlank String> permissions,@NotBlank @Size(max=500) String reason){}
    public record EmployeeCreate(@NotBlank @Email @Size(max=100) String username,@Positive long branchId,@Positive long roleId,
        @NotBlank @Size(max=500) String reason){}
    public record RoleAssignment(@NotNull List<@Positive Long> roleIds,@NotBlank @Size(max=500) String reason){}
    public record RegisterAssignment(@NotNull List<@Positive Long> registerIds,@NotBlank @Size(max=500) String reason){}
    public record BranchUpdate(@Positive long branchId,@NotBlank @Size(max=500) String reason){}
    public record Reactivation(@NotEmpty @Size(max=50) List<@Positive Long> roleIds,@NotBlank @Size(max=500) String reason){}
    public record Reason(@NotBlank @Size(max=500) String reason){}
    public record InvitationResend(@NotBlank @Size(max=500) String reason){}

    @GetMapping("/roles") @PreAuthorize("hasAuthority('ROLE_VIEW')") public Object roles(){return service.roles();}
    @GetMapping("/roles/permissions")
    @PreAuthorize("hasAnyAuthority('ROLE_VIEW','ROLE_CREATE','ROLE_UPDATE')")
    public Object delegablePermissions(){return java.util.Map.of("permissions",service.delegablePermissions(),"templates",StandardRoleTemplates.ALL);}
    @GetMapping("/roles/{id}") @PreAuthorize("hasAuthority('ROLE_VIEW')") public Object role(@PathVariable long id){return service.role(id);}
    @PostMapping("/roles") @PreAuthorize("hasAuthority('ROLE_CREATE')") public Object create(@Valid @RequestBody RoleCreate request){return org.springframework.http.ResponseEntity.status(HttpStatus.CREATED).body(request.templateKey()!=null ? service.applyTemplate(request.templateKey(),request.templateVersion()==null?0:request.templateVersion(),request.roleKey(),request.displayName(),request.permissions(),request.reason()) : service.create(request.roleKey(),request.displayName(),request.permissions(),request.reason()));}
    @PutMapping("/roles/{id}") @PreAuthorize("hasAuthority('ROLE_UPDATE')") public Object update(@PathVariable long id,@Valid @RequestBody RoleUpdate request){return service.update(id,request.expectedVersion(),request.displayName(),request.permissions(),request.reason());}
    @PostMapping("/roles/{id}/deactivate") @PreAuthorize("hasAuthority('ROLE_UPDATE')") public Object deactivate(@PathVariable long id,@Valid @RequestBody Reason request){service.deactivate(id,request.reason());return java.util.Map.of("deactivated",true);}
    @GetMapping("/roles/{id}/access-history")
    @PreAuthorize("hasAuthority('ROLE_VIEW')")
    public Object roleAccessHistory(@PathVariable long id){return service.roleAccessHistory(id);}
    @GetMapping("/employees/branches")
    @PreAuthorize("hasAnyAuthority('USER_VIEW','USER_CREATE','USER_UPDATE')")
    public Object employeeBranches(){return service.employeeBranches();}
    @GetMapping("/employees") @PreAuthorize("hasAuthority('USER_VIEW')") public Object employees(){return service.employees();}
    @PostMapping("/employees") @PreAuthorize("hasAuthority('USER_CREATE') and hasAuthority('ROLE_ASSIGN')") public Object createEmployee(@Valid @RequestBody EmployeeCreate request){return org.springframework.http.ResponseEntity.status(HttpStatus.CREATED).body(service.createEmployee(request.username(),request.branchId(),request.roleId(),request.reason()));}
    @PutMapping("/employees/{username}/roles") @PreAuthorize("hasAuthority('ROLE_ASSIGN')") public Object assignRoles(@PathVariable String username,@Valid @RequestBody RoleAssignment request){return service.assignRoles(username,request.roleIds(),request.reason());}
    @PutMapping("/employees/{username}/registers") @PreAuthorize("hasAuthority('ROLE_ASSIGN')") public Object assignRegisters(@PathVariable String username,@Valid @RequestBody RegisterAssignment request){return service.assignRegisters(username,request.registerIds(),request.reason());}
    @PatchMapping("/employees/{username}/branch") @PreAuthorize("hasAuthority('USER_UPDATE')") public Object branch(@PathVariable String username,@Valid @RequestBody BranchUpdate request){return service.updateBranch(username,request.branchId(),request.reason());}
    @PostMapping("/employees/{username}/reactivate")
    @PreAuthorize("hasAuthority('USER_UPDATE') and hasAuthority('ROLE_ASSIGN')")
    public Object reactivate(@PathVariable String username,@Valid @RequestBody Reactivation request){
        return service.reactivateEmployee(username,request.roleIds(),request.reason());
    }
    @PostMapping("/employees/{username}/disable") @PreAuthorize("hasAuthority('USER_DISABLE')") public Object disable(@PathVariable String username,@Valid @RequestBody Reason request){service.disableEmployee(username,request.reason());return java.util.Map.of("disabled",true);}
    @GetMapping("/employees/{username}/access-history")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public Object employeeAccessHistory(@PathVariable String username){return service.employeeAccessHistory(username);}
    @GetMapping("/employees/{username}/invitation")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public Object invitationStatus(@PathVariable String username){return service.invitationStatus(username);}
    @PostMapping("/employees/{username}/invitation/resend")
    @PreAuthorize("hasAuthority('USER_CREATE') and hasAuthority('ROLE_ASSIGN')")
    public Object resendInvitation(@PathVariable String username,@Valid @RequestBody InvitationResend request){
        return service.resendEmployeeInvitation(username,request.reason());
    }
    @GetMapping("/employees/{username}/effective-permissions") @PreAuthorize("hasAuthority('USER_VIEW')") public Object permissions(@PathVariable String username){return java.util.Map.of("permissions",service.effectivePermissions(username));}
}
