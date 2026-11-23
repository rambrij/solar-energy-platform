package com.saumya.pos.platform;

import com.saumya.pos.platform.internal.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformConfigurationServiceTest {
    @Mock PlatformConfigurationRepository repository;@Mock PlatformAuditService audit;
    @AfterEach void clearSecurityContext(){SecurityContextHolder.clearContext();}
    private PlatformConfigurationServiceImpl service(){
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin","n/a",
            java.util.List.of(new SimpleGrantedAuthority("PLATFORM_CONFIG_MANAGE"),new SimpleGrantedAuthority(com.saumya.pos.security.AuthorizationContext.ACTOR_CONTEXT_PLATFORM))));
        return new PlatformConfigurationServiceImpl(repository,audit,Clock.fixed(Instant.parse("2026-11-16T00:00:00Z"),ZoneOffset.UTC),new ObjectMapper());
    }
    @Test void rejectsRawCredentialsAndNeverReturnsSecretReferences(){
        var service=service();
        assertThatThrownBy(()->service.put(new PlatformConfigurationService.Update("PAYMENT_API_KEY","PAYMENT","plain-secret",false,null),"admin",null))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.put(new PlatformConfigurationService.Update("PAYMENT_API_KEY","PAYMENT","plain-secret",true,"vault://payment/key"),"admin",null))
            .isInstanceOf(ResponseStatusException.class);
        when(repository.findById("PAYMENT_API_KEY")).thenReturn(java.util.Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(inv->inv.getArgument(0));
        var saved=service.put(new PlatformConfigurationService.Update("PAYMENT_API_KEY","PAYMENT",null,true,"vault://payment/key"),"admin","corr");
        assertThat(saved.value()).isNull();assertThat(saved.sensitive()).isTrue();assertThat(saved.secretConfigured()).isTrue();
        assertThat(saved.toString()).doesNotContain("vault://payment/key");
    }
    @Test void configurationReadsRequirePlatformConfigurationPermission(){
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("vendor","n/a",
            java.util.List.of(new SimpleGrantedAuthority("VENDOR_AUDIT_VIEW"),new SimpleGrantedAuthority(com.saumya.pos.security.AuthorizationContext.ACTOR_CONTEXT_VENDOR))));
        var service=new PlatformConfigurationServiceImpl(repository,audit,Clock.systemUTC(),new ObjectMapper());
        assertThatThrownBy(service::list).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository);
    }
    @Test void validatesAndAuditsEmailTemplatesWithoutExposingTheirContentsInAudit(){
        var service=service();
        String key="EMAIL_TEMPLATE.RESET_PASSWORD";
        assertThatThrownBy(()->service.put(new PlatformConfigurationService.Update(key,"EMAIL_TEMPLATE",
            "{\"subject\":\"Title\\nInjected\",\"body\":\"Body\"}",false,null),"admin",null))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.put(new PlatformConfigurationService.Update(key,"EMAIL_TEMPLATE",
            "{\"subject\":\"Title\"}",false,null),"admin",null))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository,audit);
        when(repository.saveAndFlush(any())).thenAnswer(inv->inv.getArgument(0));
        String template="{\"subject\":\"Reset your password\",\"body\":\"Contact support for help.\"}";
        var saved=service.put(new PlatformConfigurationService.Update(key,"EMAIL_TEMPLATE",template,false,null),"admin","corr");
        assertThat(saved.value()).isEqualTo(template);
        verify(audit).record("admin","PLATFORM_CONFIGURATION_UPDATED","CONFIGURATION",key,null,"Configuration updated","corr");
    }
    @Test void paymentProviderSettingsRejectGlobalVendorCredentialsAndSeparateSubscriptionReferences(){
        var service=service();
        assertThatThrownBy(()->service.put(new PlatformConfigurationService.Update("PAYMENT_PROVIDER.VENDOR.CREDENTIAL_REFERENCE",
            "PAYMENT_PROVIDER",null,true,"vault://vendor/key"),"admin",null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.put(new PlatformConfigurationService.Update("PAYMENT_PROVIDER.SUBSCRIPTION.NAME",
            "PAYMENT_PROVIDER","https://example.test",false,null),"admin",null)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository,audit);
        when(repository.saveAndFlush(any())).thenAnswer(inv->inv.getArgument(0));
        var saved=service.put(new PlatformConfigurationService.Update("PAYMENT_PROVIDER.SUBSCRIPTION.CREDENTIAL_REFERENCE",
            "PAYMENT_PROVIDER",null,true,"vault://subscription/key"),"admin",null);
        assertThat(saved.secretConfigured()).isTrue();
        assertThat(saved.value()).isNull();
    }

    @Test void configurationWritesRequirePlatformGrantBeforeRepositoryOrAuditAccess(){
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("vendor","n/a",
            java.util.List.of(new SimpleGrantedAuthority("VENDOR_AUDIT_VIEW"),new SimpleGrantedAuthority(com.saumya.pos.security.AuthorizationContext.ACTOR_CONTEXT_VENDOR))));
        var service=new PlatformConfigurationServiceImpl(repository,audit,Clock.systemUTC(),new ObjectMapper());
        var update=new PlatformConfigurationService.Update("PUBLIC_SITE_NAME","GENERAL","Vendor Name",false,null);

        assertThatThrownBy(()->service.put(update,"vendor","trace"))
            .isInstanceOf(ResponseStatusException.class);

        verifyNoInteractions(repository,audit);
    }

}
