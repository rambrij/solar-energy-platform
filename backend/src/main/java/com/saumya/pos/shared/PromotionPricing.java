package com.saumya.pos.shared;
import java.math.*;
import org.springframework.stereotype.Component;
@Component
public class PromotionPricing {
 public BigDecimal unitPrice(BigDecimal listPrice,BigDecimal percent){
  if(listPrice==null||listPrice.signum()<0||percent==null||percent.signum()<0||percent.compareTo(new BigDecimal("100"))>0||percent.scale()>2)throw new IllegalArgumentException("Invalid promotion pricing");
  return listPrice.multiply(new BigDecimal("100").subtract(percent)).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);
 }
}
