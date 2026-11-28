package com.saumya.pos.marketplace;

import com.saumya.pos.inventory.WarehouseInventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/vendor/inventory")
public class WarehouseInventoryController {
    private final WarehouseInventoryService service;
    public WarehouseInventoryController(WarehouseInventoryService service){this.service=service;}

    public record CreateWarehouseRequest(@NotBlank @Size(max=40) String code,
        @NotBlank @Size(max=160) String name,@NotBlank @Size(max=500) String reason) {}
    public record UpdateWarehouseRequest(@Min(0) long expectedVersion,@NotBlank @Size(max=160) String name,
        @NotNull Boolean active,@NotBlank @Size(max=500) String reason) {}
    public record CreateTransferRequest(@NotBlank @Pattern(regexp="BRANCH|WAREHOUSE") String sourceType,
        Long sourceWarehouseId,@NotBlank @Pattern(regexp="BRANCH|WAREHOUSE") String destinationType,
        Long destinationWarehouseId,@Positive long destinationBranchId,@Positive long productId,
        @Min(1) @Max(1000000) int quantity,@NotBlank @Size(max=500) String reason) {}
    public record TransferActionRequest(@Min(0) long expectedVersion,@NotBlank @Size(max=500) String reason) {}
    public record ReceiveRequest(@Min(0) long expectedVersion,@Min(1) @Max(1000000) int quantity,
        boolean finalReceipt,@NotBlank @Size(max=80) String commandKey,@NotBlank @Size(max=500) String reason) {}

    @GetMapping("/warehouses")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public List<WarehouseInventoryService.WarehouseView> warehouses(){return service.warehouses();}

    @GetMapping("/warehouses/{id}/stock")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public List<WarehouseInventoryService.WarehouseStockView> warehouseStock(@PathVariable long id){
        return service.warehouseStock(id);
    }

    @PostMapping("/warehouses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER')")
    public WarehouseInventoryService.WarehouseView createWarehouse(@Valid @RequestBody CreateWarehouseRequest request){
        return service.createWarehouse(request.code(),request.name(),request.reason());
    }

    @PatchMapping("/warehouses/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER')")
    public WarehouseInventoryService.WarehouseView updateWarehouse(@PathVariable long id,
            @Valid @RequestBody UpdateWarehouseRequest request){
        return service.updateWarehouse(id,request.expectedVersion(),request.name(),request.active(),request.reason());
    }

    @GetMapping("/transfers")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public List<WarehouseInventoryService.TransferView> transfers(){return service.transfers();}

    @GetMapping("/transfers/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    public WarehouseInventoryService.TransferDetail transfer(@PathVariable String id){return service.transfer(id);}

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER')")
    public WarehouseInventoryService.TransferView createTransfer(
            @RequestHeader("Idempotency-Key") String key,@Valid @RequestBody CreateTransferRequest request){
        return service.createTransfer(key,request.sourceType(),request.sourceWarehouseId(),
            request.destinationType(),request.destinationWarehouseId(),request.destinationBranchId(),
            request.productId(),request.quantity(),request.reason());
    }

    @PostMapping("/transfers/{id}/approve")
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER')")
    public WarehouseInventoryService.TransferView approve(@PathVariable String id,
            @Valid @RequestBody TransferActionRequest request){
        return service.approve(id,request.expectedVersion(),request.reason());
    }

    @PostMapping("/transfers/{id}/dispatch")
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER')")
    public WarehouseInventoryService.TransferView dispatch(@PathVariable String id,
            @Valid @RequestBody TransferActionRequest request){
        return service.dispatch(id,request.expectedVersion(),request.reason());
    }

    @PostMapping("/transfers/{id}/receive")
    @PreAuthorize("hasAuthority('INVENTORY_TRANSFER')")
    public WarehouseInventoryService.TransferView receive(@PathVariable String id,
            @Valid @RequestBody ReceiveRequest request){
        return service.receive(id,request.expectedVersion(),request.quantity(),request.finalReceipt(),
            request.commandKey(),request.reason());
    }
}
