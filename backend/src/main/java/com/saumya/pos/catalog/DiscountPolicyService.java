package com.saumya.pos.catalog;
import com.saumya.pos.catalog.internal.*;
import com.saumya.pos.shared.BillPricing;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class DiscountPolicyService {
 private final DiscountPolicyRepository policies;
 private final BillPricing pricing;
 public DiscountPolicyService(DiscountPolicyRepository policies,BillPricing pricing){this.policies=policies;this.pricing=pricing;}
 @Transactional(readOnly=true) public BigDecimal maximum(long tenant){return policies.findById(tenant).map(DiscountPolicy::maximum).orElse(BigDecimal.ZERO);}
 @Transactional public void configure(long tenant,BigDecimal maximum){pricing.percent(maximum);policies.saveAndFlush(new DiscountPolicy(tenant,maximum));}
}
