package com.saumya.pos.service;

import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.shared.EventOutbox;
import com.saumya.pos.tenant.TenantLifecycleService;
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

class ServiceSlaServiceAuthorizationBoundaryTest {
    private final JdbcTemplate db=mock(JdbcTemplate.class);
    private final AuthorizationContext authorization=mock(AuthorizationContext.class);
    private final EventOutbox outbox=mock(EventOutbox.class);
    private final Clock clock=Clock.fixed(Instant.parse("2026-11-28T00:00:00Z"),ZoneOffset.UTC);
    private ServiceSlaService service(){return new ServiceSlaService(db,authorization,outbox,clock);}
    private void vendor(Set<String> grants){
        when(authorization.current()).thenReturn(new AuthorizationContext.Context(
            "manager@vendor.test",7L,2L,"7:manager",grants,false,false,true));
    }

    @Test void unrelatedVendorPermissionsCannotReadOrChangeServiceSla(){
        vendor(Set.of("SHIPMENT_VIEW","SHIPMENT_UPDATE"));
        assertThatThrownBy(service()::policies).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(service()::dashboard).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service().updatePolicy(new ServiceSlaService.PolicyUpdate(
            "SERVICE_REQUEST","NORMAL",60,240,"Approved service target"))).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(db,outbox);
    }

    @Test void serviceViewListsOnlyAuthenticatedTenantPoliciesAndAssignedCases(){
        vendor(Set.of("SERVICE_VIEW"));
        when(db.queryForList(contains("FROM service_sla_policies WHERE tenant_id=?"),eq(7L))).thenReturn(List.of());
        when(db.queryForList(contains("FROM service_requests r"),eq(7L),eq("manager@vendor.test"))).thenReturn(List.of());
        when(db.queryForList(contains("FROM warranty_claims c"),eq(7L),eq("manager@vendor.test"))).thenReturn(List.of());
        assertThat(service().policies()).hasSize(5);
        assertThat(service().dashboard()).isEmpty();
        verify(db).queryForList(contains("WHERE r.tenant_id=? AND r.assigned_to=?"),eq(7L),eq("manager@vendor.test"));
        verify(db).queryForList(contains("WHERE c.tenant_id=? AND c.assigned_to=?"),eq(7L),eq("manager@vendor.test"));
    }

    @Test void invalidPolicyTargetsAreRejectedBeforePersistence(){
        vendor(Set.of("SERVICE_ASSIGN"));
        assertThatThrownBy(()->service().updatePolicy(new ServiceSlaService.PolicyUpdate(
            "SERVICE_REQUEST","HIGH",240,60,"Invalid order")))
            .isInstanceOfSatisfying(ResponseStatusException.class,e->
                assertThat(e.getReason()).contains("greater than or equal"));
        assertThatThrownBy(()->service().updatePolicy(new ServiceSlaService.PolicyUpdate(
            "WARRANTY_CLAIM","URGENT",60,240,"Invalid warranty priority")))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service().updatePolicy(new ServiceSlaService.PolicyUpdate(
            "SERVICE_REQUEST","NORMAL",60,240,"")))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(db,outbox);
    }

    @Test void caseWithoutConfiguredPolicyDoesNotInventSlaTargets(){
        when(db.queryForList(contains("FROM service_sla_policies"),eq(7L),eq("HIGH"))).thenReturn(List.of());
        service().startForServiceRequest(7,21,"HIGH","CUSTOMER","buyer@example.test",clock.instant());
        verify(db,never()).update(anyString(),any(Object[].class));
        verifyNoInteractions(outbox,authorization);
    }

    @Test void scheduledBreachIsTenantScopedAndEmitsOneOutboxEventPerBreach(){
        var tenant=mock(TenantLifecycleService.TenantSummary.class);
        when(tenant.id()).thenReturn(7L);
        Map<String,Object> row=new HashMap<>();
        row.put("id",81L);row.put("case_type","SERVICE_REQUEST");row.put("service_request_id",21L);
        row.put("warranty_claim_id",null);row.put("priority","HIGH");
        row.put("first_response_at",null);row.put("closed_at",null);
        row.put("response_due_at",Timestamp.from(clock.instant().minusSeconds(60)));
        row.put("resolution_due_at",Timestamp.from(clock.instant().minusSeconds(30)));
        row.put("response_breached_at",null);row.put("resolution_breached_at",null);
        when(db.queryForList(contains("FROM service_case_sla"),eq(7L),any(Timestamp.class),any(Timestamp.class)))
            .thenReturn(List.of(row));
        when(db.update(anyString(),any(Object[].class))).thenReturn(1);
        service().processDueForTenant(tenant,clock.instant());
        verify(db,times(2)).update(contains("UPDATE service_case_sla"),any(Object[].class));
        verify(db,times(2)).update(contains("INSERT INTO service_sla_events"),any(Object[].class));
        verify(outbox).append(eq(7L),eq("service-sla-worker"),eq("SERVICE_CASE_SLA"),eq("81"),
            eq("SERVICE_SLA.RESPONSE_BREACHED"),isNull(),anyMap());
        verify(outbox).append(eq(7L),eq("service-sla-worker"),eq("SERVICE_CASE_SLA"),eq("81"),
            eq("SERVICE_SLA.RESOLUTION_BREACHED"),isNull(),anyMap());
    }
    @Test void validPolicyIsTenantOwnedAuditedAndBackfillsOpenUntrackedCases(){
        vendor(Set.of("SERVICE_ASSIGN"));
        when(db.queryForList(contains("SELECT response_target_minutes,resolution_target_minutes,version"),
            eq(7L),eq("SERVICE_REQUEST"),eq("HIGH"))).thenReturn(List.of());
        when(db.queryForList(contains("SELECT r.id,r.created_at"),eq(7L),eq("HIGH"))).thenReturn(List.of());
        Map<String,Object> policy=new HashMap<>();
        policy.put("case_type","SERVICE_REQUEST");policy.put("priority","HIGH");
        policy.put("response_target_minutes",60);policy.put("resolution_target_minutes",240);
        policy.put("version",0L);policy.put("updated_at",Timestamp.from(clock.instant()));
        policy.put("updated_by","manager@vendor.test");
        when(db.queryForList(contains("FROM service_sla_policies WHERE tenant_id=?"),eq(7L)))
            .thenReturn(List.of(policy));
        when(db.update(anyString(),any(Object[].class))).thenReturn(1);

        var result=service().updatePolicy(new ServiceSlaService.PolicyUpdate(
            "SERVICE_REQUEST","HIGH",60,240,"Approved support response target"));

        assertThat(result.configured()).isTrue();
        assertThat(result.responseTargetMinutes()).isEqualTo(60);
        assertThat(result.resolutionTargetMinutes()).isEqualTo(240);
        verify(db).update(contains("INSERT INTO service_sla_policy_audit"),any(Object[].class));
        verify(outbox).append(eq(7L),eq("manager@vendor.test"),eq("SERVICE_SLA_POLICY"),
            eq("SERVICE_REQUEST:HIGH"),eq("SERVICE_SLA_POLICY.CREATED"),isNull(),anyMap());
    }

    @Test void configuredPolicyStartsAnImmutableSnapshotForANewServiceRequest(){
        Map<String,Object> policy=new HashMap<>();
        policy.put("response_target_minutes",60);policy.put("resolution_target_minutes",240);
        when(db.queryForList(contains("FROM service_sla_policies"),eq(7L),eq("HIGH")))
            .thenReturn(List.of(policy));
        when(db.queryForObject(contains("SELECT id FROM service_case_sla"),eq(Long.class),
            eq(7L),eq("SERVICE_REQUEST"),eq(21L))).thenReturn(91L);
        when(db.update(anyString(),any(Object[].class))).thenReturn(1);

        service().startForServiceRequest(7,21,"HIGH","CUSTOMER","buyer@example.test",clock.instant());

        verify(db).update(contains("INSERT INTO service_case_sla"),any(Object[].class));
        verify(db).update(contains("INSERT INTO service_sla_events"),any(Object[].class));
    }

}
