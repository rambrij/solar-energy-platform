package db.migration;

import com.saumya.pos.membership.StandardRoleTemplates;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import static org.assertj.core.api.Assertions.assertThat;

class V50LocalDemoRoleAssignmentsTest {
 @Test void localDemoPermissionsExactlyMatchSupportedStandardTemplateSnapshots() throws Exception {
  String sql;
  try(var in=new ClassPathResource("db/local-migration/afterMigrateApplied__local_demo.sql").getInputStream()){
   sql=StreamUtils.copyToString(in,StandardCharsets.UTF_8);
  }
  assertThat(sql).contains("standard:", "employee_role_assignments", "demo_v51_standard_permissions", "vendor_access_audit_events");
  assertThat(sql).doesNotContain("demo_v51_role_grants","employee_permissions","legacy-");
  String v51Permissions=sql.substring(sql.indexOf("INSERT INTO demo_v51_standard_permissions VALUES"),
      sql.indexOf(";",sql.indexOf("INSERT INTO demo_v51_standard_permissions VALUES")));
  Map<String,Set<String>> actual=new HashMap<>();
  var matcher=Pattern.compile("\\('([A-Z_]+)','([A-Z_]+)'\\)").matcher(v51Permissions);
  while(matcher.find()) actual.computeIfAbsent(matcher.group(1),ignored->new HashSet<>()).add(matcher.group(2));
  Map<String,Set<String>> expected=new HashMap<>();
  for(var definition:StandardRoleTemplates.ALL) if(definition.supported())
   expected.put(definition.key(),new HashSet<>(definition.permissions()));
  assertThat(actual).isEqualTo(expected);
  assertThat(sql).contains("a.role_name IN ('VENDOR_ADMIN','POS_CASHIER','SALES_MANAGER','INVENTORY_MANAGER','ACCOUNTANT')");
  assertThat(sql).doesNotContain("('SALES_EXECUTIVE','","('OPERATIONS_MANAGER','","('SERVICE_MANAGER','","('SERVICE_ENGINEER','","('CONTENT_MANAGER','");
 }

 @Test void checkedInMultiVendorDemoGrantsExactlyMatchSupportedStandardTemplates() throws Exception {
  String sql;
  try(var in=new ClassPathResource("demo/setup-multi-vendor-demo.sql").getInputStream()){
   sql=StreamUtils.copyToString(in,StandardCharsets.UTF_8);
  }
  int start=sql.indexOf("INSERT INTO demo_grants VALUES");
  int end=sql.indexOf(";",start);
  assertThat(start).isGreaterThanOrEqualTo(0);
  assertThat(end).isGreaterThan(start);
  Map<String,Set<String>> actual=new HashMap<>();
  var matcher=Pattern.compile("\\('([A-Z_]+)','([A-Z0-9_]+)'\\)").matcher(sql.substring(start,end));
  while(matcher.find())actual.computeIfAbsent(matcher.group(1),ignored->new HashSet<>()).add(matcher.group(2));
  Map<String,Set<String>> expected=new HashMap<>();
  for(var definition:StandardRoleTemplates.ALL)if(definition.supported())
   expected.put(definition.key(),new HashSet<>(definition.permissions()));
  assertThat(actual).isEqualTo(expected);
 }


}
