package com.saumya.pos;
import com.saumya.pos.documents.*;
import com.saumya.pos.retail.PosService;
import com.saumya.pos.checkout.CheckoutReceiptService;
import com.saumya.pos.security.AuthorizationContext;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
class ReceiptDocumentServiceTest {
 @Test void invalidReasonAndReferenceCannotReachReceiptOrAuditPersistence(){var service=new ReceiptDocumentService(null,null,null,Clock.systemUTC(),null);for(String reason:new String[]{null," ","x".repeat(251),"private\nnotes"})assertThatThrownBy(()->service.generate("cashier","order-1",reason)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->service.generate("cashier","bad/reference","Copy request")).isInstanceOf(ResponseStatusException.class);}
    
    @Test
    void missingOrForeignReceiptCannotRenderOrSaveAudit() {
        var retail = new PosService(null, null, null) {
            @Override
            public Scope scope() {
                return new Scope(1, 2, "username");
            }
            @Override public void authorize(Scope scope,String actor,String... permissions){}
        };
        assertThatThrownBy(() -> new ReceiptDocumentService(
                retail, null, null, Clock.systemUTC(), new com.saumya.pos.checkout.CheckoutReceiptService(null, null, null, null, null, null) {
            @Override
            public java.util.Map<String, Object> receipt(PosService.Scope scope, String id) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
            }
        }
        ).generate("cashier", "order-1", "Copy request")).isInstanceOf(ResponseStatusException.class);
    }

 @Test void authenticatedVendorWithoutReceiptReprintGrantIsDeniedBeforeReceiptRenderOrAudit(){
  var authorization=mock(AuthorizationContext.class);
  when(authorization.current()).thenReturn(new AuthorizationContext.Context(
      "cashier",7L,12L,Set.of("POS_VIEW"),false,false,true));
  var receipts=mock(CheckoutReceiptService.class);
  var renderer=mock(ReceiptPdfRenderer.class);
  var audits=mock(ReceiptDocumentAuditRepository.class);
  var service=new ReceiptDocumentService(new PosService(null,null,authorization),renderer,audits,
      Clock.systemUTC(),receipts);
  assertThatThrownBy(()->service.generate("cashier","order-1","Customer requested a copy"))
      .isInstanceOfSatisfying(ResponseStatusException.class,error->
          assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
  verifyNoInteractions(receipts,renderer,audits);
 }

}