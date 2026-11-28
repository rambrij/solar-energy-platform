package com.saumya.pos.installation;

import com.saumya.pos.checkout.OrderHistoryService;
import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.shared.EventOutbox;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InstallationChecklistServiceTest {
    @Test
    void finalRequiredChecklistItemSubmitsChecklistAndWritesTenantScopedEvidenceAndOutbox() {
        var db=mock(JdbcTemplate.class);
        var authorization=mock(AuthorizationContext.class);
        var orders=mock(OrderHistoryService.class);
        var outbox=mock(EventOutbox.class);
        var now=Instant.parse("2026-11-28T12:00:00Z");
        var clock=Clock.fixed(now,ZoneOffset.UTC);
        var actor=new AuthorizationContext.Context("engineer@vendor.test",7L,2L,"7:engineer",
            Set.of("INSTALLATION_VIEW","INSTALLATION_UPDATE"),false,false,true);
        when(authorization.current()).thenReturn(actor);
        var lockedJob=Map.<String,Object>of("id",81L,"order_id","paid-order-1","status","IN_PROGRESS",
            "assigned_to","engineer@vendor.test","version",2L);
        when(db.queryForList(argThat(sql->sql!=null&&sql.contains("FOR UPDATE")),eq(7L),eq(81L)))
            .thenReturn(List.of(lockedJob));
        when(db.queryForList(contains("item_key,required,completed"),eq(7L),eq(81L),eq("CUSTOMER_HANDOVER_COMPLETED")))
            .thenReturn(List.of(Map.of("item_key","CUSTOMER_HANDOVER_COMPLETED","required",true,"completed",false)));
        when(db.queryForObject(contains("SELECT COUNT(*)"),eq(Integer.class),eq(7L),eq(81L))).thenReturn(1);
        when(db.update(contains("UPDATE installation_checklist_items"),any(Object[].class))).thenReturn(1);
        when(db.update(contains("UPDATE installation_jobs"),any(Object[].class))).thenReturn(1);
        var updatedJob=Map.<String,Object>of("id",81L,"order_id","paid-order-1","site_address","Site",
            "scheduled_at",Timestamp.from(now.plusSeconds(3600)),"status","IN_PROGRESS",
            "assigned_to","engineer@vendor.test","created_at",Timestamp.from(now.minusSeconds(3600)),
            "updated_at",Timestamp.from(now),"version",3L);
        when(db.queryForList(argThat(sql->sql!=null&&sql.contains("FROM installation_jobs")&&!sql.contains("FOR UPDATE")),
            eq(7L),eq(81L))).thenReturn(List.of(updatedJob));
        when(db.queryForList(contains("FROM installation_events"),eq(7L),eq(81L))).thenReturn(List.of());
        when(db.queryForList(contains("ORDER BY sort_order,item_key"),eq(7L),eq(81L))).thenReturn(List.of(Map.of(
            "item_key","CUSTOMER_HANDOVER_COMPLETED","label","Customer handover completed","required",true,
            "completed",true,"completed_by","engineer@vendor.test","completed_at",Timestamp.from(now),
            "evidence_note","Customer documentation and handover confirmed")));
        doReturn(List.of()).when(db).query(anyString(),any(RowMapper.class),any(Object[].class));

        var service=new InstallationService(db,authorization,orders,outbox,clock);
        var result=service.completeChecklistItem(81,"CUSTOMER_HANDOVER_COMPLETED",
            new InstallationService.ChecklistCompletion(2,"Customer documentation and handover confirmed"));

        assertThat(result.job().version()).isEqualTo(3);
        assertThat(result.checklist()).singleElement().satisfies(item->{
            assertThat(item.completed()).isTrue();
            assertThat(item.completedBy()).isEqualTo("engineer@vendor.test");
        });
        verify(db).update(contains("UPDATE installation_checklist_items"),any(Object[].class));
        verify(db).update(contains("INSERT INTO installation_checklist_events"),
            eq(7L),eq(81L),eq("CUSTOMER_HANDOVER_COMPLETED"),eq("engineer@vendor.test"),
            eq("ITEM_COMPLETED"),eq("Customer documentation and handover confirmed"),eq(Timestamp.from(now)));
        verify(db).update(contains("INSERT INTO installation_checklist_events"),
            eq(7L),eq(81L),eq("CUSTOMER_HANDOVER_COMPLETED"),eq("engineer@vendor.test"),
            eq("CHECKLIST_SUBMITTED"),eq("All required checklist items completed"),eq(Timestamp.from(now)));
        verify(outbox).append(eq(7L),eq("engineer@vendor.test"),eq("INSTALLATION"),eq("81"),
            eq("INSTALLATION.CHECKLIST_SUBMITTED"),isNull(),argThat(payload->
                payload.get("installationId").equals(81L)
                    &&payload.get("orderId").equals("paid-order-1")
                    &&payload.get("itemKey").equals("CUSTOMER_HANDOVER_COMPLETED")));
        verifyNoInteractions(orders);
    }
}
