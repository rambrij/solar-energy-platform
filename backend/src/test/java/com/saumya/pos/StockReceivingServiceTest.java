package com.saumya.pos;
import com.saumya.pos.inventory.StockReceivingService;
import com.saumya.pos.inventory.internal.*;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import com.saumya.pos.security.AuthorizationContext;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class StockReceivingServiceTest {
 @Test void rejectsMalformedCommandsBeforeTouchingInventory(){var service=new StockReceivingService(null,null,Clock.systemUTC());assertThatThrownBy(()->service.receive(1,1,"actor","short",new StockReceivingService.Input(1,1,"Approved"))).hasMessageContaining("Valid product");assertThatThrownBy(()->service.receive(1,1,"actor","valid-key",new StockReceivingService.Input(1,0,"Approved"))).hasMessageContaining("Valid product");authorize(service);assertThatThrownBy(()->service.list(1,1,0,101,null)).hasMessageContaining("pagination");}
 private void authorize(StockReceivingService service){
  var context=mock(AuthorizationContext.class);
  when(context.current()).thenReturn(new AuthorizationContext.Context("actor", 1L, 1L, String.valueOf(1L) + ":" + ("actor"), Set.of("INVENTORY_RECEIVE","INVENTORY_VIEW"), false, false, true));
  ReflectionTestUtils.setField(service,"authorization",context);
 }
 @Test void identicalRetryReturnsSavedReceiptAndChangedRetryCannotAddStock(){
  var saved=new StockReceipt(1,1,7,5,"actor","Delivery","receipt-key",5,java.time.Instant.EPOCH);
  var repository=(StockReceiptRepository)java.lang.reflect.Proxy.newProxyInstance(StockReceiptRepository.class.getClassLoader(),new Class<?>[]{StockReceiptRepository.class},(p,m,a)->{assertThat(m.getName()).isEqualTo("findByTenantAndBranchAndKey");assertThat(a).containsExactly(1L,1L,"receipt-key");return Optional.of(saved);});var service=new StockReceivingService(repository,null,Clock.systemUTC());authorize(service);
  assertThat(service.receive(1,1,"actor","receipt-key",new StockReceivingService.Input(7,5,"Delivery")).id()).isEqualTo(saved.id());
  assertThatThrownBy(()->service.receive(1,1,"actor","receipt-key",new StockReceivingService.Input(7,6,"Delivery"))).hasMessageContaining("different command");
 }

 @Test void directReceiveWithoutPermissionIsDeniedBeforeRepositoryOrInventoryAccess(){
  var repository=mock(StockReceiptRepository.class);
  var inventory=mock(com.saumya.pos.inventory.InventoryService.class);
  var service=new StockReceivingService(repository,inventory,Clock.systemUTC());
  var authorization=mock(AuthorizationContext.class);
  when(authorization.current()).thenReturn(new AuthorizationContext.Context("actor", 1L, 1L, String.valueOf(1L) + ":" + ("actor"), Set.of(), false, false, true));
  ReflectionTestUtils.setField(service,"authorization",authorization);
  assertThatThrownBy(()->service.receive(1,1,"actor","receipt-key",new StockReceivingService.Input(7,5,"Delivery")))
    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
    .satisfies(error->assertThat(((org.springframework.web.server.ResponseStatusException)error).getStatusCode().value()).isEqualTo(403));
  verifyNoInteractions(repository,inventory);
 }

 @Test void trackedReceiptRequiresExactlyOneDistinctSerialPerUnit(){
  var productRepo=mock(com.saumya.pos.catalog.internal.ProductRepository.class);
  var product=new com.saumya.pos.catalog.internal.Product(7,1);
  product.edit("P7",null,"Panel",java.math.BigDecimal.ONE,java.math.BigDecimal.ZERO,true,true);
  when(productRepo.locked(1,7)).thenReturn(Optional.of(product));
  var service=new StockReceivingService(mock(StockReceiptRepository.class),mock(com.saumya.pos.inventory.InventoryService.class),Clock.systemUTC(),productRepo,mock(InventoryReceiptSerialAssetRepository.class));
  authorize(service);
  assertThatThrownBy(()->service.receive(1,1,"actor","serial-key-01",new StockReceivingService.Input(7,2,"Delivery",java.util.List.of("SN-1"))))
   .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
   .satisfies(e->assertThat(((org.springframework.web.server.ResponseStatusException)e).getStatusCode().value()).isEqualTo(400));
  assertThatThrownBy(()->service.receive(1,1,"actor","serial-key-02",new StockReceivingService.Input(7,2,"Delivery",java.util.List.of("SN-1"," sn-1 "))))
   .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
   .satisfies(e->assertThat(((org.springframework.web.server.ResponseStatusException)e).getStatusCode().value()).isEqualTo(400));
 }
 @Test void identicalSerializedRetryComparesAndReturnsThePersistedSerials(){
  var saved=new StockReceipt(1,1,7,2,"actor","Delivery","serial-key-03",8,java.time.Instant.EPOCH);
  var receiptRepo=mock(StockReceiptRepository.class);when(receiptRepo.findByTenantAndBranchAndKey(1,1,"serial-key-03")).thenReturn(Optional.of(saved));
  var serialRepo=mock(InventoryReceiptSerialAssetRepository.class);
  var one=mock(InventoryReceiptSerialAsset.class);when(one.serialNumber()).thenReturn("SN-1");
  var two=mock(InventoryReceiptSerialAsset.class);when(two.serialNumber()).thenReturn("SN-2");
  when(serialRepo.findByTenantAndBranchAndReceiptIdOrderByIdAsc(1,1,saved.id())).thenReturn(java.util.List.of(one,two));
  var service=new StockReceivingService(receiptRepo,null,Clock.systemUTC(),null,serialRepo);authorize(service);
  var view=service.receive(1,1,"actor","serial-key-03",new StockReceivingService.Input(7,2,"Delivery",java.util.List.of("sn-1","SN-2")));
  assertThat(view.serialNumbers()).containsExactly("SN-1","SN-2");
  verify(receiptRepo,never()).saveAndFlush(any());
 }
}
