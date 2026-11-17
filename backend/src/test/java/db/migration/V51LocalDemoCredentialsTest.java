package db.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class V51LocalDemoCredentialsTest {
    @Test void refreshesLegacyDemoUsersIntoSrsAlignedSomyaLocalCredentials() {
        var source = new DriverManagerDataSource(
            "jdbc:h2:mem:v51-" + java.util.UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "sa", "");
        var db = new JdbcTemplate(source);
        createSchema(db);
        seedLegacyRows(db);

        new ResourceDatabasePopulator(
            new ClassPathResource("db/local-migration/V51__refresh_local_demo_credentials.sql")
        ).execute(source);

        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM users WHERE username LIKE '%@somya.local' AND enabled=TRUE", Integer.class
        )).isEqualTo(33);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM employees WHERE username LIKE '%@somya.local' AND enabled=TRUE", Integer.class
        )).isEqualTo(30);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM vendor_roles WHERE role_key LIKE 'local-v51-%' AND active=TRUE", Integer.class
        )).isEqualTo(30);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM employee_role_assignments a JOIN employees e ON e.username=a.username " +
            "WHERE e.username LIKE '%@somya.local' AND a.active=TRUE", Integer.class
        )).isEqualTo(30);

        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM vendor_role_permissions p JOIN vendor_roles r " +
            "ON r.tenant_id=p.tenant_id AND r.id=p.role_id " +
            "WHERE r.role_key LIKE 'local-v51-%' AND p.permission LIKE 'PLATFORM_%'", Integer.class
        )).isZero();
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM platform_user_permissions WHERE username='platform.superadmin@somya.local'", Integer.class
        )).isEqualTo(10);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM employees WHERE username='admin@saumya.demo.test' AND enabled=FALSE", Integer.class
        )).isEqualTo(1);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM employee_role_assignments WHERE username='admin@saumya.demo.test' AND active=FALSE", Integer.class
        )).isEqualTo(1);

        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM vendor_role_permissions p JOIN vendor_roles r " +
            "ON r.tenant_id=p.tenant_id AND r.id=p.role_id " +
            "WHERE r.role_key='local-v51-911105' AND p.permission IN ('POS_TENDER_MANUAL_VERIFY','POS_REFUND_APPROVE','POS_TENDER_EXTERNAL_CONFIRM')",
            Integer.class
        )).isZero();
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM vendor_role_permissions p JOIN vendor_roles r " +
            "ON r.tenant_id=p.tenant_id AND r.id=p.role_id " +
            "WHERE r.role_key='local-v51-911106' AND p.permission IN ('POS_TENDER_MANUAL_VERIFY','POS_REFUND_APPROVE')",
            Integer.class
        )).isEqualTo(2);

        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM employee_register_assignments WHERE username LIKE '%@somya.local' AND active=TRUE",
            Integer.class
        )).isEqualTo(9);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM customer_profiles", Integer.class)).isEqualTo(2);
        assertThat(db.queryForObject(
            "SELECT COUNT(*) FROM tenant_customers WHERE id LIKE 'local-v51-%'", Integer.class
        )).isEqualTo(4);

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
        db.execute("CREATE TABLE employees(username VARCHAR(100) PRIMARY KEY,tenant_id BIGINT,branch_id BIGINT,enabled BOOLEAN)");
        db.execute("CREATE TABLE employee_role_assignments(tenant_id BIGINT,branch_id BIGINT,username VARCHAR(100),role_id BIGINT,active BOOLEAN,assigned_by VARCHAR(100),revoked_by VARCHAR(100),revoked_at TIMESTAMP,PRIMARY KEY(tenant_id,username,role_id))");
        db.execute("CREATE TABLE employee_register_assignments(tenant_id BIGINT,branch_id BIGINT,username VARCHAR(100),register_id BIGINT,active BOOLEAN,assigned_by VARCHAR(100),revoked_by VARCHAR(100),revoked_at TIMESTAMP,PRIMARY KEY(tenant_id,username,register_id))");
        db.execute("CREATE TABLE vendor_roles(tenant_id BIGINT,id BIGINT AUTO_INCREMENT,role_key VARCHAR(120),display_name VARCHAR(120),active BOOLEAN,version BIGINT DEFAULT 0,created_by VARCHAR(100),updated_by VARCHAR(100),PRIMARY KEY(tenant_id,id),UNIQUE(tenant_id,role_key))");
        db.execute("CREATE TABLE vendor_role_permissions(tenant_id BIGINT,role_id BIGINT,permission VARCHAR(80),created_by VARCHAR(100),UNIQUE(tenant_id,role_id,permission))");
        db.execute("CREATE TABLE platform_user_permissions(username VARCHAR(100),permission VARCHAR(80),PRIMARY KEY(username,permission))");
        db.execute("CREATE TABLE customer_profiles(id BIGINT PRIMARY KEY,user_id BIGINT UNIQUE)");
        db.execute("CREATE TABLE registers(id BIGINT PRIMARY KEY,tenant_id BIGINT,branch_id BIGINT)");
        db.execute("CREATE TABLE tenant_customers(id VARCHAR(64) PRIMARY KEY,tenant_id BIGINT,customer_id BIGINT,consent_at TIMESTAMP,provenance VARCHAR(80),UNIQUE(tenant_id,customer_id))");
    }

    private void seedLegacyRows(JdbcTemplate db) {
        db.update("INSERT INTO users VALUES(910101,'admin@saumya.demo.test','legacy-hash',TRUE)");
        db.update("INSERT INTO employees VALUES('admin@saumya.demo.test',910001,910001,TRUE)");
        db.update("INSERT INTO employee_role_assignments(tenant_id,branch_id,username,role_id,active,assigned_by) VALUES(910001,910001,'admin@saumya.demo.test',1,TRUE,'admin@saumya.demo.test')");
        db.update("INSERT INTO employee_register_assignments(tenant_id,branch_id,username,register_id,active,assigned_by) VALUES(910001,910001,'admin@saumya.demo.test',910011,TRUE,'admin@saumya.demo.test')");
        db.update("INSERT INTO users VALUES(940001,'superadmin@platform.demo.test','legacy-hash',TRUE)");
        db.update("INSERT INTO users VALUES(949001,'asha@customer.demo.test','legacy-hash',TRUE)");
        db.update("INSERT INTO users VALUES(949002,'rahul@customer.demo.test','legacy-hash',TRUE)");
        db.update("INSERT INTO users VALUES(900003,'local-supervisor','legacy-hash',TRUE)");
        db.update("INSERT INTO users VALUES(900001,'demo-customer','legacy-hash',TRUE)");
        for (long tenant : new long[] {910001,920001,930001}) {
            db.update("INSERT INTO registers VALUES(?,?,?)", tenant+10,tenant,tenant);
            db.update("INSERT INTO registers VALUES(?,?,?)", tenant+11,tenant,tenant);
            db.update("INSERT INTO registers VALUES(?,?,?)", tenant+12,tenant,tenant);
        }
    }
}
