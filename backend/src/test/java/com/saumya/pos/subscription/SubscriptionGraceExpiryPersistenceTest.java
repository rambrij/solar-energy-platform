package com.saumya.pos.subscription;

import com.saumya.pos.subscription.internal.*;
import com.saumya.pos.tenant.TenantLifecycleService;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {
    "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:subscription-grace;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${TEST_DB_USER:sa}",
    "spring.datasource.password=${TEST_DB_PASSWORD:}"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SubscriptionGraceExpiryJob.class, SubscriptionGraceExpiryPersistenceTest.FixedClock.class})
class SubscriptionGraceExpiryPersistenceTest {
    @TestConfiguration
    static class FixedClock {
        @Bean Clock graceExpiryClock() {
            return Clock.fixed(Instant.parse("2026-11-24T00:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired JdbcTemplate db;
    @Autowired EntityManager entities;
    @Autowired SubscriptionGraceExpiryJob job;
    @Autowired TenantSubscriptionRepository subscriptions;
    @Autowired SubscriptionInvoiceRepository invoices;
    @Autowired SubscriptionPaymentRepository payments;
    @Autowired SubscriptionPlanVersionRepository versions;
    @Autowired SubscriptionEventRepository events;

    @Test void changesOnlyDueSubscriptionsAndPersistsOneForeignKeyBackedAuditEvent() {
        db.update("INSERT INTO tenants(id,name,lifecycle_status) VALUES(987601,'Due Tenant','ACTIVE'),(987602,'Future Tenant','ACTIVE')");
        db.update("""
            INSERT INTO subscription_plans(id,plan_key,display_name,active,current_version)
            VALUES(987603,'grace-expiry-test','Grace expiry test',TRUE,1)
            """);
        db.update("""
            INSERT INTO subscription_plan_versions
            (plan_id,version,price,currency,billing_interval,max_users,max_storage_mb,max_branches,
             max_warehouses,entitlements,grace_days,tax_metadata,invoice_description,created_by)
            VALUES(987603,1,100,'INR','MONTHLY',5,1000,2,0,'{}',7,'{}','Test','system:subscription-grace-expiry')
            """);
        db.update("""
            INSERT INTO tenant_subscriptions
            (id,tenant_id,plan_id,plan_version,status,billing_period_start,billing_period_end,grace_until,terms_snapshot)
            VALUES
              (987604,987601,987603,1,'GRACE_PERIOD','2026-10-01','2026-11-01','2020-01-01 00:00:00','{}'),
              (987605,987602,987603,1,'GRACE_PERIOD','2026-10-01','2026-11-01','2030-01-01 00:00:00','{}')
            """);

        job.expireGrace();
        entities.flush();
        job.expireGrace();
        entities.flush();

        assertThat(db.queryForObject("SELECT status FROM tenant_subscriptions WHERE id=987604", String.class))
            .isEqualTo("SUSPENDED");
        assertThat(db.queryForObject("SELECT status FROM tenant_subscriptions WHERE id=987605", String.class))
            .isEqualTo("GRACE_PERIOD");
        assertThat(db.queryForObject("SELECT lifecycle_status FROM tenants WHERE id=987601", String.class))
            .isEqualTo("ACTIVE");
        assertThat(db.queryForList("""
            SELECT e.actor_username FROM subscription_events e
            JOIN users u ON u.username=e.actor_username
            WHERE e.tenant_id=987601 AND e.subscription_id=987604
              AND e.old_status='GRACE_PERIOD' AND e.new_status='SUSPENDED' AND u.enabled=FALSE
            """, String.class)).containsExactly("system:subscription-grace-expiry");
    }

    @Test void mockBillingIssuesOneInvoiceRetriesAfterBackoffAndReactivatesOnlyAnActiveTenant(){
        var due=Instant.parse("2026-01-31T08:00:00Z");
        db.update("INSERT INTO tenants(id,name,lifecycle_status) VALUES(987610,'Renewal tenant','ACTIVE')");
        db.update("INSERT INTO subscription_plans(id,plan_key,display_name,active,current_version) VALUES(987611,'mock-renewal-test','Mock renewal test',TRUE,1)");
        db.update("""
            INSERT INTO subscription_plan_versions
            (plan_id,version,price,currency,billing_interval,max_users,max_storage_mb,max_branches,
             max_warehouses,entitlements,grace_days,tax_metadata,invoice_description,created_by)
            VALUES(987611,1,125,'INR','MONTHLY',5,1000,2,0,'{}',0,'{}','Test','system:subscription-mock-billing')
            """);
        db.update("""
            INSERT INTO tenant_subscriptions
            (id,tenant_id,plan_id,plan_version,status,billing_period_start,billing_period_end,billing_anchor,terms_snapshot)
            VALUES(987612,987610,987611,1,'ACTIVE','2025-12-31','2026-01-31','2025-12-31','{}')
            """);
        var tenants=mock(TenantLifecycleService.class);
        when(tenants.lockActive(987610)).thenReturn(true);
        var tenant=mock(TenantLifecycleService.TenantSummary.class);
        when(tenant.contactEmail()).thenReturn("billing@somya.local");
        when(tenants.get(987610)).thenReturn(tenant);
        var mail=mock(MockSubscriptionBillingReminder.class);

        billing("FAIL",due,tenants,mail).runDueBilling();
        billing("FAIL",due.plusSeconds(59),tenants,mail).runDueBilling();
        entities.flush();
        assertThat(status()).isEqualTo("GRACE_PERIOD");
        assertThat(attempts()).containsExactly("FAILED");
        verify(mail).afterCommit("billing@somya.local","MOCK-SUB-987612-2026-01-31",due);

        new SubscriptionGraceExpiryJob(subscriptions,events,Clock.fixed(due,ZoneOffset.UTC)).expireGrace();
        entities.flush();
        assertThat(status()).isEqualTo("SUSPENDED");
        db.update("UPDATE tenants SET lifecycle_status='SUSPENDED' WHERE id=987610");
        billing("SUCCESS",due.plusSeconds(60),tenants,mail).runDueBilling();
        assertThat(attempts()).containsExactly("FAILED");
        assertThat(status()).isEqualTo("SUSPENDED");
        db.update("UPDATE tenants SET lifecycle_status='ACTIVE' WHERE id=987610");

        billing("SUCCESS",due.plusSeconds(60),tenants,mail).runDueBilling();
        billing("SUCCESS",due.plusSeconds(61),tenants,mail).runDueBilling();
        entities.flush();
        assertThat(status()).isEqualTo("ACTIVE");
        assertThat(attempts()).containsExactly("FAILED","SUCCEEDED");
        assertThat(db.queryForList("SELECT status FROM subscription_invoices WHERE subscription_id=987612",String.class))
            .containsExactly("PAID");
        assertThat(db.queryForObject("SELECT billing_period_end FROM tenant_subscriptions WHERE id=987612",java.time.LocalDate.class))
            .isEqualTo(java.time.LocalDate.parse("2026-02-28"));
    }

    private MockSubscriptionBillingJob billing(String outcome,Instant now,TenantLifecycleService tenants,MockSubscriptionBillingReminder mail){
        var environment=new MockEnvironment();
        environment.setActiveProfiles("test");
        return new MockSubscriptionBillingJob(subscriptions,invoices,payments,versions,events,tenants,mail,
            Clock.fixed(now,ZoneOffset.UTC),environment,true,outcome,3,60);
    }

    private String status(){
        return db.queryForObject("SELECT status FROM tenant_subscriptions WHERE id=987612",String.class);
    }

    private java.util.List<String> attempts(){
        return db.queryForList("""
            SELECT p.status FROM subscription_payments p JOIN subscription_invoices i ON i.id=p.subscription_invoice_id
            WHERE i.subscription_id=987612 ORDER BY p.id
            """,String.class);
    }
}
