package com.saumya.pos.returns;
import java.math.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Component
public class RefundAllocation {
 public record Amount(BigDecimal net,BigDecimal tax,BigDecimal total){}
 public Amount line(BigDecimal unitPrice,BigDecimal originalTax,int sold,long already,int quantity){
  if(unitPrice==null||originalTax==null||unitPrice.signum()<0||originalTax.signum()<0||sold<1||quantity<1||already<0||already>(long)sold-quantity)throw new ResponseStatusException(HttpStatus.CONFLICT,"Refund exceeds the original sold balance");
  BigDecimal denominator=BigDecimal.valueOf(sold);
  BigDecimal before=originalTax.multiply(BigDecimal.valueOf(already)).divide(denominator,2,RoundingMode.HALF_UP);
  BigDecimal after=originalTax.multiply(BigDecimal.valueOf(already+quantity)).divide(denominator,2,RoundingMode.HALF_UP);
  BigDecimal net=unitPrice.multiply(BigDecimal.valueOf(quantity)),tax=after.subtract(before);
  return new Amount(net,tax,net.add(tax));
 }
}
