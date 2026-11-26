package com.saumya.pos;

import com.saumya.pos.identity.IdentityProvisioningServiceImpl;
import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.identity.internal.*;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdentityProvisioningServiceTest {
    private void authorize(IdentityProvisioningServiceImpl service,AuthorizationContext.Context actor){
        var context=mock(AuthorizationContext.class);when(context.current()).thenReturn(actor);ReflectionTestUtils.setField(service,"authorization",context);
    }
    private AuthorizationContext.Context vendorActor(){return new AuthorizationContext.Context("vendor-admin", 1L, 1L, String.valueOf(1L) + ":" + ("vendor-admin"), java.util.Set.of("USER_CREATE"), false, false, true);}
    private AuthorizationContext.Context platformActor(){return new AuthorizationContext.Context("platform-admin", null, null, null, java.util.Set.of("PLATFORM_TENANT_CREATE"), true, false, false);}
    @Mock GlobalUserRepository users;
    @Mock IdentitySequenceRepository sequences;
    @Mock IdentityInvitationRepository invitations;
    @Mock PlatformGrantRepository platform;
    @Mock CustomerRepository customers;
    @Test void directInvitationWithoutTrustedPlatformOrVendorActorIsDeniedBeforePersistence(){
        var service=new IdentityProvisioningServiceImpl(users,sequences,invitations,platform,customers,new BCryptPasswordEncoder(4),Clock.systemUTC());
        assertThatThrownBy(()->service.issueInvitation("new-employee@vendor.test"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(users,sequences,invitations);
    }
    @Test void vendorInvitationStoresOnlyHashAndCanBeConsumedOnce(){
        Instant now=Instant.parse("2026-11-16T00:00:00Z");
        var user=new GlobalUser(7,"admin@vendor.test","$2a$10$disabled",false);
        when(platform.permissions("admin@vendor.test")).thenReturn(java.util.List.of());
        when(users.findByUsername("admin@vendor.test")).thenReturn(Optional.of(user));
        final IdentityInvitation[] saved={null};
        when(invitations.saveAndFlush(any())).thenAnswer(inv->{saved[0]=inv.getArgument(0);return saved[0];});
        when(invitations.locked(anyString())).thenAnswer(inv->Optional.ofNullable(saved[0]));
        var encoder=new BCryptPasswordEncoder(4);
        var service=new IdentityProvisioningServiceImpl(users,sequences,invitations,platform,customers,encoder,Clock.fixed(now,ZoneOffset.UTC));
        authorize(service,vendorActor());
        var invitation=service.issueInvitation(" ADMIN@VENDOR.TEST ");
        assertThat(invitation.token()).hasSize(43);
        assertThat(ReflectionTestUtils.getField(saved[0],"tokenHash")).isNotEqualTo(invitation.token());
        assertThat(saved[0].expiresAt()).isEqualTo(now.plus(Duration.ofHours(48)));
        service.acceptInvitation(invitation.token(),"StrongVendorPassword2026");
        assertThat(user.enabled()).isTrue();
        assertThat(encoder.matches("StrongVendorPassword2026",user.password())).isTrue();
        assertThat(saved[0].consumedAt()).isEqualTo(now);
        assertThatThrownBy(()->service.acceptInvitation(invitation.token(),"AnotherStrongPassword2026"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void invitationStatusDistinguishesPendingExpiredAndActiveAccounts(){
        Instant now=Instant.parse("2026-11-26T00:00:00Z");
        var pending=new IdentityInvitation("pending-hash","pending@vendor.test",now.plusSeconds(3600),now.minusSeconds(60));
        var expired=new IdentityInvitation("expired-hash","expired@vendor.test",now.minusSeconds(1),now.minusSeconds(7200));
        when(users.findByUsername("pending@vendor.test")).thenReturn(Optional.of(new GlobalUser(11,"pending@vendor.test","hash",false)));
        when(users.findByUsername("expired@vendor.test")).thenReturn(Optional.of(new GlobalUser(12,"expired@vendor.test","hash",false)));
        when(users.findByUsername("active@vendor.test")).thenReturn(Optional.of(new GlobalUser(13,"active@vendor.test","hash",true)));
        when(invitations.findTopByUsernameOrderByCreatedAtDesc("pending@vendor.test")).thenReturn(Optional.of(pending));
        when(invitations.findTopByUsernameOrderByCreatedAtDesc("expired@vendor.test")).thenReturn(Optional.of(expired));
        var service=new IdentityProvisioningServiceImpl(users,sequences,invitations,platform,customers,new BCryptPasswordEncoder(4),Clock.fixed(now,ZoneOffset.UTC));
        assertThat(service.invitationStatus("pending@vendor.test").status()).isEqualTo("PENDING");
        assertThat(service.invitationStatus("pending@vendor.test").expiresAt()).isEqualTo(now.plusSeconds(3600));
        assertThat(service.invitationStatus("expired@vendor.test").status()).isEqualTo("EXPIRED");
        assertThat(service.invitationStatus("active@vendor.test").status()).isEqualTo("ACTIVE");
        verify(invitations,never()).findTopByUsernameOrderByCreatedAtDesc("active@vendor.test");
    }

    @Test void existingCustomerIdentityCanBeReusedForVendorMembershipOnboarding(){
        when(platform.permissions("customer@vendor.test")).thenReturn(java.util.List.of());
        var existingCustomer=new GlobalUser(9,"customer@vendor.test","$2a$10$active",true);
        when(users.findByUsername("customer@vendor.test")).thenReturn(Optional.of(existingCustomer));
        var service=new IdentityProvisioningServiceImpl(users,sequences,invitations,platform,customers,new BCryptPasswordEncoder(4),Clock.systemUTC());
        // Initial tenant onboarding is Platform-authorized; the existing Customer
        // profile must not be rejected once the Platform actor passes this boundary.
        authorize(service,platformActor());

        service.ensureVendorEligible("customer@vendor.test");
        var invitation=service.issueInvitation("customer@vendor.test");

        assertThat(invitation.username()).isEqualTo("customer@vendor.test");
        assertThat(invitation.accountAlreadyActive()).isTrue();
        assertThat(invitation.token()).isNull();
        verify(users).findByUsername("customer@vendor.test");
        verifyNoInteractions(sequences,invitations);
        verifyNoInteractions(customers);
    }
    @Test void platformIdentityCannotBeReusedAsVendorAdmin(){
        when(platform.permissions("platform@saumya.test")).thenReturn(java.util.List.of("PLATFORM_TENANT_CREATE"));
        var service=new IdentityProvisioningServiceImpl(users,sequences,invitations,platform,customers,new BCryptPasswordEncoder(4),Clock.systemUTC());
        authorize(service,platformActor());
        assertThatThrownBy(()->service.ensureVendorEligible("platform@saumya.test"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("Platform identities cannot receive vendor membership");
        verifyNoInteractions(users,sequences,invitations);
    }
}
