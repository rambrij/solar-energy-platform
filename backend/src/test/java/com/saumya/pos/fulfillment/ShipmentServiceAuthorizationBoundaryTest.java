package com.saumya.pos.fulfillment;
import com.saumya.pos.checkout.OrderHistoryService;
import com.saumya.pos.security.AuthorizationContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class ShipmentServiceAuthorizationBoundaryTest {
 @Test void deniedVendorCallsStopBeforePersistence(){
  var db=mock(JdbcTemplate.class);var auth=mock(AuthorizationContext.class);
  when(auth.current()).thenReturn(new AuthorizationContext.Context("v@test",7L,1L,"7:v@test",Set.of("POS_VIEW"),false,false,true));
  var service=new ShipmentService(db,auth,mock(OrderHistoryService.class));
  assertThatThrownBy(service::eligibleOrders).isInstanceOf(ResponseStatusException.class);
  assertThatThrownBy(service::shipments).isInstanceOf(ResponseStatusException.class);
  assertThatThrownBy(()->service.createShipment("o","c","t",null,"r")).isInstanceOf(ResponseStatusException.class);
  assertThatThrownBy(()->service.addEvent(1,"IN_TRANSIT","moving","r")).isInstanceOf(ResponseStatusException.class);
  verifyNoInteractions(db);
 }
 @Test void customerTrackingUsesOnlyTheTenantFromAuthenticatedOwnedOrder(){
  var db=mock(JdbcTemplate.class);var auth=mock(AuthorizationContext.class);var orderHistory=mock(OrderHistoryService.class);
  when(orderHistory.customerOrderTenant("buyer@test","owned")).thenReturn(7L);
  var t=Timestamp.from(Instant.parse("2026-11-26T08:00:00Z"));
  when(db.queryForList(contains("FROM shipments WHERE tenant_id=? AND order_id=?"),eq(7L),eq("owned")))
   .thenReturn(List.of(Map.of("id",2L,"carrier","Carrier","tracking_reference","T-1","tracking_url","https://carrier.test/t","created_at",t)));
  when(db.queryForList(contains("FROM shipment_events WHERE tenant_id=? AND shipment_id=?"),eq(7L),eq(2L)))
   .thenReturn(List.of(Map.of("id",3L,"event_code","IN_TRANSIT","description","On the way","occurred_at",t)));
  var result=new ShipmentService(db,auth,orderHistory).customerShipments("buyer@test","owned");
  assertThat(result).hasSize(1);
  assertThat(result.getFirst().events().getFirst().description()).isEqualTo("On the way");
  verify(orderHistory).customerOrderTenant("buyer@test","owned");
 }
 @Test void customerShipmentFeedDerivesIdentityAndReturnsOnlyPaidOnlineCustomerSafeTracking(){
  var db=mock(JdbcTemplate.class);var history=mock(OrderHistoryService.class);
  when(history.customerPaidOnlineOrderReferences("buyer@test")).thenReturn(List.of(new OrderHistoryService.CustomerOrderReference("owned-order",7L,"Solar Vendor")));
  var t=Timestamp.from(Instant.parse("2026-11-26T08:00:00Z"));Map<String,Object> row=new HashMap<>();
  row.put("tenant_id",7L);row.put("id",2L);row.put("order_id","owned-order");row.put("vendor_name","Solar Vendor");row.put("carrier","Carrier");row.put("tracking_reference","T-1");row.put("tracking_url","https://carrier.test/t");row.put("created_at",t);
  when(db.queryForList(contains("FROM shipments WHERE tenant_id=? AND order_id=?"),eq(7L),eq("owned-order"))).thenReturn(List.of(Map.of("id",2L,"order_id","owned-order","carrier","Carrier","tracking_reference","T-1","tracking_url","https://carrier.test/t","created_at",t)));
  when(db.queryForList(contains("FROM shipment_events WHERE tenant_id=? AND shipment_id=?"),eq(7L),eq(2L))).thenReturn(List.of(Map.of("id",3L,"event_code","IN_TRANSIT","description","On the way","occurred_at",t)));
  var result=new ShipmentService(db,mock(AuthorizationContext.class),history).customerShipmentFeed("buyer@test");
  assertThat(result).hasSize(1);assertThat(result.getFirst().orderId()).isEqualTo("owned-order");assertThat(result.getFirst().vendorName()).isEqualTo("Solar Vendor");
  assertThat(result.getFirst().events().getFirst().description()).isEqualTo("On the way");
  assertThat(Arrays.stream(ShipmentService.CustomerEvent.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList()).containsExactly("id","eventCode","description","occurredAt");
  verify(history).customerPaidOnlineOrderReferences("buyer@test");
  verify(db).queryForList(contains("FROM shipments WHERE tenant_id=? AND order_id=?"),eq(7L),eq("owned-order"));
 }
 @Test void foreignCustomerOrderIsRejectedBeforeShipmentLookup(){
  var db=mock(JdbcTemplate.class);var orderHistory=mock(OrderHistoryService.class);
  when(orderHistory.customerOrderTenant("buyer@test","foreign")).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Order unavailable"));
  assertThatThrownBy(()->new ShipmentService(db,mock(AuthorizationContext.class),orderHistory).customerShipments("buyer@test","foreign"))
   .isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(404));
  verifyNoInteractions(db);
 }
}
