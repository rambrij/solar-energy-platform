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
    private AuthorizationContext.Context vendorActor(){return new AuthorizationContext.Context("vendor-admin",1L,1L,java.util.Set.of("USER_CREATE"),false,false,true);}
    private AuthorizationContext.Context platformActor(){return new AuthorizationContext.Context("platform-admin",null,null,java.util.Set.of("PLATFORM_TENANT_CREATE"),true,false,false);}
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
    @Test void customerIdentityCannotBeReusedAsVendorEmployee(){
        when(platform.permissions("customer@vendor.test")).thenReturn(java.util.List.of());
        when(customers.profileCount("customer@vendor.test")).thenReturn(1L);
        var service=new IdentityProvisioningServiceImpl(users,sequences,invitations,platform,customers,new BCryptPasswordEncoder(4),Clock.systemUTC());
        authorize(service,vendorActor());
        assertThatThrownBy(()->service.issueInvitation("customer@vendor.test"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("Customer identities cannot receive vendor membership");
        verifyNoInteractions(users,sequences,invitations);
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
