package com.saumya.pos;
import com.saumya.pos.checkout.DraftRecoveryPolicy;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class DraftRecoveryPolicyTest {
 final DraftRecoveryPolicy policy=new DraftRecoveryPolicy();final Instant now=Instant.parse("2026-11-13T12:00:00Z");
 @Test void expiryBoundaryBlocksPaymentWithoutChangingRecordedState(){assertThat(policy.expired("DRAFT",now.plusSeconds(1),now)).isFalse();assertThat(policy.expired("DRAFT",now,now)).isTrue();assertThat(policy.expired("DRAFT",now.minusSeconds(1),now)).isTrue();assertThat(policy.message("DRAFT",true)).contains("Payment is blocked");}
 @Test void terminalStatesHaveAccurateFinancialMessages(){assertThat(policy.expired("PAID",now.minusSeconds(1),now)).isFalse();assertThat(policy.message("PAID",false)).contains("do not collect cash again");assertThat(policy.message("CANCELLED",false)).contains("No payment is due");assertThat(policy.expired("EXPIRED",now.plusSeconds(1),now)).isTrue();assertThat(policy.message("EXPIRED",true)).contains("stock hold was released");}
 @Test void liveDraftNeverClaimsAnExternalPayment(){assertThat(policy.message("DRAFT",false)).contains("Unpaid","No external payment");}
 @Test void unknownStatusesCannotMasqueradeAsUnpaid(){assertThatThrownBy(()->policy.message("UNKNOWN",false)).isInstanceOf(IllegalArgumentException.class);}
}
