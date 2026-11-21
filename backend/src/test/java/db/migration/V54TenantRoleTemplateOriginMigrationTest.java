package db.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import static org.assertj.core.api.Assertions.assertThat;

/** Verifies V54 upgrades a populated pre-template tenant-role table without rewriting roles or grants. */
class V54TenantRoleTemplateOriginMigrationTest {
    @Test void addsNullableTemplateOriginWithoutChangingExistingTenantRolesOrAssignments() {
        var source=new DriverManagerDataSource("jdbc:h2:mem:v54-"+java.util.UUID.randomUUID()
                +";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        var db=new JdbcTemplate(source);
        db.execute("""
            CREATE TABLE vendor_roles(
                id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT NOT NULL,role_key VARCHAR(120) NOT NULL,
                display_name VARCHAR(120) NOT NULL,active BOOLEAN NOT NULL,created_by VARCHAR(100) NOT NULL,
                updated_by VARCHAR(100) NOT NULL,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,version BIGINT DEFAULT 0,
                UNIQUE(tenant_id,role_key))
            """);
        db.execute("""
            CREATE TABLE vendor_role_permissions(
                tenant_id BIGINT NOT NULL,role_id BIGINT NOT NULL,permission VARCHAR(80) NOT NULL,
                created_by VARCHAR(100) NOT NULL,PRIMARY KEY(tenant_id,role_id,permission))
            """);
        db.execute("""
            CREATE TABLE employee_role_assignments(
                tenant_id BIGINT NOT NULL,username VARCHAR(100) NOT NULL,role_id BIGINT NOT NULL,
                active BOOLEAN NOT NULL,PRIMARY KEY(tenant_id,username,role_id))
            """);
        db.update("""
            INSERT INTO vendor_roles(tenant_id,role_key,display_name,active,created_by,updated_by)
            VALUES(7,'custom-operator','Existing custom operator',TRUE,'owner@vendor.test','owner@vendor.test')
            """);
        Long roleId=db.queryForObject("SELECT id FROM vendor_roles WHERE tenant_id=7 AND role_key='custom-operator'",Long.class);
        db.update("INSERT INTO vendor_role_permissions VALUES(7,?,'PRODUCT_VIEW','owner@vendor.test')",roleId);
        db.update("INSERT INTO employee_role_assignments VALUES(7,'operator@vendor.test',?,TRUE)",roleId);

        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V54__tenant_role_template_origin.sql"))
                .execute(source);

        assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_roles WHERE tenant_id=7 AND id=?",Integer.class,roleId))
                .isEqualTo(1);
        assertThat(db.queryForObject("SELECT template_key FROM vendor_roles WHERE tenant_id=7 AND id=?",String.class,roleId))
                .isNull();
        assertThat(db.queryForObject("SELECT template_version FROM vendor_roles WHERE tenant_id=7 AND id=?",Integer.class,roleId))
                .isNull();
        assertThat(db.queryForList("SELECT permission FROM vendor_role_permissions WHERE tenant_id=7 AND role_id=?",
                String.class,roleId)).containsExactly("PRODUCT_VIEW");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM employee_role_assignments WHERE tenant_id=7 AND role_id=? AND active=TRUE",
                Integer.class,roleId)).isEqualTo(1);
        db.update("""
            UPDATE vendor_roles SET template_key='POS_CASHIER',template_version=1
            WHERE tenant_id=7 AND id=?
            """,roleId);
        assertThat(db.queryForObject("SELECT template_key FROM vendor_roles WHERE tenant_id=7 AND id=?",String.class,roleId))
                .isEqualTo("POS_CASHIER");
    }
}
