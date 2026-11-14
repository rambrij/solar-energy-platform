package com.saumya.pos.checkout;
import java.security.Principal;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
public class CheckoutController {
    private final CheckoutService checkout;
    public CheckoutController(CheckoutService checkout){this.checkout=checkout;}
    @PostMapping("/api/vendor/pos/sales") @PreAuthorize("hasAuthority('POS_SALE_CREATE') and hasAuthority('POS_TENDER_CASH_RECORD') and hasAuthority('POS_RECEIPT_VIEW')")
    Object cashSale(Principal p,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody CheckoutService.CashSale request){return checkout.cashSale(p.getName(),key,request);}
    @PostMapping("/api/customer/checkout/drafts") @PreAuthorize("hasAuthority('CUSTOMER')")
    Object online(Principal p,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody CheckoutService.OnlineDraft r){return checkout.online(p.getName(),key,r);}
    @GetMapping("/api/customer/checkout/drafts") @PreAuthorize("hasAuthority('CUSTOMER')")
    Object onlineList(Principal p){return checkout.onlineList(p.getName());}
    @PostMapping("/api/customer/checkout/drafts/{id}/cancel") @PreAuthorize("hasAuthority('CUSTOMER')")
    Object cancelOnline(Principal p,@PathVariable String id,@Valid @RequestBody CheckoutService.Cancel r){return checkout.cancelOnline(p.getName(),id,r.reason());}
    @PostMapping("/api/vendor/pos/drafts") @PreAuthorize("hasAuthority('POS_VIEW') and hasAuthority('POS_SALE_CREATE')")
    Object pos(Authentication auth,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody CheckoutService.PosDraft r){
        boolean apply=auth.getAuthorities().stream().anyMatch(a->"POS_DISCOUNT_APPLY".equals(a.getAuthority())),approve=auth.getAuthorities().stream().anyMatch(a->"POS_DISCOUNT_OVERRIDE".equals(a.getAuthority()));
        return checkout.pos(auth.getName(),key,r,new CheckoutService.DiscountAuthority(apply,approve));}
    @GetMapping("/api/vendor/pos/drafts") @PreAuthorize("hasAuthority('POS_VIEW')")
    Object posList(Principal p,@RequestParam String sessionId){return checkout.posList(p.getName(),sessionId);}
    @PostMapping("/api/vendor/pos/drafts/{id}/cancel") @PreAuthorize("hasAuthority('POS_SALE_CANCEL')")
    Object cancelPos(Principal p,@PathVariable String id,@Valid @RequestBody CheckoutService.Cancel r){return checkout.cancelPos(p.getName(),id,r.reason());}
    @PostMapping("/api/vendor/pos/drafts/{id}/finalize-cash") @PreAuthorize("hasAuthority('POS_SALE_CREATE') and hasAuthority('POS_TENDER_CASH_RECORD') and hasAuthority('POS_RECEIPT_VIEW')")
    Object cash(Principal p,@PathVariable String id,@Valid @RequestBody CheckoutService.Cash r){return checkout.finalizeCash(p.getName(),id,r.cashReceived());}
}
