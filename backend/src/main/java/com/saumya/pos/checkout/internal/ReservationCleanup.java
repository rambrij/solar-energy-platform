package com.saumya.pos.checkout.internal;
import com.saumya.pos.checkout.CheckoutService;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
@Component @ConditionalOnProperty(name="checkout.cleanup-enabled",havingValue="true",matchIfMissing=true)
public class ReservationCleanup {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(ReservationCleanup.class);
    private final DraftRepository orders;private final CheckoutService checkout;private final Clock clock;
    public ReservationCleanup(DraftRepository orders,CheckoutService checkout,Clock clock){this.orders=orders;this.checkout=checkout;this.clock=clock;}
    @Scheduled(fixedDelayString="${checkout.cleanup-delay-ms:60000}",initialDelayString="${checkout.cleanup-delay-ms:60000}")
    public void sweep(){for(var order:orders.findTop100ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc("DRAFT",clock.instant()))try{checkout.expire(order.tenant(),order.id());}catch(RuntimeException failure){log.error("Reservation cleanup failed for tenant={} order={}",order.tenant(),order.id(),failure);}}
}
