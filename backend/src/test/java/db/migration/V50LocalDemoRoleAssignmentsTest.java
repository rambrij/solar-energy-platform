package db.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import static org.assertj.core.api.Assertions.assertThat;

class V50LocalDemoRoleAssignmentsTest {
    @Test void createsExplicitRolesAndAssignmentsWithoutOverwritingExistingRoleHistory()throws Exception{
        var source=new DriverManagerDataSource("jdbc:h2:mem:v50-"+java.util.UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        var db=new JdbcTemplate(source);
        db.execute("CREATE TABLE local_demo_role_permission_seed(tenant_id BIGINT,username VARCHAR(100),role_name VARCHAR(40),permission VARCHAR(60))");
        db.execute("CREATE TABLE employees(tenant_id BIGINT,branch_id BIGINT,username VARCHAR(100))");
        db.execute("CREATE TABLE vendor_roles(tenant_id BIGINT,id BIGINT AUTO_INCREMENT,role_key VARCHAR(120),display_name VARCHAR(120),active BOOLEAN,created_by VARCHAR(100),updated_by VARCHAR(100),PRIMARY KEY(tenant_id,id),UNIQUE(tenant_id,role_key))");
        db.execute("CREATE TABLE vendor_role_permissions(tenant_id BIGINT,role_id BIGINT,permission VARCHAR(80),created_by VARCHAR(100),UNIQUE(tenant_id,role_id,permission))");
        db.execute("CREATE TABLE employee_role_assignments(tenant_id BIGINT,branch_id BIGINT,username VARCHAR(100),role_id BIGINT,active BOOLEAN,assigned_by VARCHAR(100),UNIQUE(tenant_id,username,role_id))");
        db.execute("CREATE TABLE registers(id BIGINT,tenant_id BIGINT,branch_id BIGINT)");
        db.execute("CREATE TABLE employee_register_assignments(tenant_id BIGINT,branch_id BIGINT,username VARCHAR(100),register_id BIGINT,active BOOLEAN,assigned_by VARCHAR(100),UNIQUE(tenant_id,username,register_id))");

        db.update("INSERT INTO employees VALUES(1,1,'cashier@vendor.test'),(1,1,'admin@vendor.test')");
        db.update("INSERT INTO vendor_roles(tenant_id,role_key,display_name,active,created_by,updated_by) VALUES(1,'custom-admin','Custom Admin',TRUE,'admin@vendor.test','admin@vendor.test')");
        Long existingRole=db.queryForObject("SELECT id FROM vendor_roles WHERE tenant_id=1 AND role_key='custom-admin'",Long.class);
        db.update("INSERT INTO vendor_role_permissions VALUES(1,?,'USER_VIEW','admin@vendor.test')",existingRole);
        db.update("INSERT INTO employee_role_assignments VALUES(1,1,'admin@vendor.test',?,TRUE,'admin@vendor.test')",existingRole);
        db.update("INSERT INTO registers VALUES(1,1,1)");
        db.update("INSERT INTO local_demo_role_permission_seed VALUES(1,'cashier@vendor.test','POS_CASHIER','POS_VIEW'),(1,'cashier@vendor.test','POS_CASHIER','POS_SESSION_OPEN'),(1,'admin@vendor.test','VENDOR_ADMIN','USER_CREATE')");

        new ResourceDatabasePopulator(new ClassPathResource("db/local-migration/V50__local_demo_role_assignments.sql")).execute(source);

        assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_roles",Integer.class)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_roles WHERE role_key='demo-cashier-vendor.test'",Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_roles WHERE role_key='demo-admin-vendor.test'",Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM employee_role_assignments",Integer.class)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_role_permissions WHERE role_id=?",Integer.class,
            db.queryForObject("SELECT id FROM vendor_roles WHERE role_key='demo-cashier-vendor.test'",Long.class))).isEqualTo(2);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM employee_register_assignments WHERE username='cashier@vendor.test'",Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name)='local_demo_role_permission_seed'",Integer.class)).isZero();
    }
}
