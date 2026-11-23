package db.migration;

import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LocalDemoFlywaySeedMySqlTest {
 @Test void cleanMySqlInstallSeedsTenantOwnedStandardRolesAndNoLegacyGrantStore() throws Exception {
  String baseUrl=System.getenv("TEST_DB_URL");
  String user=System.getenv("TEST_DB_USER");
  String password=System.getenv("TEST_DB_PASSWORD");
  assumeTrue(baseUrl!=null && baseUrl.startsWith("jdbc:mysql://"),"MySQL-only local migration smoke test");
  String schema="solar_local_seed_"+UUID.randomUUID().toString().replace("-","");
  String serverUrl=baseUrl.substring(0,baseUrl.lastIndexOf('/')+1);
  String targetUrl=serverUrl+schema;
  try(var admin=DriverManager.getConnection(baseUrl,user,password); Statement statement=admin.createStatement()){
   statement.execute("CREATE DATABASE "+schema);
  }
  try {
   Flyway.configure().dataSource(targetUrl,user,password)
       .locations("classpath:db/migration","classpath:db/local-migration")
       .schemas(schema).defaultSchema(schema).createSchemas(false).load().migrate();
   JdbcTemplate db=new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(targetUrl,user,password));
   assertThat(count(db,"SELECT COUNT(*) FROM vendor_roles WHERE role_key LIKE 'standard:%'")).isEqualTo(15);
   assertThat(count(db,"SELECT COUNT(*) FROM vendor_role_permissions")).isEqualTo(192);
   assertThat(count(db,"SELECT COUNT(*) FROM employee_role_assignments WHERE active=TRUE")).isEqualTo(15);
   assertThat(count(db,"SELECT COUNT(*) FROM employee_register_assignments WHERE active=TRUE")).isEqualTo(9);
   assertThat(count(db,"SELECT COUNT(*) FROM vendor_access_audit_events")).isGreaterThanOrEqualTo(39);
   assertThat(count(db,"SELECT COUNT(*) FROM platform_user_permissions")).isEqualTo(11);
   assertThat(count(db,"SELECT COUNT(*) FROM platform_user_permissions WHERE permission='PLATFORM_TENANT_REACTIVATE'")).isEqualTo(1);
   assertThat(count(db,"SELECT COUNT(*) FROM vendor_role_permissions WHERE permission LIKE 'PLATFORM_%'")).isZero();
   assertThat(count(db,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='employee_permissions'")).isZero();
   assertThat(count(db,"SELECT COUNT(*) FROM vendor_roles WHERE template_key IS NULL OR template_version<>1")).isZero();
   assertThat(count(db,"SELECT COUNT(*) FROM employees e LEFT JOIN employee_role_assignments a ON a.tenant_id=e.tenant_id AND a.username=e.username AND a.active=TRUE WHERE (e.username LIKE '%.sales.executive@somya.local' OR e.username LIKE '%.operations.manager@somya.local' OR e.username LIKE '%.service.manager@somya.local' OR e.username LIKE '%.service.engineer@somya.local' OR e.username LIKE '%.content.manager@somya.local') AND a.role_id IS NOT NULL")).isZero();
  } finally {
   try(var admin=DriverManager.getConnection(baseUrl,user,password); Statement statement=admin.createStatement()){
    statement.execute("DROP DATABASE IF EXISTS "+schema);
   }
  }
 }
 private static int count(JdbcTemplate db,String sql){return db.queryForObject(sql,Integer.class);}
}
