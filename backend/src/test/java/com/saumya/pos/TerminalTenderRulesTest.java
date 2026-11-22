package com.saumya.pos;
import com.saumya.pos.checkout.TerminalTenderRules;
import com.saumya.pos.checkout.internal.PaymentAttempt;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class TerminalTenderRulesTest {
 private final TerminalTenderRules rules=new TerminalTenderRules();
 @Test void onlyIndependentReviewWithPlainEvidenceIsAccepted(){assertThatThrownBy(()->rules.independent("cashier","cashier"));rules.independent("cashier","manager");for(String value:new String[]{null,"abc","4111111111111111","CVV-123","evidence\nsecret"})assertThatThrownBy(()->rules.token(value,100));for(String value:new String[]{null," ","PIN checked","4111 1111 1111 1111","line\nnotes"})assertThatThrownBy(()->rules.reason(value));assertThat(rules.token("bank-result-123",80)).isEqualTo("bank-result-123");assertThat(rules.reason(" Verified vendor funds ")).isEqualTo("Verified vendor funds");assertThatThrownBy(()->rules.decision("PENDING"));}
 @Test void uncertainTerminalTenderRetainsItsIdentityUntilIndependentDecision(){var a=PaymentAttempt.terminal(1,"order-1","UPI","vendor-account",BigDecimal.TEN,Instant.EPOCH);String id=a.id();assertThat(a.state()).isEqualTo("PENDING_VERIFICATION");assertThat(a.unresolved()).isTrue();a.unknown();assertThat(a.state()).isEqualTo("UNKNOWN_RECONCILIATION_REQUIRED");assertThat(a.unresolved()).isTrue();a.review("manager","CONFIRM","bank-123","proof-123","Verified full amount",Instant.EPOCH.plusSeconds(10));assertThat(a.id()).isEqualTo(id);assertThat(a.state()).isEqualTo("CONFIRMED");assertThat(a.unresolved()).isFalse();assertThat(a.reviewer()).isEqualTo("manager");assertThat(a.merchant()).isEqualTo("vendor-account");}
}
