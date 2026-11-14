package com.saumya.pos.returns;
import java.security.Principal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController
public class ReturnQueueController {
 private final ReturnQueueService service;
 public ReturnQueueController(ReturnQueueService service){this.service=service;}
 @GetMapping("/api/vendor/pos/returns/queue") @PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('POS_RETURN_VIEW')")
 Object queue(Principal p,@RequestParam(defaultValue="REQUESTED") String state,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,@RequestParam(defaultValue="") String reference,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="12") int size){return service.queue(p.getName(),state,from,to,reference,page,size);}
}
