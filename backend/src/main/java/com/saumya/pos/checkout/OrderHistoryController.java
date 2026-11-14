package com.saumya.pos.checkout;

import java.security.Principal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class OrderHistoryController {
    private final OrderHistoryService history;
    public OrderHistoryController(OrderHistoryService history){this.history=history;}
    @GetMapping("/api/customer/orders") @PreAuthorize("hasAuthority('CUSTOMER')")
    Object customer(Principal p,@RequestParam(defaultValue="ALL") String status,@RequestParam(defaultValue="") String reference,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        return history.customer(p.getName(),new OrderHistoryService.Filter(status,reference,from,to,page,size));
    }
    @GetMapping("/api/vendor/pos/orders") @PreAuthorize("hasAuthority('POS_VIEW')")
    Object pos(Principal p,@RequestParam(defaultValue="ALL") String status,@RequestParam(defaultValue="") String reference,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        return history.pos(p.getName(),new OrderHistoryService.Filter(status,reference,from,to,page,size));
    }
    @GetMapping("/api/customer/orders/{id}") @PreAuthorize("hasAuthority('CUSTOMER')")
    Object customerDetail(Principal p,@PathVariable String id){return history.customerDetail(p.getName(),id);}
    @GetMapping("/api/vendor/pos/orders/{id}") @PreAuthorize("hasAuthority('POS_VIEW')")
    Object posDetail(Principal p,@PathVariable String id){return history.posDetail(p.getName(),id);}
}
