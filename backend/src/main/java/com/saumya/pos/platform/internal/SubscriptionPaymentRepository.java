package com.saumya.pos.platform.internal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment,String> { List<SubscriptionPayment> findByInvoiceIdOrderByRecordedAtAscIdAsc(String invoiceId); }
