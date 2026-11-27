package com.saumya.pos.checkout;

import com.saumya.pos.checkout.internal.VendorPaymentAccount;
import com.saumya.pos.checkout.internal.VendorPaymentAccountEvent;
import com.saumya.pos.checkout.internal.VendorPaymentAccountEventRepository;
import com.saumya.pos.checkout.internal.VendorPaymentAccountRepository;
import com.saumya.pos.security.AuthorizationContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VendorPaymentAccountServiceTest {
    @Mock VendorPaymentAccountRepository accounts;
    @Mock VendorPaymentAccountEventRepository events;
    @Mock AuthorizationContext authorization;
    Clock clock;
    VendorPaymentAccountService service;
    final Instant now=Instant.parse("2026-11-27T10:00:00Z");

    @BeforeEach void setUp() {
        clock=Clock.fixed(now,ZoneOffset.UTC);
        service=new VendorPaymentAccountService(accounts,events,authorization,clock);
    }

    private AuthorizationContext.Context actor(String... permissions) {
        return new AuthorizationContext.Context("vendor.admin@example.test",71L,null,"membership-1",
            Set.of(permissions),false,false,true);
    }

    @Test void firstSubmissionCreatesPendingAccountAndMasksProviderReference() {
        when(authorization.current()).thenReturn(actor("TENANT_PROFILE_UPDATE"));
        when(accounts.lockTenantProvider(71L,"RAZORPAY")).thenReturn(Optional.empty());
        when(accounts.saveAndFlush(any(VendorPaymentAccount.class))).thenAnswer(invocation->{
            VendorPaymentAccount saved=invocation.getArgument(0);
            ReflectionTestUtils.setField(saved,"id",91L);
            return saved;
        });

        var result=service.submit(new VendorPaymentAccountService.Submit(
            "razorpay","acc_1234567890",0,"Vendor merchant account submitted"));

        assertThat(result.id()).isEqualTo(91L);
        assertThat(result.providerKey()).isEqualTo("RAZORPAY");
        assertThat(result.maskedMerchantAccountReference()).isEqualTo("••••7890");
        assertThat(result.status()).isEqualTo("PENDING_VERIFICATION");
        assertThat(result.verifiedAt()).isNull();

        ArgumentCaptor<VendorPaymentAccountEvent> event=ArgumentCaptor.forClass(VendorPaymentAccountEvent.class);
        verify(events).saveAndFlush(event.capture());
        assertThat(event.getValue().action()).isEqualTo("SUBMITTED");
        assertThat(event.getValue().tenantId()).isEqualTo(71L);
        assertThat(event.getValue().reason()).isEqualTo("Vendor merchant account submitted");
    }

    @Test void changingMerchantReferenceInvalidatesPriorProviderVerification() {
        when(authorization.current()).thenReturn(actor("TENANT_PROFILE_UPDATE"));
        VendorPaymentAccount existing=new VendorPaymentAccount(71L,"RAZORPAY","acc_11111111",
            "vendor.admin@example.test",now.minusSeconds(60));
        ReflectionTestUtils.setField(existing,"id",92L);
        existing.providerVerified("provider-verification-1",now.minusSeconds(30));
        when(accounts.lockTenantProvider(71L,"RAZORPAY")).thenReturn(Optional.of(existing));
        when(accounts.saveAndFlush(existing)).thenAnswer(invocation->{
            ReflectionTestUtils.setField(existing,"version",1L);
            return existing;
        });

        var result=service.submit(new VendorPaymentAccountService.Submit(
            "RAZORPAY","acc_22222222",0,"Changed merchant account reference"));

        assertThat(result.status()).isEqualTo("PENDING_VERIFICATION");
        assertThat(result.verifiedAt()).isNull();
        assertThat(result.maskedMerchantAccountReference()).isEqualTo("••••2222");
        verify(events).saveAndFlush(argThat(event->event.action().equals("RESUBMITTED")));
    }

    @Test void staleVersionCannotOverwriteAnotherAdministratorSubmission() {
        when(authorization.current()).thenReturn(actor("TENANT_PROFILE_UPDATE"));
        VendorPaymentAccount existing=new VendorPaymentAccount(71L,"RAZORPAY","acc_11111111",
            "vendor.admin@example.test",now.minusSeconds(60));
        ReflectionTestUtils.setField(existing,"id",93L);
        ReflectionTestUtils.setField(existing,"version",4L);
        when(accounts.lockTenantProvider(71L,"RAZORPAY")).thenReturn(Optional.of(existing));

        assertThatThrownBy(()->service.submit(new VendorPaymentAccountService.Submit(
            "RAZORPAY","acc_22222222",3,"Stale form")))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Merchant-account record changed");
        verify(accounts,never()).saveAndFlush(any());
        verifyNoInteractions(events);
    }

    @Test void suspendedAccountCannotBeReactivatedByVendorSelfService() {
        when(authorization.current()).thenReturn(actor("TENANT_PROFILE_UPDATE"));
        VendorPaymentAccount suspended=new VendorPaymentAccount(71L,"RAZORPAY","acc_11111111",
            "vendor.admin@example.test",now.minusSeconds(60));
        ReflectionTestUtils.setField(suspended,"id",94L);
        ReflectionTestUtils.setField(suspended,"status","SUSPENDED");
        when(accounts.lockTenantProvider(71L,"RAZORPAY")).thenReturn(Optional.of(suspended));

        assertThatThrownBy(()->service.submit(new VendorPaymentAccountService.Submit(
            "RAZORPAY","acc_22222222",0,"Attempted reactivation")))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("cannot be reactivated");
        verify(accounts,never()).saveAndFlush(any());
    }
}
