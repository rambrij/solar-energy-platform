package com.saumya.pos.subscription;

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
import static org.assertj.core.api.Assertions.assertThat;

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
}
