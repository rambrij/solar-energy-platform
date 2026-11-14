package com.saumya.pos;
import com.saumya.pos.returns.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
class ReturnRulesTest {
 final ReturnRules rules=new ReturnRules();final Instant paid=Instant.parse("2026-11-01T12:00:00Z");
 @Test void disabledPolicyAndExactDeadlineFailClosed(){assertThat(rules.eligible(paid,0,paid)).isFalse();rules.requireWindow(paid,30,paid.plusSeconds(1));assertThatThrownBy(()->rules.requireWindow(paid,30,paid.plus(Duration.ofDays(30)))).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->rules.requireWindow(paid,0,paid)).isInstanceOf(ResponseStatusException.class);assertThat(rules.eligible(paid,30,paid.minusSeconds(1))).isFalse();}
 @Test void quantityBalanceIncludesPendingAndInspectedClaimsWithoutOverflow(){rules.quantity(3,1,2);for(long held:new long[]{2,3,Long.MAX_VALUE})assertThatThrownBy(()->rules.quantity(3,held,2)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->rules.quantity(3,0,0)).isInstanceOf(ResponseStatusException.class);}
 @Test void damagedGoodsCannotBecomeAvailableStock(){rules.disposition("SELLABLE","RESTOCK");rules.disposition("DAMAGED","QUARANTINE");rules.disposition("INCOMPLETE","QUARANTINE");assertThatThrownBy(()->rules.disposition("DAMAGED","RESTOCK")).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->rules.disposition(null,"RESTOCK")).isInstanceOf(ResponseStatusException.class);}
 @Test void approvalAndInspectionRequireAnotherCanonicalActor(){rules.independent("alice","manager");assertThatThrownBy(()->rules.independent("alice","alice")).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(403));}
}
