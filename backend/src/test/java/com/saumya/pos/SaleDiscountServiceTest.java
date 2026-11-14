package com.saumya.pos;
import com.saumya.pos.checkout.SaleDiscountService;
import com.saumya.pos.catalog.DiscountPolicyService;
import com.saumya.pos.shared.BillPricing;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;

class SaleDiscountServiceTest {
 final DiscountPolicyService policy=new DiscountPolicyService(null,null){public BigDecimal maximum(long tenant){return new BigDecimal("20");}};
 final SaleDiscountService service=new SaleDiscountService(policy,new BillPricing());
 final List<BillPricing.Input> lines=List.of(new BillPricing.Input(1,new BigDecimal("100"),1,new BigDecimal("10"),BigDecimal.ZERO));
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 private void authorized(){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("cashier","",List.of(new SimpleGrantedAuthority("POS_DISCOUNT_APPLY"))));}
 @Test void zeroDiscountNeedsNoOverridePermission(){assertThat(service.calculate(1,lines,null).getFirst().total()).isEqualByComparingTo("110");}
 @Test void permissionReasonAndCombinedLimitAreEnforced(){var request=new SaleDiscountService.Request(Map.of(1L,new BigDecimal("15")),new BigDecimal("10"),"Approved sale");assertThatThrownBy(()->service.calculate(1,lines,request)).hasMessageContaining("permission");authorized();assertThatThrownBy(()->service.calculate(1,lines,request)).hasMessageContaining("limit");assertThatThrownBy(()->service.calculate(1,lines,new SaleDiscountService.Request(Map.of(),BigDecimal.ONE,""))).hasMessageContaining("reason");}
 @Test void approvedDiscountAndUnknownProduct(){authorized();assertThat(service.calculate(1,lines,new SaleDiscountService.Request(Map.of(1L,new BigDecimal("10")),BigDecimal.ZERO,"Approved"))).hasSize(1);assertThatThrownBy(()->service.calculate(1,lines,new SaleDiscountService.Request(Map.of(2L,BigDecimal.ONE),BigDecimal.ZERO,"Approved"))).hasMessageContaining("outside");}
}
