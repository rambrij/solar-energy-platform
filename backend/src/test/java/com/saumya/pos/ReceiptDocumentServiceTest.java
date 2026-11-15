package com.saumya.pos;
import com.saumya.pos.documents.*;
import com.saumya.pos.retail.PosService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
class ReceiptDocumentServiceTest {
 @Test void invalidReasonAndReferenceCannotReachReceiptOrAuditPersistence(){var service=new ReceiptDocumentService(null,null,null,Clock.systemUTC(),null);for(String reason:new String[]{null," ","x".repeat(251),"private\nnotes"})assertThatThrownBy(()->service.generate("cashier","order-1",reason)).isInstanceOf(ResponseStatusException.class);assertThatThrownBy(()->service.generate("cashier","bad/reference","Copy request")).isInstanceOf(ResponseStatusException.class);}
 @Test void missingOrForeignReceiptCannotRenderOrSaveAudit(){var retail=new PosService(null,null){@Override public Scope scope(String username){return new Scope(1,2,username);}};assertThatThrownBy(()->new ReceiptDocumentService(retail,null,null,Clock.systemUTC(),new com.saumya.pos.checkout.CheckoutReceiptService(null,null,null,null,null,null){@Override public java.util.Map<String,Object> receipt(PosService.Scope scope,String id){throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);}}).generate("cashier","order-1","Copy request")).isInstanceOf(ResponseStatusException.class);}
}
