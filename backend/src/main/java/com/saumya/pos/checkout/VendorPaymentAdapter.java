package com.saumya.pos.checkout;
import java.math.BigDecimal;
/** Provider implementations must authenticate their result and preserve the vendor merchant context. */
public interface VendorPaymentAdapter {
 record Confirmation(String merchantAccount,String attemptId,BigDecimal amount,String reference){}
 Confirmation confirmSimulation(String merchantAccount,String attemptId,BigDecimal amount);
}
