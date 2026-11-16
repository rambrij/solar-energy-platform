package com.saumya.pos.catalog;
import com.saumya.pos.catalog.internal.*;
import com.saumya.pos.shared.BillPricing;
import com.saumya.pos.retail.PosService;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class DiscountPolicyService {
 private final DiscountPolicyRepository policies;
 private final BillPricing pricing;private final PosService retail;
 public DiscountPolicyService(DiscountPolicyRepository policies,BillPricing pricing,PosService retail){this.policies=policies;this.pricing=pricing;this.retail=retail;}
 @Transactional(readOnly=true) public BigDecimal maximum(long tenant){var scope=retail.scope();retail.authorize(scope,scope.cashier(),"POS_VIEW");if(scope.tenant()!=tenant)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"Tenant mismatch");return policies.findById(tenant).map(DiscountPolicy::maximum).orElse(BigDecimal.ZERO);}
 @Transactional public void configure(long tenant,BigDecimal maximum){var scope=retail.scope();retail.authorize(scope,scope.cashier(),"POS_DISCOUNT_OVERRIDE");if(scope.tenant()!=tenant)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"Tenant mismatch");pricing.percent(maximum);policies.saveAndFlush(new DiscountPolicy(tenant,maximum));}
}
