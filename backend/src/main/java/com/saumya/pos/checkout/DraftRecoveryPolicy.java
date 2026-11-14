package com.saumya.pos.checkout;
import java.time.Instant;
import org.springframework.stereotype.Component;
@Component
public class DraftRecoveryPolicy {
 public boolean expired(String status,Instant expires,Instant now){return "EXPIRED".equals(status)||("DRAFT".equals(status)&&expires!=null&&!expires.isAfter(now));}
 public String message(String status,boolean expired){return switch(status){
  case "PAID"->"Sale is already paid. Recover the original receipt; do not collect cash again.";
  case "CANCELLED"->"Reservation was cancelled. No payment is due on this draft.";
  case "EXPIRED"->"Reservation expired and its stock hold was released. No payment is due.";
  case "DRAFT"->expired?"Reservation time elapsed. Payment is blocked; refresh or cancel the remaining hold before starting again.":"Unpaid stock reservation. Check payment options/status. No external payment is implied by this draft.";
  default->throw new IllegalArgumentException("Unsupported draft status");
 };}
}
