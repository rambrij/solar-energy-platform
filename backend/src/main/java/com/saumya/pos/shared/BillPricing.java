package com.saumya.pos.shared;

import java.math.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Tax-exclusive discounts; final bill cents are allocated deterministically by product ID. */
@Component
public class BillPricing {
 public record Input(long product, BigDecimal price, int quantity, BigDecimal taxRate, BigDecimal linePercent) {}
 public record Line(long product, BigDecimal manualDiscount, BigDecimal tax, BigDecimal total) {}
 public List<Line> calculate(List<Input> inputs, BigDecimal billPercent) {
  percent(billPercent);
  var sorted=inputs.stream().sorted(Comparator.comparingLong(Input::product)).toList();
  if(sorted.isEmpty() || sorted.stream().map(Input::product).distinct().count()!=sorted.size())throw new IllegalArgumentException("Distinct bill lines required");
  var nets=new ArrayList<BigDecimal>();BigDecimal subtotal=BigDecimal.ZERO;
  for(var input:sorted){
   percent(input.linePercent());percent(input.taxRate());
   if(input.quantity()<1 || input.price()==null || input.price().signum()<0 || input.price().scale()>2)throw new IllegalArgumentException("Invalid bill line");
   var gross=input.price().multiply(BigDecimal.valueOf(input.quantity()));
   var net=gross.subtract(discount(gross,input.linePercent()));nets.add(net);subtotal=subtotal.add(net);
  }
  BigDecimal billDiscount=discount(subtotal,billPercent),remaining=billDiscount;
  var allocations=new ArrayList<BigDecimal>();
  for(var net:nets){var allocated=subtotal.signum()==0?BigDecimal.ZERO:billDiscount.multiply(net).divide(subtotal,2,RoundingMode.DOWN);allocations.add(allocated);remaining=remaining.subtract(allocated);}
  for(int i=0;remaining.signum()>0;i=(i+1)%nets.size()){
   if(allocations.get(i).compareTo(nets.get(i))<0){allocations.set(i,allocations.get(i).add(new BigDecimal("0.01")));remaining=remaining.subtract(new BigDecimal("0.01"));}
  }
  var result=new ArrayList<Line>();
  for(int i=0;i<sorted.size();i++){
   var input=sorted.get(i);var net=nets.get(i).subtract(allocations.get(i));
   var tax=net.multiply(input.taxRate()).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);
   result.add(new Line(input.product(),input.price().multiply(BigDecimal.valueOf(input.quantity())).subtract(net),tax,net.add(tax)));
  }
  return List.copyOf(result);
 }
 private BigDecimal discount(BigDecimal base,BigDecimal percent){return base.multiply(percent).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);}
 public void percent(BigDecimal value){if(value==null||value.signum()<0||value.compareTo(new BigDecimal("100"))>0||value.scale()>2)throw new IllegalArgumentException("Percentage must be 0..100 with at most two decimal places");}
}
