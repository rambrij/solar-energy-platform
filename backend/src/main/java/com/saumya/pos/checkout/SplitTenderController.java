package com.saumya.pos.checkout;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class SplitTenderController {
    private final SplitTenderService split;
    public SplitTenderController(SplitTenderService split){this.split=split;}
    public record Tender(@NotNull TenderPlan.Method method,@NotNull @DecimalMin(value="0.00",inclusive=false) @Digits(integer=12,fraction=2) BigDecimal amount){}
    public record Request(@NotEmpty @Size(max=TenderPlan.MAX_ENTRIES) List<@Valid Tender> tenders){}
    private static boolean has(Authentication auth,String authority){return auth.getAuthorities().stream().anyMatch(a->authority.equals(a.getAuthority()));}
    @PostMapping("/api/vendor/pos/drafts/{id}/finalize-split")
    @PreAuthorize("hasAuthority('POS_SALE_CREATE') and hasAuthority('POS_TENDER_SPLIT') and hasAuthority('POS_RECEIPT_VIEW')")
    Object finalizeSplit(Authentication auth,@PathVariable String id,@Valid @RequestBody Request request){
        var entries=request.tenders().stream().map(t->new TenderPlan.Entry(t.method(),t.amount())).toList();
        return split.pay(auth.getName(),id,entries,new SplitTenderService.Rights(has(auth,"POS_TENDER_CASH_RECORD"),has(auth,"POS_TENDER_EXTERNAL_RECORD")));
    }
}
