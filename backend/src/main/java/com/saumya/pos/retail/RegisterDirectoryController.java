package com.saumya.pos.retail;
import java.security.Principal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
public class RegisterDirectoryController {
 private final RegisterDirectoryService registers;
 public RegisterDirectoryController(RegisterDirectoryService registers){this.registers=registers;}
 @GetMapping("/api/vendor/pos/registers") @PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('POS_SESSION_OPEN')")
 Object directory(Principal p,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="12") int size){return registers.directory(p.getName(),page,size);}
}
