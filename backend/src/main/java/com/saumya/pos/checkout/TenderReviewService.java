package com.saumya.pos.checkout;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
/** Evidence is committed before local finalization; retries never discard a verified external charge. */
@Service
public class TenderReviewService {
 private final PaymentCheckoutService payments;
 public TenderReviewService(PaymentCheckoutService payments){this.payments=payments;}
 public PaymentCheckoutService.View review(String username,String order,PaymentCheckoutService.Review request){
  var evidence=payments.verifyEvidence(username,order,request);
  if(!"CONFIRMED".equals(evidence.state()))return evidence;
  try{return payments.finalizeVerified(username,order,request.attemptId());}
  catch(ResponseStatusException error){if(!error.getStatusCode().is4xxClientError())throw error;return evidence;}
 }
}
