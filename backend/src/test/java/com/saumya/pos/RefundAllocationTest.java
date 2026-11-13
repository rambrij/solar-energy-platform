package com.saumya.pos;
import com.saumya.pos.returns.RefundAllocation;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class RefundAllocationTest {
 final RefundAllocation allocation=new RefundAllocation();
 @Test void partialTaxRoundingSumsExactlyToOriginalDiscountedSale(){
  BigDecimal sum=BigDecimal.ZERO;
  for(int prior=0;prior<3;prior++)sum=sum.add(allocation.line(new BigDecimal("0.05"),new BigDecimal("0.02"),3,prior,1).total());
  assertThat(sum).isEqualByComparingTo("0.17");
  assertThat(allocation.line(new BigDecimal("80"),new BigDecimal("16"),2,1,1).total()).isEqualByComparingTo("88");
 }
 @Test void quantityCapsAndOverflowFailClosed(){
  for(long prior:new long[]{-1,3,Long.MAX_VALUE})assertThatThrownBy(()->allocation.line(BigDecimal.ONE,BigDecimal.ONE,3,prior,1)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThatThrownBy(()->allocation.line(BigDecimal.ONE,BigDecimal.ONE,3,0,0)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }
}
