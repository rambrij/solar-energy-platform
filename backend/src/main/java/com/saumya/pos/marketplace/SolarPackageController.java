package com.saumya.pos.marketplace;

import com.saumya.pos.catalog.SolarPackageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class SolarPackageController {
    private final SolarPackageService service;
    public SolarPackageController(SolarPackageService service){this.service=service;}

    public record ItemRequest(@Positive long productId,@Min(1) @Max(10000) int quantity){}
    public record PackageRequest(@NotBlank @Size(max=80) String sku,@NotBlank @Size(max=160) String name,
        @Size(max=2000) String description,@NotNull @DecimalMin("0.001") @Digits(integer=5,fraction=3) BigDecimal systemCapacityKw,
        @NotNull @DecimalMin("0.00") @Digits(integer=17,fraction=2) BigDecimal packagePrice,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer=3,fraction=2) BigDecimal taxRate,
        boolean published,@PositiveOrZero long expectedVersion,@NotBlank @Size(max=250) String reason,
        @NotEmpty @Size(max=40) List<@Valid ItemRequest> items){}

    private static SolarPackageService.Input input(PackageRequest request){
        return new SolarPackageService.Input(request.sku(),request.name(),request.description(),
            request.systemCapacityKw(),request.packagePrice(),request.taxRate(),request.published(),
            request.expectedVersion(),request.reason(),
            request.items().stream().map(item->new SolarPackageService.ItemInput(item.productId(),item.quantity())).toList());
    }

    @GetMapping("/api/v1/vendor/packages")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public SolarPackageService.Page list(@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size){return service.list(page,size);}

    @PostMapping("/api/v1/vendor/packages")
    @PreAuthorize("hasAuthority('PRODUCT_CREATE')")
    public SolarPackageService.View create(@Valid @RequestBody PackageRequest request){return service.create(input(request));}

    @PutMapping("/api/v1/vendor/packages/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    public SolarPackageService.View update(@PathVariable long id,@Valid @RequestBody PackageRequest request){
        return service.update(id,input(request));
    }

    @GetMapping("/api/v1/marketplace/vendors/{slug}/packages")
    @PreAuthorize("permitAll()")
    public List<SolarPackageService.View> publicList(@PathVariable String slug){return service.publicList(slug);}
}
