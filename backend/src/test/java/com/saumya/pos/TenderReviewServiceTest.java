package com.saumya.pos;
import com.saumya.pos.checkout.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.*;
class TenderReviewServiceTest {
 private PaymentCheckoutService.View evidence(){return new PaymentCheckoutService.View("order","attempt","UPI","CONFIRMED",BigDecimal.TEN,"vendor",false,"Evidence saved",true,"DRAFT","cashier","ref","manager",Instant.now(),"proof");}
 @Test void finalizationFailureDoesNotDiscardCommittedEvidence(){var saved=evidence();var calls=new AtomicInteger();var payments=new PaymentCheckoutService(null,null,null,null,null,null,null,null,Clock.systemUTC()){
  public View verifyEvidence(String user,String order,Review request){calls.incrementAndGet();return saved;}
  public View finalizeVerified(String user,String order,String attempt){throw new ResponseStatusException(HttpStatus.CONFLICT,"Price changed; reconcile");}
 };var result=new TenderReviewService(payments).review("manager","order",new PaymentCheckoutService.Review("attempt","CONFIRM","ref","proof","Verified"));assertThat(result).isSameAs(saved);assertThat(result.orderStatus()).isEqualTo("DRAFT");assertThat(calls.get()).isEqualTo(1);}
 @Test void authorizationFailureBeforeEvidenceIsNotSuppressed(){var payments=new PaymentCheckoutService(null,null,null,null,null,null,null,null,Clock.systemUTC()){
  public View verifyEvidence(String user,String order,Review request){throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Independent supervisor required");}
 };assertThatThrownBy(()->new TenderReviewService(payments).review("cashier","order",null)).hasMessageContaining("supervisor");}
}
