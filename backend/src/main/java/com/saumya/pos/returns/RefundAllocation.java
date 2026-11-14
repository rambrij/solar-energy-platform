package com.saumya.pos.returns;
import java.math.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Component
public class RefundAllocation {
 public record Amount(BigDecimal net,BigDecimal tax,BigDecimal total){}
 public Amount line(BigDecimal originalNet,BigDecimal originalTax,int sold,long already,int quantity){
  if(originalNet==null||originalTax==null||originalNet.signum()<0||originalTax.signum()<0||sold<1||quantity<1||already<0||already>(long)sold-quantity)throw new ResponseStatusException(HttpStatus.CONFLICT,"Refund exceeds the original sold balance");
  BigDecimal denominator=BigDecimal.valueOf(sold);
  BigDecimal before=originalTax.multiply(BigDecimal.valueOf(already)).divide(denominator,2,RoundingMode.HALF_UP);
  BigDecimal after=originalTax.multiply(BigDecimal.valueOf(already+quantity)).divide(denominator,2,RoundingMode.HALF_UP);
  BigDecimal net=originalNet.multiply(BigDecimal.valueOf(already+quantity)).divide(denominator,2,RoundingMode.HALF_UP).subtract(originalNet.multiply(BigDecimal.valueOf(already)).divide(denominator,2,RoundingMode.HALF_UP)),tax=after.subtract(before);
  return new Amount(net,tax,net.add(tax));
 }
}
