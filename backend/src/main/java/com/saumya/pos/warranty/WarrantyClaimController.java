package com.saumya.pos.warranty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class WarrantyClaimController {
    private static final long MAX_EVIDENCE=10L*1024L*1024L;
    private final WarrantyClaimService claims;
    public WarrantyClaimController(WarrantyClaimService claims){this.claims=claims;}

    public record CreateRequest(@Positive long warrantyId,@NotBlank @Size(max=160) String subject,
        @NotBlank @Size(max=2000) String description){}
    public record AssignmentRequest(long expectedVersion,@NotBlank @Size(max=100) String assignedTo,
        @NotBlank @Size(max=500) String reason){}
    public record InspectionRequest(long expectedVersion,@NotBlank @Size(max=2000) String summary,
        @NotBlank @Size(max=1000) String note,boolean customerVisible){}
    public record DecisionRequest(long expectedVersion,@NotBlank @Size(max=12) String decision,
        @Size(max=16) String resolutionType,@NotBlank @Size(max=1000) String reason){}
    public record ResolutionRequest(long expectedVersion,@NotBlank @Size(max=1000) String note){}

    @PostMapping("/api/v1/customer/warranty-claims")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public WarrantyClaimService.CustomerClaim submit(Principal principal,@Valid @RequestBody CreateRequest request){
        return claims.submit(principal.getName(),new WarrantyClaimService.Create(request.warrantyId(),request.subject(),request.description()));
    }
    @GetMapping("/api/v1/customer/warranty-claims")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public List<WarrantyClaimService.CustomerClaim> customerList(Principal principal){
        return claims.customerList(principal.getName());
    }
    @GetMapping("/api/v1/customer/warranty-claims/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public WarrantyClaimService.CustomerDetail customerGet(Principal principal,@PathVariable long id){
        return claims.customerGet(principal.getName(),id);
    }
    @PostMapping(value="/api/v1/customer/warranty-claims/{id}/evidence",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public WarrantyClaimService.Evidence customerEvidence(Principal principal,@PathVariable long id,
            @RequestPart("file") MultipartFile file){
        return claims.uploadCustomerEvidence(principal.getName(),id,fileName(file),file.getContentType(),bytes(file));
    }
    @GetMapping("/api/v1/customer/warranty-claims/{id}/evidence/{evidenceId}")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<byte[]> customerEvidenceContent(Principal principal,@PathVariable long id,@PathVariable String evidenceId){
        return content(claims.customerEvidence(principal.getName(),id,evidenceId));
    }

    @GetMapping("/api/v1/vendor/warranty-claims")
    @PreAuthorize("hasAuthority('SERVICE_VIEW')")
    public List<WarrantyClaimService.Claim> vendorList(){return claims.vendorList();}
    @GetMapping("/api/v1/vendor/warranty-claims/{id}")
    @PreAuthorize("hasAuthority('SERVICE_VIEW')")
    public WarrantyClaimService.VendorDetail vendorGet(@PathVariable long id){return claims.vendorGet(id);}
    @PatchMapping("/api/v1/vendor/warranty-claims/{id}/assignment")
    @PreAuthorize("hasAuthority('SERVICE_ASSIGN')")
    public WarrantyClaimService.Claim assign(@PathVariable long id,@Valid @RequestBody AssignmentRequest request){
        return claims.assign(id,new WarrantyClaimService.Assignment(request.expectedVersion(),request.assignedTo(),request.reason()));
    }
    @PatchMapping("/api/v1/vendor/warranty-claims/{id}/inspection")
    @PreAuthorize("hasAuthority('SERVICE_UPDATE')")
    public WarrantyClaimService.Claim inspect(@PathVariable long id,@Valid @RequestBody InspectionRequest request){
        return claims.inspect(id,new WarrantyClaimService.Inspection(request.expectedVersion(),request.summary(),request.note(),request.customerVisible()));
    }
    @PatchMapping("/api/v1/vendor/warranty-claims/{id}/decision")
    @PreAuthorize("hasAuthority('SERVICE_RESOLVE')")
    public WarrantyClaimService.Claim decide(@PathVariable long id,@Valid @RequestBody DecisionRequest request){
        return claims.decide(id,new WarrantyClaimService.Decision(request.expectedVersion(),request.decision(),request.resolutionType(),request.reason()));
    }
    @PatchMapping("/api/v1/vendor/warranty-claims/{id}/resolution")
    @PreAuthorize("hasAuthority('SERVICE_RESOLVE')")
    public WarrantyClaimService.Claim resolve(@PathVariable long id,@Valid @RequestBody ResolutionRequest request){
        return claims.resolve(id,new WarrantyClaimService.Resolution(request.expectedVersion(),request.note()));
    }
    @PostMapping(value="/api/v1/vendor/warranty-claims/{id}/evidence",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SERVICE_UPDATE')")
    public WarrantyClaimService.Evidence vendorEvidence(@PathVariable long id,@RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue="false") boolean customerVisible){
        return claims.uploadVendorEvidence(id,fileName(file),file.getContentType(),bytes(file),customerVisible);
    }
    @GetMapping("/api/v1/vendor/warranty-claims/{id}/evidence/{evidenceId}")
    @PreAuthorize("hasAuthority('SERVICE_VIEW')")
    public ResponseEntity<byte[]> vendorEvidenceContent(@PathVariable long id,@PathVariable String evidenceId){
        return content(claims.vendorEvidence(id,evidenceId));
    }

    private static String fileName(MultipartFile file){
        if(file==null||file.isEmpty()||file.getSize()>MAX_EVIDENCE)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Evidence file must be between 1 byte and 10 MB");
        String name=file.getOriginalFilename();
        return name==null?"evidence":name.replace('\\','_').replace('/','_');
    }
    private static byte[] bytes(MultipartFile file){
        try{return file.getBytes();}
        catch(java.io.IOException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Evidence upload could not be read",ex);}
    }
    private static ResponseEntity<byte[]> content(WarrantyClaimService.EvidenceContent file){
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment()
                .filename(file.fileName(),StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options","nosniff").cacheControl(org.springframework.http.CacheControl.noStore())
            .body(file.content());
    }
}
