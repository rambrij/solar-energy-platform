package com.saumya.pos.checkout;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;
/** Explicit simulation endpoint only; this implementation never contacts a payment network. */
@Component
public class MockVendorPaymentAdapter implements VendorPaymentAdapter {
 private final PaymentPolicy policy;
 public MockVendorPaymentAdapter(PaymentPolicy policy){this.policy=policy;}
 public Confirmation confirmSimulation(String merchant,String attempt,BigDecimal amount){policy.requireEnabled();return new Confirmation(merchant,attempt,amount,"MOCK-"+attempt);}
}
