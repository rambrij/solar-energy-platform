package com.saumya.pos.checkout;

import com.saumya.pos.checkout.internal.*;
import com.saumya.pos.security.AuthorizationContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Vendor onboarding only. Provider verification remains external and fail-closed. */
@Service
@Transactional
public class VendorPaymentAccountService {
    private final VendorPaymentAccountRepository accounts;
    private final VendorPaymentAccountEventRepository events;
    private final AuthorizationContext authorization;
    private final Clock clock;

    public VendorPaymentAccountService(VendorPaymentAccountRepository accounts,
            VendorPaymentAccountEventRepository events,AuthorizationContext authorization,Clock clock) {
        this.accounts=accounts;this.events=events;this.authorization=authorization;this.clock=clock;
    }

    public record Submit(String providerKey,String merchantAccountReference,long expectedVersion,String reason) {}
    public record View(long id,String providerKey,String maskedMerchantAccountReference,String status,
            Instant submittedAt,Instant updatedAt,Instant verifiedAt,String rejectionReason,long version) {}

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);
    }
    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT,message);
    }
    private AuthorizationContext.Context actor(String permission) {
        var actor=authorization.current();
        actor.requireVendorMembership();
        actor.requirePermission(permission);
        return actor;
    }
    private String provider(String value) {
        if(value==null||!value.matches("[A-Za-z][A-Za-z0-9_-]{1,39}"))
            throw bad("Provider key must be 2 to 40 letters, numbers, hyphens or underscores");
        return value.toUpperCase(Locale.ROOT);
    }
    private String reference(String value) {
        if(value==null||!value.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{2,159}"))
            throw bad("Enter the provider merchant-account reference only; API secrets and bank credentials are not accepted");
        return value;
    }
    private String reason(String value) {
        if(value==null||value.isBlank()||value.strip().length()>500)
            throw bad("A submission reason of 1 to 500 characters is required");
        return value.strip();
    }
    private View view(VendorPaymentAccount account) {
        String raw=account.merchantAccountReference();
        String masked=raw.length()<=4?"••••":"••••"+raw.substring(raw.length()-4);
        return new View(account.id(),account.providerKey(),masked,account.status(),account.submittedAt(),
            account.updatedAt(),account.verifiedAt(),account.rejectionReason(),account.version());
    }

    @Transactional(readOnly=true)
    public List<View> list() {
        var actor=actor("TENANT_PROFILE_VIEW");
        return accounts.findAllByTenantIdOrderByUpdatedAtDesc(actor.tenantId()).stream().map(this::view).toList();
    }

    public View submit(Submit request) {
        if(request==null)throw bad("Merchant-account submission is required");
        var actor=actor("TENANT_PROFILE_UPDATE");
        String provider=provider(request.providerKey());
        String merchantReference=reference(request.merchantAccountReference());
        String reason=reason(request.reason());
        if(request.expectedVersion()<0)throw bad("Expected account version cannot be negative");

        Instant now=clock.instant();
        var existing=accounts.lockTenantProvider(actor.tenantId(),provider);
        if(existing.isEmpty()) {
            if(request.expectedVersion()!=0)throw conflict("Merchant-account record changed; reload before saving");
            var account=accounts.saveAndFlush(new VendorPaymentAccount(actor.tenantId(),provider,
                merchantReference,actor.username(),now));
            events.saveAndFlush(new VendorPaymentAccountEvent(actor.tenantId(),account.id(),actor.username(),
                "SUBMITTED",reason,now));
            return view(account);
        }

        VendorPaymentAccount account=existing.get();
        if(account.version()!=request.expectedVersion())
            throw conflict("Merchant-account record changed; reload before saving");
        if(account.merchantAccountReference().equals(merchantReference)
                &&"PENDING_VERIFICATION".equals(account.status()))
            return view(account);

        boolean firstSubmission="PENDING_VERIFICATION".equals(account.status())
            &&account.verifiedAt()==null&&account.version()==0;
        account.resubmit(merchantReference,actor.username(),now);
        VendorPaymentAccount saved=accounts.saveAndFlush(account);
        events.saveAndFlush(new VendorPaymentAccountEvent(actor.tenantId(),saved.id(),actor.username(),
            firstSubmission?"SUBMITTED":"RESUBMITTED",reason,now));
        return view(saved);
    }
}
