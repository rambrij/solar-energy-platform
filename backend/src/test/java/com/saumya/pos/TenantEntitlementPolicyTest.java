package com.saumya.pos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saumya.pos.membership.TenantEntitlementPolicy;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TenantEntitlementPolicyTest {
    private static final Clock CLOCK=Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"),ZoneOffset.UTC);
    private TenantEntitlementPolicy policy(JdbcTemplate db){
        return new TenantEntitlementPolicy(db,new ObjectMapper(),CLOCK,new MockEnvironment().withProperty("spring.profiles.active","production"));
    }
    private Map<String,Object> subscription(String state,LocalDate end,Instant grace,String entitlements){
        Map<String,Object> row=new HashMap<>();row.put("status",state);row.put("billing_period_end",Date.valueOf(end));
        row.put("grace_until",grace==null?null:Timestamp.from(grace));row.put("entitlements",entitlements);return row;
    }
    private void rows(JdbcTemplate db,Map<String,Object> row){
        when(db.queryForList(anyString(),eq(10L))).thenReturn(List.of(row));
    }
    @Test void noSubscriptionIsDeniedOutsideLocalAndTestProfiles(){
        JdbcTemplate db=mock(JdbcTemplate.class);when(db.queryForList(anyString(),eq(10L))).thenReturn(List.of());
        assertThat(policy(db).permissions(10,List.of("POS_VIEW","POS_SALE_CREATE"))).isEmpty();
    }
    @Test void activeSubscriptionRequiresExplicitFeatureEntitlement(){
        JdbcTemplate db=mock(JdbcTemplate.class);
        rows(db,subscription("ACTIVE",LocalDate.parse("2026-12-01"),null,"{\"features\":{\"POS\":true,\"CATALOG\":false}}"));
        assertThat(policy(db).permissions(10,List.of("POS_VIEW","POS_SALE_CREATE","PRODUCT_VIEW")))
            .containsExactly("POS_VIEW","POS_SALE_CREATE");
    }
    @Test void activeWithMissingFeatureIsDeniedByDefault(){
        JdbcTemplate db=mock(JdbcTemplate.class);
        rows(db,subscription("ACTIVE",LocalDate.parse("2026-12-01"),null,"{}"));
        assertThat(policy(db).permissions(10,List.of("POS_VIEW","POS_SALE_CREATE"))).isEmpty();
    }
    @Test void gracePeriodAllowsWritesOnlyUntilTheConfiguredGraceInstant(){
        JdbcTemplate db=mock(JdbcTemplate.class);
        rows(db,subscription("GRACE_PERIOD",LocalDate.parse("2026-11-01"),CLOCK.instant().plusSeconds(60),"{\"features\":{\"POS\":true,\"ADMINISTRATION\":true}}"));
        assertThat(policy(db).permissions(10,List.of("POS_VIEW","POS_SALE_CREATE"))).containsExactly("POS_VIEW","POS_SALE_CREATE");
        rows(db,subscription("PAST_DUE",LocalDate.parse("2026-11-01"),CLOCK.instant().minusSeconds(1),"{\"features\":{\"POS\":true}}"));
        assertThat(policy(db).permissions(10,List.of("POS_VIEW","POS_SALE_CREATE"))).containsExactly("POS_VIEW");
    }
    @Test void suspendedSubscriptionRetainsOnlyApprovedReadPermissions(){
        JdbcTemplate db=mock(JdbcTemplate.class);
        rows(db,subscription("SUSPENDED",LocalDate.parse("2026-11-01"),null,"{\"features\":{\"POS\":true}}"));
        assertThat(policy(db).permissions(10,List.of("POS_VIEW","POS_SALE_CREATE","ROLE_VIEW","ROLE_ASSIGN")))
            .containsExactly("POS_VIEW","ROLE_VIEW");
    }
    @Test void expiredPeriodBlocksWritesEvenWhenStatusSaysActive(){
        JdbcTemplate db=mock(JdbcTemplate.class);
        rows(db,subscription("ACTIVE",LocalDate.parse("2026-11-15"),null,"{\"features\":{\"POS\":true}}"));
        assertThat(policy(db).permissions(10,List.of("POS_VIEW","POS_SALE_CREATE"))).containsExactly("POS_VIEW");
    }
}
