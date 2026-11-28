package com.saumya.pos.installation;

import com.saumya.pos.checkout.OrderHistoryService;
import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.shared.EventOutbox;
import java.math.BigDecimal;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InstallationServiceEventOutboxTest {
    @Test
    void jobCreationAppendsTenantScopedOutboxEventWithoutCustomerSiteOrContactData() {
        var db=mock(JdbcTemplate.class);
        var auth=mock(AuthorizationContext.class);
        var orders=mock(OrderHistoryService.class);
        var outbox=mock(EventOutbox.class);
        var now=Instant.parse("2026-11-27T12:00:00Z");
        var clock=Clock.fixed(now,ZoneOffset.UTC);
        when(auth.current()).thenReturn(new AuthorizationContext.Context(
            "manager@vendor.test",7L,2L,"7:manager",
            Set.of("INSTALLATION_ASSIGN","INSTALLATION_VIEW"),false,false,true));
        when(db.queryForList(contains("SELECT customer_id,sales_channel,status"),
            eq(7L),eq("paid-order-1"))).thenReturn(List.of(Map.of(
                "customer_id",501L,"sales_channel","ONLINE","status","PAID")));
        when(db.queryForObject(contains("SELECT COUNT(*)>0"),eq(Boolean.class),
            eq(7L),eq("engineer@vendor.test"))).thenReturn(true);
        when(db.queryForObject(contains("SELECT id FROM installation_jobs"),eq(Long.class),
            eq(7L),eq("paid-order-1"))).thenReturn(81L);
        Map<String,Object> row=new HashMap<>();
        row.put("id",81L);
        row.put("order_id","paid-order-1");
        row.put("site_address","Solar installation site, Mumbai");
        row.put("scheduled_at",Timestamp.from(now.plusSeconds(3600)));
        row.put("status","ASSIGNED");
        row.put("assigned_to","engineer@vendor.test");
        row.put("created_at",Timestamp.from(now));
        row.put("updated_at",Timestamp.from(now));
        row.put("completed_at",null);
        row.put("version",0L);
        when(db.queryForList(contains("FROM installation_jobs WHERE tenant_id=? AND id=?"),
            eq(7L),eq(81L))).thenReturn(List.of(row));

        var service=new InstallationService(db,auth,orders,outbox,clock);
        var created=service.create(new InstallationService.Create(
            "paid-order-1","Solar installation site, Mumbai",now.plusSeconds(3600),
            "engineer@vendor.test","Customer confirmed the installation site"
        ));

        assertThat(created.id()).isEqualTo(81L);
        assertThat(created.status()).isEqualTo("ASSIGNED");
        verify(outbox).append(eq(7L),eq("manager@vendor.test"),eq("INSTALLATION"),eq("81"),
            eq("INSTALLATION.JOB_CREATED"),isNull(),argThat(payload ->
                payload.keySet().equals(Set.of("installationId","orderId","status","occurredAt"))
                    &&payload.get("installationId").equals(81L)
                    &&payload.get("orderId").equals("paid-order-1")
                    &&payload.get("status").equals("ASSIGNED")
                    &&payload.get("occurredAt").equals(now.toString())));
        verify(db,times(5)).update(anyString(),any(Object[].class));
        verify(db).update(contains("UPDATE shipment_serial_assets SET installation_id=?"),
            eq(81L),eq(7L),eq("paid-order-1"));
        verify(db).update(contains("INSTALLATION_LINKED"),eq("manager@vendor.test"),
            eq("Linked to installation 81"),eq(7L),eq(81L));
        verifyNoInteractions(orders);
    }
}
