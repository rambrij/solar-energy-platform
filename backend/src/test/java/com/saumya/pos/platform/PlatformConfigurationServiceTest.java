package com.saumya.pos.platform;

import com.saumya.pos.platform.internal.*;
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
            java.util.List.of(new SimpleGrantedAuthority("PLATFORM_CONFIG_MANAGE"))));
        return new PlatformConfigurationServiceImpl(repository,audit,Clock.fixed(Instant.parse("2026-11-16T00:00:00Z"),ZoneOffset.UTC));
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
            java.util.List.of(new SimpleGrantedAuthority("VENDOR_AUDIT_VIEW"))));
        var service=new PlatformConfigurationServiceImpl(repository,audit,Clock.systemUTC());
        assertThatThrownBy(service::list).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository);
    }

    @Test void configurationWritesRequirePlatformGrantBeforeRepositoryOrAuditAccess(){
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("vendor","n/a",
            java.util.List.of(new SimpleGrantedAuthority("VENDOR_AUDIT_VIEW"))));
        var service=new PlatformConfigurationServiceImpl(repository,audit,Clock.systemUTC());
        var update=new PlatformConfigurationService.Update("PUBLIC_SITE_NAME","GENERAL","Vendor Name",false,null);

        assertThatThrownBy(()->service.put(update,"vendor","trace"))
            .isInstanceOf(ResponseStatusException.class);

        verifyNoInteractions(repository,audit);
    }

}
