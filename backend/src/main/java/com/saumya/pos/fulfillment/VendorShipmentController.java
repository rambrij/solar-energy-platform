package com.saumya.pos.fulfillment;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;import java.util.List;import org.springframework.http.HttpStatus;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/vendor")
public class VendorShipmentController {
 private final ShipmentService service;public VendorShipmentController(ShipmentService service){this.service=service;}
 public record Create(@NotBlank @Size(max=120) String carrier,@NotBlank @Size(max=160) String trackingReference,@Size(max=1000) String trackingUrl,@NotBlank @Size(max=500) String reason){}
 public record EventCreate(@NotBlank @Pattern(regexp="[A-Za-z][A-Za-z0-9_]{1,39}") String eventCode,@NotBlank @Size(max=500) String description,@NotBlank @Size(max=500) String reason){}
 @GetMapping("/shipments/orders") @PreAuthorize("hasAuthority('SHIPMENT_VIEW')") public List<ShipmentService.EligibleOrder> eligibleOrders(){return service.eligibleOrders();}
 @GetMapping("/shipments") @PreAuthorize("hasAuthority('SHIPMENT_VIEW')") public List<ShipmentService.Shipment> shipments(){return service.shipments();}
 @PostMapping("/orders/{orderId}/shipments") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('SHIPMENT_UPDATE')") public ShipmentService.Shipment create(@PathVariable String orderId,@Valid @RequestBody Create r){return service.createShipment(orderId,r.carrier(),r.trackingReference(),r.trackingUrl(),r.reason());}
 @PostMapping("/shipments/{shipmentId}/events") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('SHIPMENT_UPDATE')") public ShipmentService.Event event(@PathVariable long shipmentId,@Valid @RequestBody EventCreate r){return service.addEvent(shipmentId,r.eventCode(),r.description(),r.reason());}
}
