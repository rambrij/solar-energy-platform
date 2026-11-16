package com.saumya.pos;
import com.saumya.pos.checkout.BuyerDetailsPolicy;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class BuyerDetailsPolicyTest {
 private final BuyerDetailsPolicy policy=new BuyerDetailsPolicy();
 @Test void guestAndBlankDetailsRemainAbsent(){assertThat(policy.normalize(null,false)).isNull();assertThat(policy.normalize(new BuyerDetailsPolicy.Input(" ",null,null,""),false)).isNull();}
 @Test void approvedInvoiceRequirementNeedsNameAndAddressButNoContactOrAccount(){assertThatThrownBy(()->policy.normalize(null,true)).hasMessageContaining("Buyer name and address");var value=policy.normalize(new BuyerDetailsPolicy.Input(" Buyer ",null,null," Street\nCity "),true);assertThat(value.name()).isEqualTo("Buyer");assertThat(value.address()).isEqualTo("Street\nCity");assertThat(value.contact()).isNull();}
 @Test void invalidDataErrorsNeverEchoPrivateValues(){String privateValue="PRIVATE-TAX-ID\n";assertThatThrownBy(()->policy.normalize(new BuyerDetailsPolicy.Input(null,null,privateValue,null),false)).hasMessageNotContaining("PRIVATE-TAX-ID");assertThatThrownBy(()->policy.normalize(new BuyerDetailsPolicy.Input("x".repeat(161),null,null,null),false)).hasMessageContaining("allowed size");}
 @Test void delimiterLikeTextCannotMakeDifferentBuyerFieldsShareACommandFingerprint(){var first=new BuyerDetailsPolicy.Input("a, contact=b","c",null,null);var second=new BuyerDetailsPolicy.Input("a","b, contact=c",null,null);assertThat(first.toString()).isEqualTo(second.toString());assertThat(policy.fingerprint(first)).isNotEqualTo(policy.fingerprint(second));}
}
