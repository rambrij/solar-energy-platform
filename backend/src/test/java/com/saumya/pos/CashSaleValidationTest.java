package com.saumya.pos;
import com.saumya.pos.checkout.CheckoutService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
class CashSaleValidationTest {
 private CheckoutService service(){return new CheckoutService(null,null,null,null,null,null,null,null,null,null,600);}
 @Test void rejectsInvalidCashBeforeAnyPersistenceOrScopeLookup(){for(BigDecimal cash:new BigDecimal[]{null,new BigDecimal("-1"),new BigDecimal("1.001"),new BigDecimal("1000000000000")})assertThatThrownBy(()->service().cashSale("cashier","cash-key",new CheckoutService.CashSale("session",List.of(new CheckoutService.Item(1,1)),cash,false))).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(400));}
 @Test void reservedFinalizationUsesTheSameCashBounds(){assertThatThrownBy(()->service().finalizeCash("cashier","order",new BigDecimal("1.001"))).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(400));}
}
