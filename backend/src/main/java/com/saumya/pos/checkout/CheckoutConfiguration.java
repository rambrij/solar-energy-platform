package com.saumya.pos.checkout;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
@Configuration @EnableScheduling
public class CheckoutConfiguration {
    @Bean Clock checkoutClock(){return Clock.systemUTC();}
}
