package com.saumya.pos;
import com.saumya.pos.checkout.*;
import com.saumya.pos.checkout.internal.*;
import com.saumya.pos.identity.CustomerAccess;
import com.saumya.pos.retail.PosService;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
class OnlineReceiptServiceTest {
 @SuppressWarnings("unchecked") private <T>T repository(Class<T> type,java.lang.reflect.InvocationHandler handler){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);}
 @Test void invalidReferencesStopBeforeIdentityOrPersistence(){var service=new OnlineReceiptService(null,null,null,null,null);for(String id:new String[]{null,"","../order","x".repeat(37)})assertThatThrownBy(()->service.read("buyer",id)).isInstanceOf(ResponseStatusException.class);}
 @Test void missingOrUnpaidOrderCannotReadLinesOrInvoices(){
  var customers=new CustomerAccess(null){public long resolve(String name){return 7;}};
  var orders=repository(DraftRepository.class,(p,m,a)->Optional.empty());var service=new OnlineReceiptService(orders,null,null,customers,null);assertThatThrownBy(()->service.read("buyer","order-1")).isInstanceOf(ResponseStatusException.class);
  var draft=new DraftOrder("order-1",1,7L,null,null,null,"ONLINE","key","hash",BigDecimal.TEN,2,"owner",Instant.now(),false);
  orders=repository(DraftRepository.class,(p,m,a)->Optional.of(draft));var unpaid=new OnlineReceiptService(orders,null,null,customers,null);assertThatThrownBy(()->unpaid.copy("buyer","order-1")).isInstanceOf(ResponseStatusException.class);
 }
 @Test void readsSavedAmountsAndOnlyCopiesWriteCustomerScopedAudit(){
  var order=new DraftOrder("order-1",1,7L,null,null,null,"ONLINE","key","hash",BigDecimal.TEN,2,"owner",Instant.now(),false);order.payExternal("UPI",null,false,Instant.parse("2026-11-14T00:00:00Z"));
  var customers=new CustomerAccess(null){public long resolve(String name){return 7;}};
  var orders=repository(DraftRepository.class,(p,m,a)->{assertThat(a).containsExactly(7L,"order-1");return Optional.of(order);});
  var lines=repository(DraftLineRepository.class,(p,m,a)->{assertThat(a).containsExactly(1L,"order-1");return List.of(new DraftLine(1,"order-1",9,"Saved panel",1,BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.TEN));});
  var invoices=repository(InvoiceSnapshotRepository.class,(p,m,a)->Optional.empty());var audit=new ArrayList<String>();
  var retail=new PosService(null,null){public void draftAudit(Scope scope,String action,String id){assertThat(scope).isEqualTo(new Scope(1,2,"customer:7"));audit.add(action+":"+id);}};
  var service=new OnlineReceiptService(orders,lines,invoices,customers,retail);var receipt=service.read("buyer","order-1");assertThat(receipt.copy()).isFalse();assertThat(receipt.seller()).isNull();assertThat(receipt.items().getFirst().name()).isEqualTo("Saved panel");assertThat(receipt.paymentMethod()).isEqualTo("MOCK_UPI");assertThat(audit).isEmpty();
  var copy=service.copy("buyer","order-1");assertThat(copy.copy()).isTrue();assertThat(copy.total()).isEqualTo(receipt.total());assertThat(copy.issuedAt()).isEqualTo(receipt.issuedAt());assertThat(audit).containsExactly("CUSTOMER_RECEIPT_COPY:order-1");
 }
}
