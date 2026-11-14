package com.saumya.pos.retail;
import java.security.Principal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController
public class ShiftHistoryController {
 private final ShiftHistoryService service;
 public ShiftHistoryController(ShiftHistoryService service){this.service=service;}
 @GetMapping("/api/vendor/pos/sessions/history") @PreAuthorize("hasAuthority('POS_VIEW')")
 Object history(Principal p,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,@RequestParam(defaultValue="") String reference,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="12") int size){return service.history(p.getName(),from,to,reference,page,size);}
}
