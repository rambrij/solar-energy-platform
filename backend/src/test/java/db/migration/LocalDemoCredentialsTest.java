package db.migration;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class LocalDemoCredentialsTest {
    @Test void provisionsFreshDemoIdentitiesWithExplicitTenantOwnedRoleAssignments() throws Exception {
        var source = new DriverManagerDataSource(
            "jdbc:h2:mem:v51-" + java.util.UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "sa", "");
        var db = new JdbcTemplate(source);
        createSchema(db);

        String seed = new ClassPathResource("db/local-migration/afterMigrateApplied__local_demo.sql").getContentAsString(StandardCharsets.UTF_8);
        String identities = seed.substring(seed.indexOf("CREATE TEMPORARY TABLE demo_local_accounts"),
            seed.indexOf("INSERT INTO subscription_plans")).replace("DROP TEMPORARY TABLE", "DROP TABLE");
        new ResourceDatabasePopulator(new ByteArrayResource(identities.getBytes(StandardCharsets.UTF_8))).execute(source);

        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM users WHERE username LIKE '%@somya.local' AND enabled=TRUE", Integer.class
        )).isEqualTo(33);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM employees WHERE username LIKE '%@somya.local' AND enabled=TRUE", Integer.class
        )).isEqualTo(30);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM customer_profiles", Integer.class
        )).isEqualTo(2);

        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM vendor_roles WHERE role_key LIKE 'standard:%' AND active=TRUE", Integer.class
        )).isEqualTo(15);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM vendor_role_permissions p JOIN vendor_roles r " +
            "ON r.tenant_id=p.tenant_id AND r.id=p.role_id WHERE r.role_key LIKE 'standard:%' " +
            "AND p.permission LIKE 'PLATFORM_%'", Integer.class
        )).isZero();
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM employee_role_assignments a JOIN vendor_roles r " +
            "ON r.tenant_id=a.tenant_id AND r.id=a.role_id WHERE a.active=TRUE " +
            "AND r.role_key LIKE 'standard:%'", Integer.class
        )).isEqualTo(15);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM employee_role_assignments a JOIN employees e ON e.username=a.username " +
            "WHERE e.username LIKE '%@somya.local' AND a.active=TRUE " +
            "AND e.username NOT LIKE '%.admin@somya.local' " +
            "AND NOT EXISTS (SELECT 1 FROM vendor_roles r WHERE r.tenant_id=a.tenant_id AND r.id=a.role_id " +
            "AND r.role_key LIKE 'standard:%')", Integer.class
        )).isZero();

        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM employee_role_assignments a JOIN employees e ON e.username=a.username " +
            "WHERE e.username LIKE '%.sales.executive@somya.local' OR " +
            "e.username LIKE '%.operations.manager@somya.local' OR " +
            "e.username LIKE '%.service.manager@somya.local' OR " +
            "e.username LIKE '%.service.engineer@somya.local' OR " +
            "e.username LIKE '%.content.manager@somya.local'", Integer.class
        )).isZero();

        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM platform_user_permissions WHERE username='platform.superadmin@somya.local'", Integer.class
        )).isEqualTo(11);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM platform_user_permissions WHERE username='platform.superadmin@somya.local' " +
            "AND permission='PLATFORM_TENANT_REACTIVATE'", Integer.class
        )).isOne();
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM employee_register_assignments WHERE active=TRUE", Integer.class
        )).isEqualTo(9);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM vendor_access_audit_events WHERE action='ROLE_TEMPLATE_APPLIED'", Integer.class
        )).isEqualTo(15);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM vendor_access_audit_events WHERE action='ROLE_ASSIGNED'", Integer.class
        )).isEqualTo(15);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM vendor_access_audit_events WHERE action='REGISTER_ASSIGNED'", Integer.class
        )).isEqualTo(9);

        var encoder = new BCryptPasswordEncoder();
        assertThat(encoder.matches("Somya-Platform-2026!",
            db.queryForObject("SELECT password_hash FROM users WHERE username='platform.superadmin@somya.local'", String.class))).isTrue();
        assertThat(encoder.matches("Somya-Saumya-ADM-2026!",
            db.queryForObject("SELECT password_hash FROM users WHERE username='saumya.admin@somya.local'", String.class))).isTrue();
        assertThat(encoder.matches("Somya-Asha-Buyer-2026!",
            db.queryForObject("SELECT password_hash FROM users WHERE username='customer.asha@somya.local'", String.class))).isTrue();
    }

    private void createSchema(JdbcTemplate db) {
        db.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username VARCHAR(100) UNIQUE,password_hash VARCHAR(200),enabled BOOLEAN)");
        db.execute("CREATE TABLE employees(username VARCHAR(100) PRIMARY KEY,tenant_id BIGINT,branch_id BIGINT,enabled BOOLEAN,UNIQUE(tenant_id,username))");
        db.execute("CREATE TABLE employee_role_assignments(tenant_id BIGINT,branch_id BIGINT,username VARCHAR(100),role_id BIGINT,active BOOLEAN,assigned_by VARCHAR(100),revoked_by VARCHAR(100),revoked_at TIMESTAMP,PRIMARY KEY(tenant_id,username,role_id))");
        db.execute("CREATE TABLE employee_register_assignments(tenant_id BIGINT,branch_id BIGINT,username VARCHAR(100),register_id BIGINT,active BOOLEAN,assigned_by VARCHAR(100),revoked_by VARCHAR(100),revoked_at TIMESTAMP,PRIMARY KEY(tenant_id,username,register_id))");
        db.execute("CREATE TABLE vendor_roles(tenant_id BIGINT,id BIGINT AUTO_INCREMENT,role_key VARCHAR(120),display_name VARCHAR(120),active BOOLEAN,version BIGINT DEFAULT 0,created_by VARCHAR(100),updated_by VARCHAR(100),template_key VARCHAR(80),template_version INT,PRIMARY KEY(tenant_id,id),UNIQUE(tenant_id,role_key))");
        db.execute("CREATE TABLE vendor_role_permissions(tenant_id BIGINT,role_id BIGINT,permission VARCHAR(80),created_by VARCHAR(100),UNIQUE(tenant_id,role_id,permission))");
        db.execute("CREATE TABLE vendor_access_audit_events(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT,actor_username VARCHAR(100),subject_username VARCHAR(100),resource_type VARCHAR(40),resource_id VARCHAR(120),action VARCHAR(60),reason VARCHAR(500))");
        db.execute("CREATE TABLE platform_user_permissions(username VARCHAR(100),permission VARCHAR(80),PRIMARY KEY(username,permission))");
        db.execute("CREATE TABLE customer_profiles(id BIGINT PRIMARY KEY,user_id BIGINT UNIQUE)");
        db.execute("CREATE TABLE registers(id BIGINT PRIMARY KEY,tenant_id BIGINT,branch_id BIGINT)");
        db.execute("CREATE TABLE tenant_customers(id VARCHAR(64) PRIMARY KEY,tenant_id BIGINT,customer_id BIGINT,consent_at TIMESTAMP,provenance VARCHAR(80),UNIQUE(tenant_id,customer_id))");
        db.update("INSERT INTO registers(id,tenant_id,branch_id) VALUES (910011,910001,910001),(910012,910001,910001),(910013,910001,910001),(920011,920001,920001),(920012,920001,920001),(920013,920001,920001),(930011,930001,930001),(930012,930001,930001),(930013,930001,930001)");
    }
}
