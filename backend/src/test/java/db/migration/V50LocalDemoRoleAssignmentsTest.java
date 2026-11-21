package db.migration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
class V50LocalDemoRoleAssignmentsTest {
 @Test void localDemoAssignsOnlySupportedTenantOwnedStandardRoles() throws Exception {
  String sql;
  try(var in=new ClassPathResource("db/local-migration/V51__refresh_local_demo_credentials.sql").getInputStream()){
   sql=StreamUtils.copyToString(in,StandardCharsets.UTF_8);
  }
  assertThat(sql).contains("standard:", "employee_role_assignments", "demo_v51_standard_permissions");
  assertThat(sql).doesNotContain("demo_v51_role_grants","employee_permissions","legacy-");
  assertThat(sql).contains("'VENDOR_ADMIN'","'POS_CASHIER'","'SALES_MANAGER'","'INVENTORY_MANAGER'","'ACCOUNTANT'");
  assertThat(sql).contains("a.role_name IN ('VENDOR_ADMIN','POS_CASHIER','SALES_MANAGER','INVENTORY_MANAGER','ACCOUNTANT')");
  assertThat(sql).doesNotContain("('SALES_EXECUTIVE','","('OPERATIONS_MANAGER','","('SERVICE_MANAGER','","('SERVICE_ENGINEER','","('CONTENT_MANAGER','");
 }
}
