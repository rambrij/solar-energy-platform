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
   var flyway = Flyway.configure().dataSource(targetUrl,user,password)
       .locations("classpath:db/migration","classpath:db/local-migration")
       .schemas(schema).defaultSchema(schema).createSchemas(false).load();
   assertThat(flyway.migrate().migrationsExecuted).isEqualTo(23);
   assertThat(flyway.migrate().migrationsExecuted).isZero();
   var dataSource=new org.springframework.jdbc.datasource.DriverManagerDataSource(targetUrl,user,password);
   var seedScript=new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
       new org.springframework.core.io.ClassPathResource("db/local-seed/local_demo_solar_catalog.sql"));
   seedScript.execute(dataSource);
   seedScript.execute(dataSource); // A second application start must not duplicate stock or rows.
   JdbcTemplate db=new JdbcTemplate(dataSource);
   assertThat(count(db,"SELECT COUNT(*) FROM products WHERE tenant_id IN (910001,920001,930001)")).isEqualTo(36);
   assertThat(count(db,"SELECT COUNT(*) FROM catalog_categories WHERE tenant_id IN (910001,920001,930001)")).isEqualTo(21);
   assertThat(count(db,"SELECT COUNT(*) FROM products WHERE tenant_id IN (910001,920001,930001) AND published=TRUE")).isEqualTo(36);
   assertThat(count(db,"SELECT COUNT(*) FROM inventory_receipts WHERE tenant_id IN (910001,920001,930001) AND command_key LIKE 'local-demo-catalog-v2-%'")).isEqualTo(36);
   assertThat(count(db,"SELECT COUNT(*) FROM inventory i JOIN products p ON p.tenant_id=i.tenant_id AND p.id=i.product_id WHERE i.tenant_id IN (910001,920001,930001) AND i.quantity-i.reserved<>100")).isZero();
   assertThat(count(db,"SELECT COUNT(*) FROM products p LEFT JOIN product_categories pc ON pc.tenant_id=p.tenant_id AND pc.product_id=p.id LEFT JOIN catalog_categories c ON c.tenant_id=pc.tenant_id AND c.id=pc.category_id WHERE p.tenant_id IN (910001,920001,930001) AND (pc.product_id IS NULL OR c.id IS NULL)")).isZero();
   assertThat(count(db,"SELECT COUNT(*) FROM (SELECT tenant_id,sku FROM products WHERE tenant_id IN (910001,920001,930001) GROUP BY tenant_id,sku HAVING COUNT(*)>1) duplicate_skus")).isZero();
   assertThat(count(db,"SELECT COUNT(*) FROM vendor_roles WHERE role_key LIKE 'standard:%'")).isEqualTo(24);
   // V14 adds installation grants; V15 adds service grants; V20 adds invoice-view grants; V22 adds INVENTORY_TRANSFER for each seeded Vendor Admin and Inventory Manager role.
   assertThat(count(db,"SELECT COUNT(*) FROM vendor_role_permissions")).isEqualTo(312);
   assertThat(count(db,"SELECT COUNT(*) FROM vendor_role_permissions WHERE permission='INVENTORY_TRANSFER'")).isEqualTo(6);
   assertThat(count(db,"SELECT COUNT(*) FROM vendor_role_permissions WHERE permission IN ('RETURN_VIEW','RETURN_APPROVE')")).isEqualTo(9);
   assertThat(count(db,"SELECT COUNT(*) FROM employee_role_assignments WHERE active=TRUE")).isEqualTo(24);
   assertThat(count(db,"SELECT COUNT(*) FROM employee_register_assignments WHERE active=TRUE")).isEqualTo(9);
   assertThat(count(db,"SELECT COUNT(*) FROM vendor_access_audit_events")).isGreaterThanOrEqualTo(39);
   assertThat(count(db,"SELECT COUNT(*) FROM platform_user_permissions")).isEqualTo(14);
   assertThat(count(db,"SELECT COUNT(*) FROM platform_user_permissions WHERE username='platform.superadmin@somya.local' AND permission IN ('PLATFORM_TENANT_VIEW','PLATFORM_CONFIG_MANAGE','PLATFORM_IDENTITY_MANAGE')")).isEqualTo(3);
   assertThat(count(db,"SELECT COUNT(*) FROM platform_user_permissions WHERE permission='PLATFORM_TENANT_REACTIVATE'")).isEqualTo(1);
   assertThat(count(db,"SELECT COUNT(*) FROM vendor_role_permissions WHERE permission LIKE 'PLATFORM_%'")).isZero();
   assertThat(count(db,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='employee_permissions'")).isZero();
   assertThat(count(db,"SELECT COUNT(*) FROM vendor_roles WHERE template_key IS NULL OR template_version<>1")).isZero();
   assertThat(count(db,"SELECT COUNT(*) FROM employees e LEFT JOIN employee_role_assignments a ON a.tenant_id=e.tenant_id AND a.username=e.username AND a.active=TRUE WHERE (e.username LIKE '%.operations.manager@somya.local' OR e.username LIKE '%.service.manager@somya.local' OR e.username LIKE '%.service.engineer@somya.local' OR e.username LIKE '%.content.manager@somya.local') AND a.role_id IS NOT NULL")).isEqualTo(6);
  } finally {
   try(var admin=DriverManager.getConnection(baseUrl,user,password); Statement statement=admin.createStatement()){
    statement.execute("DROP DATABASE IF EXISTS "+schema);
   }
  }
 }
 private static int count(JdbcTemplate db,String sql){return db.queryForObject(sql,Integer.class);}
}
