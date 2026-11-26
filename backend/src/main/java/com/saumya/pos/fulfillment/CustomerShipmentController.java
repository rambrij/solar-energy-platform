package com.saumya.pos.fulfillment;
import java.security.Principal;import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/customer") @PreAuthorize("hasAuthority('CUSTOMER')")
public class CustomerShipmentController {
 private final ShipmentService service;public CustomerShipmentController(ShipmentService service){this.service=service;}
 @GetMapping("/orders/{orderId}/shipments") public List<ShipmentService.CustomerShipment> customerShipments(Principal p,@PathVariable String orderId){return service.customerShipments(p.getName(),orderId);}
 @GetMapping("/shipments") public List<ShipmentService.CustomerShipmentSummary> customerShipmentFeed(Principal p){return service.customerShipmentFeed(p.getName());}
}
