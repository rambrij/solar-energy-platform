package com.saumya.pos.subscription;

import com.saumya.pos.checkout.internal.PaymentAttempt;
import com.saumya.pos.checkout.internal.InvoiceSnapshot;
import com.saumya.pos.subscription.internal.SubscriptionPaymentRecord;
import com.saumya.pos.subscription.internal.SubscriptionInvoiceRecord;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SubscriptionPaymentSeparationTest {
    @Test void platformSubscriptionBillingUsesDifferentPersistenceTablesFromVendorCommerce(){
        String vendorPayment=PaymentAttempt.class.getAnnotation(jakarta.persistence.Table.class).name();
        String platformPayment=SubscriptionPaymentRecord.class.getAnnotation(jakarta.persistence.Table.class).name();
        String vendorInvoice=InvoiceSnapshot.class.getAnnotation(jakarta.persistence.Table.class).name();
        String platformInvoice=SubscriptionInvoiceRecord.class.getAnnotation(jakarta.persistence.Table.class).name();
        assertThat(platformPayment).isEqualTo("subscription_payments").isNotEqualTo(vendorPayment);
        assertThat(vendorPayment).isEqualTo("vendor_payment_attempts");
        assertThat(platformInvoice).isEqualTo("subscription_invoices").isNotEqualTo(vendorInvoice);
        assertThat(vendorInvoice).isEqualTo("vendor_invoices");
    }
}
