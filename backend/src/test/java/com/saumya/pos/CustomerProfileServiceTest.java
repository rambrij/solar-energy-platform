package com.saumya.pos;

import com.saumya.pos.identity.CustomerAccess;
import com.saumya.pos.identity.CustomerProfileService;
import com.saumya.pos.identity.internal.CustomerProfile;
import com.saumya.pos.identity.internal.CustomerRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerProfileServiceTest {
 @Test void readsOnlyTheAuthenticatedCustomersGlobalProfile(){
  var access=mock(CustomerAccess.class);var repository=mock(CustomerRepository.class);
  when(access.resolve("buyer@example.test")).thenReturn(42L);
  when(repository.findById(42L)).thenReturn(Optional.of(new CustomerProfile(42,7,"Solar Buyer")));
  var service=new CustomerProfileService(access,repository);
  assertThat(service.read("buyer@example.test")).isEqualTo(new CustomerProfileService.Profile(42,"Solar Buyer","buyer@example.test"));
  verify(access).resolve("buyer@example.test");verifyNoMoreInteractions(access);verify(repository).findById(42L);
 }
 @Test void updatesDisplayNameAfterCustomerOwnershipLock(){
  var access=mock(CustomerAccess.class);var repository=mock(CustomerRepository.class);
  var profile=new CustomerProfile(42,7,"Old name");
  when(access.resolve("buyer@example.test")).thenReturn(42L);
  when(repository.findById(42L)).thenReturn(Optional.of(profile));
  when(repository.saveAndFlush(profile)).thenReturn(profile);
  var service=new CustomerProfileService(access,repository);
  assertThat(service.update("buyer@example.test",new CustomerProfileService.Update("  New name  ")))
   .isEqualTo(new CustomerProfileService.Profile(42,"New name","buyer@example.test"));
  var order= inOrder(access,repository);order.verify(access).resolve("buyer@example.test");order.verify(access).lock(42L);
  order.verify(repository).findById(42L);order.verify(repository).saveAndFlush(profile);
 }
 @Test void clearsAnOptionalBlankDisplayName(){
  var access=mock(CustomerAccess.class);var repository=mock(CustomerRepository.class);
  var profile=new CustomerProfile(42,7,"Name");when(access.resolve("buyer@example.test")).thenReturn(42L);
  when(repository.findById(42L)).thenReturn(Optional.of(profile));when(repository.saveAndFlush(profile)).thenReturn(profile);
  assertThat(new CustomerProfileService(access,repository).update("buyer@example.test",new CustomerProfileService.Update("  ")).displayName()).isNull();
 }
 @Test void missingProfileDoesNotRevealAnotherIdentity(){
  var access=mock(CustomerAccess.class);var repository=mock(CustomerRepository.class);
  when(access.resolve("buyer@example.test")).thenReturn(42L);when(repository.findById(42L)).thenReturn(Optional.empty());
  assertThatThrownBy(()->new CustomerProfileService(access,repository).read("buyer@example.test"))
   .isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(404));
 }
}
