package com.saumya.pos;
import com.saumya.pos.shared.BillPricing;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class BillPricingTest {
 private final BillPricing pricing=new BillPricing();
 private BillPricing.Input line(long id,String price,int quantity,String percent){return new BillPricing.Input(id,new BigDecimal(price),quantity,new BigDecimal("10"),new BigDecimal(percent));}
 @Test void lineThenBillDiscountRecalculatesTax(){var result=pricing.calculate(List.of(line(1,"100",2,"10")),new BigDecimal("10")).getFirst();assertThat(result.manualDiscount()).isEqualByComparingTo("38");assertThat(result.tax()).isEqualByComparingTo("16.20");assertThat(result.total()).isEqualByComparingTo("178.20");}
 @Test void tinyBillRemainderNeverMakesZeroValueLineNegative(){var result=pricing.calculate(List.of(line(3,"0",1,"0"),line(2,"0.01",1,"0"),line(1,"0.01",1,"0")),new BigDecimal("50"));assertThat(result).allSatisfy(l->assertThat(l.total()).isNotNegative());assertThat(result.stream().map(BillPricing.Line::manualDiscount).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("0.01");assertThat(result.getFirst().product()).isEqualTo(1);}
 @Test void invalidPercentQuantityAndDuplicateProductAreRejected(){assertThatThrownBy(()->pricing.calculate(List.of(line(1,"100",1,"101")),BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->pricing.calculate(List.of(line(1,"100",0,"0")),BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->pricing.calculate(List.of(line(1,"100",1,"0"),line(1,"100",1,"0")),BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);}
}
