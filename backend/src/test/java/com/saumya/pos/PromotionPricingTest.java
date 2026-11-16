package com.saumya.pos;
import com.saumya.pos.shared.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class PromotionPricingTest {
 final PromotionPricing promotions=new PromotionPricing();
 @Test void unitDiscountRoundsHalfUpBeforeSharedLineTax(){var price=promotions.unitPrice(new BigDecimal("100.05"),BigDecimal.TEN);assertThat(price).isEqualByComparingTo("90.05");var line=new Pricing().line(price,BigDecimal.TEN,3);assertThat(line.net()).isEqualByComparingTo("270.15");assertThat(line.tax()).isEqualByComparingTo("27.02");assertThat(line.total()).isEqualByComparingTo("297.17");}
 @Test void zeroAndFullyApprovedDiscountHaveExactBoundaries(){assertThat(promotions.unitPrice(new BigDecimal("100"),BigDecimal.ZERO)).isEqualByComparingTo("100");assertThat(promotions.unitPrice(new BigDecimal("100"),new BigDecimal("100"))).isEqualByComparingTo("0");}
 @Test void invalidPromotionCannotCreateNegativeOrOverPrecisePrices(){for(String rate:new String[]{"-1","100.01","1.111"})assertThatThrownBy(()->promotions.unitPrice(BigDecimal.TEN,new BigDecimal(rate))).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->promotions.unitPrice(new BigDecimal("-1"),BigDecimal.TEN)).isInstanceOf(IllegalArgumentException.class);}
}
