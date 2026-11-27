package com.saumya.pos.tenant;

import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.tenant.internal.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VendorTenantProfileServiceTest {
 private static final Instant NOW=Instant.parse("2026-11-27T08:00:00Z");
 private final TenantRepository tenants=mock(TenantRepository.class);
 private final TenantProfileAuditRepository audit=mock(TenantProfileAuditRepository.class);
 private final AuthorizationContext authorization=mock(AuthorizationContext.class);
 private final VendorTenantProfileService service=new VendorTenantProfileService(tenants,audit,authorization,Clock.fixed(NOW,ZoneOffset.UTC));
 private void actor(String... permissions){when(authorization.current()).thenReturn(new AuthorizationContext.Context("vendor.admin@example.test",81L,810L,"81:vendor.admin@example.test",Set.of(permissions),false,false,true));}
 private Tenant tenant(){return new Tenant(81,"solar-rooftops","Solar Rooftops","Solar Rooftops Private Limited","admin@solar.example","9876543210","GSTIN-OLD","Old business address","vendor.admin@example.test",null,NOW.minusSeconds(3600));}
 @Test void readsOnlyTheAuthenticatedMembershipTenant(){
  actor("TENANT_PROFILE_VIEW");when(tenants.findById(81L)).thenReturn(Optional.of(tenant()));
  var result=service.read();assertThat(result.tenantId()).isEqualTo(81L);assertThat(result.slug()).isEqualTo("solar-rooftops");verify(tenants).findById(81L);verifyNoInteractions(audit);
 }
 @Test void updatesExistingTenantProfileWithVersionAndFieldNameOnlyAudit(){
  actor("TENANT_PROFILE_UPDATE");var t=tenant();when(tenants.lockById(81L)).thenReturn(Optional.of(t));when(tenants.saveAndFlush(any(Tenant.class))).thenAnswer(x->x.getArgument(0));when(audit.saveAndFlush(any(TenantProfileAuditEvent.class))).thenAnswer(x->x.getArgument(0));
  var result=service.update(new VendorTenantProfileService.Update(0,"Solar Rooftops India","Solar Rooftops Private Limited","contact@solar.example","9876500000","GSTIN-NEW","New business address"));
  assertThat(result.tenantId()).isEqualTo(81L);assertThat(result.slug()).isEqualTo("solar-rooftops");assertThat(result.name()).isEqualTo("Solar Rooftops India");
  var captor=org.mockito.ArgumentCaptor.forClass(TenantProfileAuditEvent.class);verify(audit).saveAndFlush(captor.capture());
  assertThat(captor.getValue().changedFields()).isEqualTo("name,contactEmail,contactPhone,taxIdentifier,address");
  assertThat(captor.getValue().changedFields()).doesNotContain("contact@solar.example","GSTIN-NEW");
 }
 @Test void staleVersionIsRejectedWithoutMutationOrAudit(){
  actor("TENANT_PROFILE_UPDATE");when(tenants.lockById(81L)).thenReturn(Optional.of(tenant()));
  assertThatThrownBy(()->service.update(new VendorTenantProfileService.Update(7,"Changed","Legal",null,null,null,null))).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
  verify(tenants,never()).saveAndFlush(any(Tenant.class));verifyNoInteractions(audit);
 }
 @Test void updateRequiresItsOwnGrantBeforeTenantLookup(){
  actor("TENANT_PROFILE_VIEW");
  assertThatThrownBy(()->service.update(new VendorTenantProfileService.Update(0,"Changed","Legal",null,null,null,null))).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
  verifyNoInteractions(tenants,audit);
 }
}
