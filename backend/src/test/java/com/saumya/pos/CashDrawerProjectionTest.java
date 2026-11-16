package com.saumya.pos;
import com.saumya.pos.retail.CashDrawerProjection;
import com.saumya.pos.shared.CashTenderRecorded;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CashDrawerProjectionTest {
 @Test void invalidCashFactsCannotReachPersistence(){
  var projection=new CashDrawerProjection(null);
  assertThatThrownBy(()->projection.record(new CashTenderRecorded("tender",1,1,"shift","order",BigDecimal.ONE,BigDecimal.TEN))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->projection.record(new CashTenderRecorded("tender",1,1,null,"order",BigDecimal.ONE,BigDecimal.ZERO))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->projection.record(new CashTenderRecorded("tender",1,1,"shift","order",new BigDecimal("-1"),BigDecimal.ZERO))).isInstanceOf(IllegalArgumentException.class);
 }
}
