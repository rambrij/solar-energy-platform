package com.saumya.pos;
import com.saumya.pos.returns.CashRefundService;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CashRefundServiceTest {
 final CashRefundService service=new CashRefundService(null,null,null,null,null,null,null,null,null,null);
 @Test void invalidFinancialCommandsFailBeforeAccessingPersistence(){
  assertThatThrownBy(()->service.pay("actor","valid-key",null)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  for(String confirmation:new String[]{null,"","YES"})assertThatThrownBy(()->service.pay("actor","valid-key",new CashRefundService.Pay("return","session","Reason","a".repeat(64),confirmation))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }
}
