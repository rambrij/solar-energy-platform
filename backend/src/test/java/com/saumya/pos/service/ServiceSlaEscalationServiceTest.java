package com.saumya.pos.service;

import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.shared.EventOutbox;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ServiceSlaEscalationServiceTest {
    private final JdbcTemplate db=mock(JdbcTemplate.class);
    private final AuthorizationContext authorization=mock(AuthorizationContext.class);
    private final EventOutbox outbox=mock(EventOutbox.class);
    private final Clock clock=Clock.fixed(Instant.parse("2026-11-28T00:00:00Z"),ZoneOffset.UTC);
    private ServiceSlaEscalationService service(){return new ServiceSlaEscalationService(db,authorization,outbox,clock);}
    private void actor(Set<String> grants){
        when(authorization.current()).thenReturn(new AuthorizationContext.Context(
            "manager@vendor.test",7L,2L,"7:manager",grants,false,false,true));
    }

    @Test void serviceAssignCanSavePolicyWithoutNeedingSeparateReadPermission(){
        actor(Set.of("SERVICE_ASSIGN"));
        when(db.queryForList(contains("SELECT id,version"),eq(7L),eq("SERVICE_REQUEST"),
            eq("HIGH"),eq("RESPONSE"),eq(1))).thenReturn(List.of());
        when(db.queryForObject(contains("SELECT id FROM service_sla_escalation_policies"),
            eq(Long.class),eq(7L),eq("SERVICE_REQUEST"),eq("HIGH"),eq("RESPONSE"),eq(1))).thenReturn(31L);
        when(db.update(anyString(),any(Object[].class))).thenReturn(1);

        var saved=service().savePolicy(new ServiceSlaEscalationService.PolicyUpdate(
            "SERVICE_REQUEST","HIGH","RESPONSE",1,30,"SERVICE_MANAGER",true));

        assertThat(saved.id()).isEqualTo(31L);
        assertThat(saved.recipientRoleKey()).isEqualTo("SERVICE_MANAGER");
        verify(outbox).append(eq(7L),eq("manager@vendor.test"),eq("SERVICE_SLA_ESCALATION_POLICY"),
            anyString(),eq("SERVICE_SLA_ESCALATION_POLICY.SAVED"),isNull(),anyMap());
    }

    @Test void updateOnlyCanAcknowledgeEscalationWithoutReadPermission(){
        actor(Set.of("SERVICE_UPDATE"));
        Map<String,Object> open=new HashMap<>();open.put("id",9L);open.put("status","OPEN");
        when(db.queryForList(contains("SELECT id,status"),eq(7L),eq(9L))).thenReturn(List.of(open));
        Map<String,Object> updated=new HashMap<>();
        updated.put("id",9L);updated.put("service_case_sla_id",81L);updated.put("case_type","SERVICE_REQUEST");
        updated.put("case_id",21L);updated.put("breach_type","RESPONSE");updated.put("escalation_level",1);
        updated.put("recipient_role_key","SERVICE_MANAGER");updated.put("status","ACKNOWLEDGED");
        updated.put("escalated_at",Timestamp.from(clock.instant().minusSeconds(60)));
        updated.put("acknowledged_by","manager@vendor.test");updated.put("acknowledged_at",Timestamp.from(clock.instant()));
        updated.put("acknowledgement_note","I have taken ownership");
        when(db.queryForList(contains("SELECT e.id"),eq(7L),eq(9L))).thenReturn(List.of(updated));
        when(db.update(anyString(),any(Object[].class))).thenReturn(1);

        var result=service().acknowledge(9L,new ServiceSlaEscalationService.Acknowledge("I have taken ownership"));

        assertThat(result.status()).isEqualTo("ACKNOWLEDGED");
        assertThat(result.acknowledgedBy()).isEqualTo("manager@vendor.test");
        verify(db).update(contains("INSERT INTO service_sla_escalation_events"),any(Object[].class));
        verify(outbox).append(eq(7L),eq("manager@vendor.test"),eq("SERVICE_SLA_ESCALATION"),
            eq("9"),eq("SERVICE_SLA.ESCALATION_ACKNOWLEDGED"),isNull(),anyMap());
    }

    @Test void unrelatedPermissionCannotCreateEscalationPolicy(){
        actor(Set.of("SHIPMENT_UPDATE"));
        assertThatThrownBy(()->service().savePolicy(new ServiceSlaEscalationService.PolicyUpdate(
            "SERVICE_REQUEST","NORMAL","RESPONSE",1,0,"SERVICE_MANAGER",true,"Approved escalation policy change")))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(db,outbox);
    }

    @Test void warrantyEscalationRejectsUnsupportedPriority(){
        actor(Set.of("SERVICE_ASSIGN"));
        assertThatThrownBy(()->service().savePolicy(new ServiceSlaEscalationService.PolicyUpdate(
            "WARRANTY_CLAIM","URGENT","RESPONSE",1,0,"SERVICE_MANAGER",true,"Approved escalation policy change")))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(db,outbox);
    }
}
