package db.migration;

import java.sql.Connection;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;

class V49RemoveLegacyEmployeePermissionsTest {
    private JdbcTemplate database(){
        var source=new DriverManagerDataSource("jdbc:h2:mem:v49-"+java.util.UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        var db=new JdbcTemplate(source);
        db.execute("CREATE TABLE vendor_roles(tenant_id BIGINT NOT NULL,id BIGINT NOT NULL,role_key VARCHAR(120),display_name VARCHAR(120),created_by VARCHAR(100),PRIMARY KEY(tenant_id,id))");
        db.execute("CREATE TABLE vendor_role_permissions(tenant_id BIGINT NOT NULL,role_id BIGINT NOT NULL,permission VARCHAR(80) NOT NULL)");
        db.execute("CREATE TABLE employee_role_assignments(tenant_id BIGINT NOT NULL,role_id BIGINT NOT NULL,username VARCHAR(100) NOT NULL)");
        db.execute("CREATE TABLE employee_permissions(username VARCHAR(100) NOT NULL,permission VARCHAR(80) NOT NULL)");
        return db;
    }

    @Test void removesOnlyV45SyntheticLegacyRolesAndDropsEmployeePermissionStore()throws Exception{
        JdbcTemplate db=database();
        db.update("INSERT INTO vendor_roles VALUES(1,10,'legacy-cashier@vendor.test','Migrated grants: cashier@vendor.test','cashier@vendor.test')");
        db.update("INSERT INTO vendor_roles VALUES(1,11,'cashier','Cashier','admin@vendor.test')");
        db.update("INSERT INTO vendor_role_permissions VALUES(1,10,'POS_VIEW'),(1,11,'POS_VIEW')");
        db.update("INSERT INTO employee_role_assignments VALUES(1,10,'cashier@vendor.test'),(1,11,'cashier@vendor.test')");
        db.update("INSERT INTO employee_permissions VALUES('cashier@vendor.test','POS_VIEW')");
        try(Connection connection=db.getDataSource().getConnection()){
            V49__remove_legacy_employee_permissions.removeLegacyData(connection);
        }
        assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_roles",Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_roles WHERE role_key='cashier'",Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_role_permissions",Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM employee_role_assignments",Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM employee_role_assignments WHERE role_id=11",Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name)='employee_permissions'",Integer.class)).isZero();
    }
}
