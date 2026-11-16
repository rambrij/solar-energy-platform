package com.saumya.pos.identity;

import com.saumya.pos.identity.internal.GlobalUser;
import com.saumya.pos.identity.internal.GlobalUserRepository;
import com.saumya.pos.identity.internal.IdentitySequenceRepository;
import com.saumya.pos.membership.MembershipService;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Provisions a tenant's initial Vendor Admin (TEN-001): a global user plus a vendor membership. Vendor permissions only; never PLATFORM_*. */
@Service @Transactional
public class VendorAdminService {
    /** Vendor permissions currently enforced by the vendor modules. The platform grants no more than the vendor itself could delegate. */
    public static final Set<String> INITIAL_PERMISSIONS=Set.of("CATALOG_PROMOTION_MANAGE","INVENTORY_RECEIVE","INVENTORY_VIEW","POS_CASHIER_REPORT_VIEW","POS_DISCOUNT_APPROVE","POS_DISCOUNT_OVERRIDE","POS_RECEIPT_REPRINT","POS_RECEIPT_VIEW",
        "POS_REFUND_APPROVE","POS_REFUND_CASH_RECORD","POS_REFUND_PRINT","POS_REFUND_VIEW","POS_REPORT_EXPORT","POS_REPORT_VIEW","POS_RETURN_APPROVE","POS_RETURN_AUDIT_VIEW","POS_RETURN_CREATE","POS_RETURN_INSPECT","POS_RETURN_VIEW",
        "POS_SALE_CANCEL","POS_SALE_CREATE","POS_SESSION_CLOSE","POS_SESSION_OPEN","POS_SETTINGS_MANAGE","POS_SHIFT_VARIANCE_APPROVE","POS_TENDER_CASH_RECORD","POS_TENDER_EXTERNAL_RECORD","POS_TENDER_MANUAL_VERIFY","POS_VIEW",
        "PRODUCT_ARCHIVE","PRODUCT_CREATE","PRODUCT_UPDATE","PRODUCT_VIEW");
    /** temporaryPassword is present only on the call that created the account; it is never stored or retrievable afterwards. */
    public record Provisioned(String username,boolean created,String temporaryPassword) {}
    private static final String ALPHABET="ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private final GlobalUserRepository users;private final IdentitySequenceRepository sequence;private final MembershipService memberships;private final PasswordEncoder encoder;private final SecureRandom random=new SecureRandom();
    public VendorAdminService(GlobalUserRepository users,IdentitySequenceRepository sequence,MembershipService memberships,PasswordEncoder encoder){this.users=users;this.sequence=sequence;this.memberships=memberships;this.encoder=encoder;}

    /** Idempotent per (tenant, username): a repeat returns the existing admin without a second account or a new password. */
    public Provisioned provision(long tenant,long branch,String rawUsername){
        String username=rawUsername==null?"":rawUsername.strip().toLowerCase(Locale.ROOT);
        if(!username.matches("^[a-z0-9][a-z0-9._@+-]{2,98}$"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid username");
        var existing=memberships.membershipOf(username);
        if(existing.isPresent()){
            if(existing.get().tenant()!=tenant)throw new ResponseStatusException(HttpStatus.CONFLICT,"Username is already in use");
            return new Provisioned(username,false,null);
        }
        if(users.findByUsername(username).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"Username is already in use");
        var chars=new StringBuilder();for(int i=0;i<20;i++)chars.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        String password=chars.toString();
        long id=sequence.locked().orElseThrow(()->new IllegalStateException("Identity sequence is not initialised")).allocate();
        users.saveAndFlush(new GlobalUser(id,username,encoder.encode(password)));
        memberships.provisionAdmin(tenant,branch,username,INITIAL_PERMISSIONS);
        return new Provisioned(username,true,password);
    }
}
