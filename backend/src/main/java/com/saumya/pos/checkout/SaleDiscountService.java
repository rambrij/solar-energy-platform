package com.saumya.pos.checkout;
import com.saumya.pos.catalog.DiscountPolicyService;
import com.saumya.pos.shared.*;
import java.math.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
@Service
public class SaleDiscountService {
 private final DiscountPolicyService policy;private final BillPricing pricing;
 public SaleDiscountService(DiscountPolicyService policy,BillPricing pricing){this.policy=policy;this.pricing=pricing;}
 public record Request(Map<Long,BigDecimal> linePercent,BigDecimal billPercent,String reason){
  public Request {
   var sorted=new TreeMap<Long,BigDecimal>();
   if(linePercent!=null)linePercent.forEach((key,value)->{if(key==null||key<1||value==null)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Valid discount product and percent required");sorted.put(key,value.stripTrailingZeros());});
   linePercent=Collections.unmodifiableSortedMap(sorted);billPercent=billPercent==null?BigDecimal.ZERO:billPercent.stripTrailingZeros();reason=reason==null?null:reason.strip();
  }
 }
 public List<BillPricing.Line> calculate(long tenant,List<BillPricing.Input> inputs,Request request){
  var specified=request==null||request.linePercent()==null?Map.<Long,BigDecimal>of():request.linePercent();
  BigDecimal bill=request==null||request.billPercent()==null?BigDecimal.ZERO:request.billPercent();
  try{pricing.percent(bill);for(var value:specified.values())pricing.percent(value);}catch(IllegalArgumentException e){throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,e.getMessage());}
  if(!inputs.stream().map(BillPricing.Input::product).collect(java.util.stream.Collectors.toSet()).containsAll(specified.keySet()))throw new DomainProblem("DISCOUNT_PRODUCT_MISMATCH","Discount references a product outside this bill");
  boolean applied=bill.signum()>0||specified.values().stream().anyMatch(value->value.signum()>0);
  if(applied){
   var actor=SecurityContextHolder.getContext().getAuthentication();
   if(actor==null||actor.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("POS_DISCOUNT_APPLY")))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"Discount permission required");
   if(request.reason()==null||request.reason().isBlank()||request.reason().length()>250)throw new DomainProblem("DISCOUNT_REASON_REQUIRED","Discount reason required");
   var maximum=policy.maximum(tenant);
   for(var input:inputs){var line=specified.getOrDefault(input.product(),BigDecimal.ZERO);var effective=new BigDecimal("100").subtract(new BigDecimal("100").subtract(line).multiply(new BigDecimal("100").subtract(bill)).divide(new BigDecimal("100")));
    if(effective.compareTo(maximum)>0)throw new DomainProblem("DISCOUNT_LIMIT_EXCEEDED","Combined line and bill discount exceeds the approved vendor limit");}
  }
  return pricing.calculate(inputs.stream().map(i->new BillPricing.Input(i.product(),i.price(),i.quantity(),i.taxRate(),specified.getOrDefault(i.product(),BigDecimal.ZERO))).toList(),bill);
 }
}
