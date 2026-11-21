package db.migration;
import java.sql.Connection;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;
class V49RemoveLegacyEmployeePermissionsTest {
 @Test void dropsOnlyTheRetiredEmployeePermissionTable() throws Exception {
  var source=new DriverManagerDataSource("jdbc:h2:mem:v49-"+java.util.UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
  var db=new JdbcTemplate(source);
  db.execute("CREATE TABLE employee_permissions(username VARCHAR(100),permission VARCHAR(80))");
  db.execute("CREATE TABLE vendor_roles(id BIGINT PRIMARY KEY,role_key VARCHAR(100))");
  db.execute("CREATE TABLE vendor_role_permissions(role_id BIGINT,permission VARCHAR(80))");
  db.execute("CREATE TABLE employee_role_assignments(role_id BIGINT,username VARCHAR(100))");
  db.update("INSERT INTO employee_permissions VALUES('employee@vendor.test','POS_VIEW')");
  db.update("INSERT INTO vendor_roles VALUES(7,'standard:POS_CASHIER')");
  db.update("INSERT INTO vendor_role_permissions VALUES(7,'POS_VIEW')");
  db.update("INSERT INTO employee_role_assignments VALUES(7,'cashier@vendor.test')");
  try(Connection c=db.getDataSource().getConnection()){V49__remove_legacy_employee_permissions.dropLegacyTable(c);}
  assertThat(db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name)='employee_permissions'",Integer.class)).isZero();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_roles",Integer.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_role_permissions",Integer.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM employee_role_assignments",Integer.class)).isEqualTo(1);
 }
}
