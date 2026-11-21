package db.migration;

import java.sql.Connection;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;

class V49RemoveLegacyEmployeePermissionsTest {
    private JdbcTemplate database() {
        var source=new DriverManagerDataSource("jdbc:h2:mem:v49-"+java.util.UUID.randomUUID()
            +";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        var db=new JdbcTemplate(source);
        db.execute("""
            CREATE TABLE vendor_roles(tenant_id BIGINT NOT NULL,id BIGINT AUTO_INCREMENT,role_key VARCHAR(120),
            display_name VARCHAR(120),active BOOLEAN DEFAULT TRUE,created_by VARCHAR(100),updated_by VARCHAR(100),
            version BIGINT DEFAULT 0,PRIMARY KEY(tenant_id,id),UNIQUE(tenant_id,role_key))
            """);
        db.execute("""
            CREATE TABLE vendor_role_permissions(tenant_id BIGINT NOT NULL,role_id BIGINT NOT NULL,
            permission VARCHAR(80) NOT NULL,created_by VARCHAR(100) NOT NULL,PRIMARY KEY(tenant_id,role_id,permission))
            """);
        db.execute("""
            CREATE TABLE employee_role_assignments(tenant_id BIGINT NOT NULL,branch_id BIGINT NOT NULL,
            username VARCHAR(100) NOT NULL,role_id BIGINT NOT NULL,active BOOLEAN NOT NULL,
            assigned_by VARCHAR(100) NOT NULL,assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            revoked_by VARCHAR(100),revoked_at TIMESTAMP,PRIMARY KEY(tenant_id,username,role_id))
            """);
        db.execute("""
            CREATE TABLE vendor_access_audit_events(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT NOT NULL,
            actor_username VARCHAR(100) NOT NULL,subject_username VARCHAR(100),resource_type VARCHAR(40),
            resource_id VARCHAR(120),action VARCHAR(60),reason VARCHAR(500))
            """);
        db.execute("CREATE TABLE employee_permissions(username VARCHAR(100),permission VARCHAR(80))");
        return db;
    }

    @Test void consolidatesSupportedLegacyGrantsAndRemovesObsoleteStoreIdempotently() throws Exception {
        JdbcTemplate db=database();
        db.update("""
            INSERT INTO vendor_roles(tenant_id,id,role_key,display_name,active,created_by,updated_by) VALUES
            (1,10,'legacy-cashier1@vendor.test','Migrated grants: cashier1@vendor.test',TRUE,'cashier1@vendor.test','cashier1@vendor.test'),
            (1,11,'cashier','Cashier',TRUE,'admin@vendor.test','admin@vendor.test'),
            (1,12,'legacy-cashier2@vendor.test','Migrated grants: cashier2@vendor.test',TRUE,'cashier2@vendor.test','cashier2@vendor.test'),
            (1,13,'legacy-engineer@vendor.test','Migrated grants: engineer@vendor.test',TRUE,'engineer@vendor.test','engineer@vendor.test'),
            (1,14,'legacy-admin@vendor.test','Custom imported role',TRUE,'admin@vendor.test','admin@vendor.test')
            """);
        db.update("""
            INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by) VALUES
            (1,10,'POS_VIEW','cashier1@vendor.test'),(1,11,'USER_VIEW','admin@vendor.test'),
            (1,12,'POS_VIEW','cashier2@vendor.test'),(1,13,'SERVICE_CREATE','engineer@vendor.test'),
            (1,14,'USER_VIEW','admin@vendor.test')
            """);
        db.update("""
            INSERT INTO employee_role_assignments(tenant_id,branch_id,username,role_id,active,assigned_by)
            VALUES(1,101,'cashier1@vendor.test',10,TRUE,'cashier1@vendor.test'),
            (1,101,'admin@vendor.test',11,TRUE,'admin@vendor.test'),
            (1,101,'cashier2@vendor.test',12,TRUE,'cashier2@vendor.test'),
            (1,101,'engineer@vendor.test',13,TRUE,'engineer@vendor.test'),
            (1,101,'admin@vendor.test',14,TRUE,'admin@vendor.test')
            """);
        db.update("INSERT INTO employee_permissions VALUES('cashier1@vendor.test','POS_VIEW')");
        try(Connection c=db.getDataSource().getConnection()) {
            V49__remove_legacy_employee_permissions.removeLegacyData(c);
            V49__remove_legacy_employee_permissions.removeLegacyData(c);
        }
        assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_roles",Integer.class)).isEqualTo(3);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_roles WHERE role_key='cashier'",Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_roles WHERE role_key='legacy-admin@vendor.test'",Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_roles WHERE role_key LIKE 'migrated-%'",Integer.class)).isEqualTo(1);
        Long migrated=db.queryForObject("SELECT id FROM vendor_roles WHERE role_key LIKE 'migrated-%'",Long.class);
        assertThat(db.queryForList("SELECT username FROM employee_role_assignments WHERE role_id=? ORDER BY username",String.class,migrated))
            .containsExactly("cashier1@vendor.test","cashier2@vendor.test");
        assertThat(db.queryForList("SELECT permission FROM vendor_role_permissions WHERE role_id=?",String.class,migrated))
            .containsExactly("POS_VIEW");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM employee_role_assignments WHERE username='engineer@vendor.test'",Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM employee_role_assignments WHERE role_id=11",Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_access_audit_events WHERE action='LEGACY_ROLE_CONSOLIDATED'",Integer.class)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name)='employee_permissions'",Integer.class)).isZero();
    }
}
