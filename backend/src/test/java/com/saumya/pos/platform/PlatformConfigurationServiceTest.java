package com.saumya.pos.platform;

import com.saumya.pos.platform.internal.*;
import com.saumya.pos.security.AuthorizationContext;
import java.time.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformConfigurationServiceTest {
    @Mock PlatformConfigurationRepository repository;@Mock PlatformAuditService audit;@Mock AuthorizationContext authorization;
    private PlatformConfigurationServiceImpl service(){
        when(authorization.current()).thenReturn(new AuthorizationContext.Context("admin",null,null,
            Set.of("PLATFORM_CONFIG_MANAGE"),true,false,false));
        return new PlatformConfigurationServiceImpl(repository,audit,Clock.fixed(Instant.parse("2026-11-16T00:00:00Z"),ZoneOffset.UTC),authorization);
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
        when(authorization.current()).thenReturn(new AuthorizationContext.Context("vendor",1L,1L,Set.of("VENDOR_AUDIT_VIEW"),false,false,true));
        var service=new PlatformConfigurationServiceImpl(repository,audit,Clock.systemUTC(),authorization);
        assertThatThrownBy(service::list).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository);
    }
}
