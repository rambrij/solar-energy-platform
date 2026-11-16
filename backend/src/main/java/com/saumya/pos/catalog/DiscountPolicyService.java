package com.saumya.pos.catalog;
import com.saumya.pos.catalog.internal.*;
import com.saumya.pos.shared.BillPricing;
import com.saumya.pos.security.AuthorizationContext;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.FORBIDDEN;
@Service
public class DiscountPolicyService {
 private final DiscountPolicyRepository policies; private final BillPricing pricing; private final AuthorizationContext auth;
 public DiscountPolicyService(DiscountPolicyRepository policies,BillPricing pricing,AuthorizationContext auth){this.policies=policies;this.pricing=pricing;this.auth=auth;}
 @Transactional(readOnly=true) public BigDecimal maximum(long tenant){var c=auth.current();c.requireVendorMembership();c.requirePermission("POS_VIEW");if(c.tenantId()!=tenant)throw new ResponseStatusException(FORBIDDEN,"Tenant mismatch");return policies.findById(tenant).map(DiscountPolicy::maximum).orElse(BigDecimal.ZERO);}
 @Transactional public void configure(long tenant,BigDecimal maximum){var c=auth.current();c.requireVendorMembership();c.requirePermission("POS_DISCOUNT_OVERRIDE");if(c.tenantId()!=tenant)throw new ResponseStatusException(FORBIDDEN,"Tenant mismatch");pricing.percent(maximum);policies.saveAndFlush(new DiscountPolicy(tenant,maximum));}
}
