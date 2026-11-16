package com.saumya.pos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.saumya.pos.checkout.PaymentPolicy;
import com.saumya.pos.identity.PlatformPermissions;
import com.saumya.pos.platform.ConfigurationService;
import com.saumya.pos.platform.PlatformAuditService;
import com.saumya.pos.platform.internal.ConfigRepository;
import com.saumya.pos.retail.PosService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Platform Super Admin: authorization, lifecycle, provisioning, plans/subscriptions, audit, configuration, support access. */
@SpringBootTest(properties={"spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:pos;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}","spring.datasource.username=${TEST_DB_USER:sa}","spring.datasource.password=${TEST_DB_PASSWORD:}","checkout.cleanup-enabled=false"})
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("test")
@org.springframework.test.context.TestPropertySource(properties="payments.mock-enabled=true")
@org.springframework.test.context.TestExecutionListeners(listeners=org.springframework.test.context.support.DependencyInjectionTestExecutionListener.class,inheritListeners=false)
class PlatformAdminTest {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate db; @Autowired ObjectMapper json; @Autowired PosService pos; 
 @Autowired PasswordEncoder encoder; @Autowired PlatformTransactionManager transactions; @Autowired Clock clock; @Autowired ConfigRepository configs; @Autowired PlatformAuditService audit;
 static final String JSON="application/json";
 static final String ALL_PLATFORM[]=PlatformPermissions.ALL.toArray(String[]::new);

 @BeforeEach @AfterEach void clean(){
  for(String table:List.of("support_access_grants","subscription_payments","subscription_invoices","tenant_subscriptions","subscription_plan_versions","subscription_plans","platform_audit_events","platform_configuration"))db.update("DELETE FROM "+table);
  db.update("DELETE FROM platform_user_permissions WHERE username LIKE 'pa-%'");
  db.update("DELETE FROM pos_audit WHERE tenant_id IN (SELECT id FROM tenants WHERE slug LIKE 'pa-%')");
  db.update("DELETE FROM identity_refresh_tokens WHERE username LIKE 'pa-%'");db.update("DELETE FROM employee_permissions WHERE username LIKE 'pa-%'");db.update("DELETE FROM employees WHERE username LIKE 'pa-%'");
  db.update("DELETE FROM branches WHERE tenant_id IN (SELECT id FROM tenants WHERE slug LIKE 'pa-%')");db.update("DELETE FROM tenants WHERE slug LIKE 'pa-%'");db.update("DELETE FROM users WHERE username LIKE 'pa-%'");
 }
 void globalUser(String username,String password){long id=db.queryForObject("SELECT COALESCE(MAX(id),0)+1 FROM users",Long.class);db.update("INSERT INTO users VALUES(?,?,?,TRUE)",id,username,encoder.encode(password));}
 void operator(String username,String password,Set<String> permissions){globalUser(username,password);for(String permission:permissions)db.update("INSERT INTO platform_user_permissions(username,permission) VALUES(?,?)",username,permission);}
 RequestPostProcessor bearer(String username,String password)throws Exception{var response=mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login").with(csrf()).contentType(JSON).content(json.writeValueAsString(Map.of("username",username,"password",password)))).andExpect(status().isOk()).andReturn();String token=json.readTree(response.getResponse().getContentAsString()).get("accessToken").asText();return request->{request.addHeader("Authorization","Bearer "+token);return request;};}
 static RequestPostProcessor op(String name,String... permissions){return user(name).authorities(Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new));}
 String body(Object value)throws Exception{return json.writeValueAsString(value);}
 ResultActions post(String path,Object body,RequestPostProcessor who)throws Exception{return mvc.perform(MockMvcRequestBuilders.post(path).with(csrf()).with(who).contentType(JSON).content(body(body)));}
 long tenant(String slug)throws Exception{
  var result=post("/api/v1/platform/tenants",Map.of("slug",slug,"name","Pa Store","legalName","Pa Store Pvt Ltd","contactEmail","owner@example.com"),op("pa-root","PLATFORM_TENANT_CREATE")).andExpect(status().isCreated()).andReturn();
  return ((Number)JsonPath.read(result.getResponse().getContentAsString(),"$.tenant.id")).longValue();
 }
 ResultActions move(long id,String target,String reason,String... permissions)throws Exception{
  var request=new LinkedHashMap<String,Object>();request.put("status",target);if(reason!=null)request.put("reason",reason);
  return post("/api/v1/platform/tenants/"+id+"/status",request,op("pa-root",permissions));
 }
 void activate(long id)throws Exception{move(id,"UNDER_REVIEW","Documents received","PLATFORM_TENANT_UPDATE").andExpect(status().isOk());move(id,"ACTIVE","Review approved","PLATFORM_TENANT_UPDATE").andExpect(status().isOk());}
 String tenantStatus(long id){return db.queryForObject("SELECT status FROM tenants WHERE id=?",String.class,id);}
 int audits(String action){return db.queryForObject("SELECT COUNT(*) FROM platform_audit_events WHERE action=?",Integer.class,action);}
 static Map<String,Object> terms(String price){return new LinkedHashMap<>(Map.of("interval","MONTHLY","price",price,"currency","INR","taxRate","18.00","taxInclusive",false,"graceDays",5,"maxUsers",10,"features",List.of("pos","marketplace")));}
 String plan(String code,String price)throws Exception{post("/api/v1/platform/plans",Map.of("code",code,"name","Basic","terms",terms(price)),op("pa-root","PLATFORM_PLAN_MANAGE")).andExpect(status().isOk());return code;}

 // ---- Authorization ----
 @Test void everyPlatformEndpointRequiresItsPermissionAndCsrf()throws Exception{
  record Endpoint(String method,String path,String body,String permission){}
  var endpoints=List.of(new Endpoint("GET","/api/v1/platform/overview",null,"PLATFORM_ANALYTICS_VIEW"),new Endpoint("GET","/api/v1/platform/health",null,"PLATFORM_ANALYTICS_VIEW"),new Endpoint("GET","/api/v1/platform/audit",null,"PLATFORM_AUDIT_VIEW"),
   new Endpoint("GET","/api/v1/platform/plans",null,"PLATFORM_PLAN_MANAGE"),new Endpoint("POST","/api/v1/platform/plans","{}","PLATFORM_PLAN_MANAGE"),new Endpoint("POST","/api/v1/platform/plans/x/versions","{}","PLATFORM_PLAN_MANAGE"),
   new Endpoint("GET","/api/v1/platform/configuration",null,"PLATFORM_ANALYTICS_VIEW"),new Endpoint("PUT","/api/v1/platform/configuration/platform.notification.sender","{\"value\":\"a\"}","PLATFORM_SUBSCRIPTION_ADJUST"),
   new Endpoint("POST","/api/v1/platform/tenants","{}","PLATFORM_TENANT_CREATE"),new Endpoint("POST","/api/v1/platform/tenants/1/vendor-admin","{}","PLATFORM_TENANT_CREATE"),new Endpoint("PUT","/api/v1/platform/tenants/1","{}","PLATFORM_TENANT_UPDATE"),
   new Endpoint("GET","/api/v1/platform/tenants/1/subscriptions",null,"PLATFORM_SUBSCRIPTION_VIEW"),new Endpoint("POST","/api/v1/platform/tenants/1/subscriptions","{}","PLATFORM_SUBSCRIPTION_ADJUST"),
   new Endpoint("POST","/api/v1/platform/tenants/1/subscriptions/invoices","{}","PLATFORM_SUBSCRIPTION_ADJUST"),new Endpoint("POST","/api/v1/platform/subscription-invoices/x/payments","{}","PLATFORM_SUBSCRIPTION_ADJUST"),
   new Endpoint("GET","/api/v1/platform/tenants/1/support-access",null,"PLATFORM_SUPPORT_ACCESS"),new Endpoint("POST","/api/v1/platform/tenants/1/support-access","{}","PLATFORM_SUPPORT_ACCESS"));
  for(var e:endpoints){
   String wrong=e.permission().equals("PLATFORM_AUDIT_VIEW")?"PLATFORM_SUPPORT_ACCESS":"PLATFORM_AUDIT_VIEW";
   java.util.function.Function<RequestPostProcessor,org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder> req=who->{
    var b=switch(e.method()){case "GET"->get(e.path());case "PUT"->put(e.path());default->MockMvcRequestBuilders.post(e.path());};
    b=b.with(who);if(e.body()!=null)b=b.contentType(JSON).content(e.body());return b;};
   boolean write=!e.method().equals("GET");
   mvc.perform(req.apply(csrf())).andExpect(status().isUnauthorized());
   mvc.perform(req.apply(op("x",wrong)).with(csrf())).andExpect(status().isForbidden());
   mvc.perform(req.apply(op("x","POS_VIEW","POS_SALE_CREATE","POS_REPORT_VIEW")).with(csrf())).andExpect(status().isForbidden());
   if(write)mvc.perform(req.apply(op("x",e.permission()))).andExpect(status().isForbidden());
   var allowed=mvc.perform(req.apply(op("x",e.permission())).with(csrf())).andReturn().getResponse().getStatus();
   assertThat(allowed).as(e.method()+" "+e.path()).isNotIn(401,403);
  }
 }
 @Test void vendorRolesCanNeverHoldPlatformPermissions()throws Exception{
  long id=tenant("pa-vendorperm");var created=post("/api/v1/platform/tenants/"+id+"/vendor-admin",Map.of("username","pa-admin1"),op("pa-root","PLATFORM_TENANT_CREATE")).andExpect(status().isCreated()).andReturn();
  String password=JsonPath.read(created.getResponse().getContentAsString(),"$.temporaryPassword");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM employee_permissions WHERE permission LIKE 'PLATFORM%'",Integer.class)).isZero();
  assertThatThrownBy(()->db.update("INSERT INTO employee_permissions(username,permission,tenant_id) VALUES('pa-admin1','PLATFORM_TENANT_CLOSE',?)",id)).as("V28 constraint").isInstanceOf(DataIntegrityViolationException.class);
  assertThatThrownBy(()->db.update("INSERT INTO platform_user_permissions(username,permission) VALUES('pa-admin1','POS_VIEW')")).as("V37 constraint").isInstanceOf(DataIntegrityViolationException.class);
  activate(id);bearer("pa-admin1",password);
  var vendorAuthorities=db.queryForList("SELECT permission FROM employee_permissions WHERE username='pa-admin1'",String.class);
  mvc.perform(get("/api/v1/platform/overview").with(op("pa-admin1",vendorAuthorities.toArray(String[]::new)))).andExpect(status().isForbidden());
  db.update("INSERT INTO platform_user_permissions(username,permission) VALUES('pa-admin1','PLATFORM_AUDIT_VIEW')");
  mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login").with(csrf()).contentType(JSON).content(body(Map.of("username","pa-admin1","password",password)))).as("an identity that is both an operator and a vendor employee is refused").andExpect(status().isUnauthorized());
 }
 @Test void platformOperatorsUseTheGlobalLoginButHaveNoVendorCommercialAuthority()throws Exception{
  operator("pa-root","S3cret-pass-12",PlatformPermissions.ALL);var token=bearer("pa-root","S3cret-pass-12");
  mvc.perform(get("/api/v1/platform/overview").with(token)).andExpect(status().isOk()).andExpect(jsonPath("$.tenantsByStatus").exists());
  mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login").with(csrf()).contentType(JSON).content(body(Map.of("username","pa-root","password","wrong-password")))).andExpect(status().isUnauthorized());
  var me=mvc.perform(get("/api/v1/auth/me").with(token)).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("pa-root")).andExpect(jsonPath("$.tenantId").isEmpty()).andReturn().getResponse().getContentAsString();
  assertThat(JsonPath.<List<String>>read(me,"$.permissions")).containsExactlyInAnyOrderElementsOf(PlatformPermissions.ALL);
  mvc.perform(get("/api/v1/vendor/products").with(token)).andExpect(status().isForbidden());
  mvc.perform(MockMvcRequestBuilders.post("/api/v1/vendor/pos/drafts").with(csrf()).with(token).header("Idempotency-Key","k1").contentType(JSON).content("{}")).andExpect(status().isForbidden());
  mvc.perform(MockMvcRequestBuilders.post("/api/v1/vendor/pos/drafts").with(csrf()).with(op("pa-root",ALL_PLATFORM)).header("Idempotency-Key","k2").contentType(JSON).content("{}")).andExpect(status().isForbidden());
  assertThatThrownBy(()->pos.scope("pa-root")).isInstanceOf(ResponseStatusException.class);
 }
 @Test void aVendorTokenNeverCarriesPlatformPermissions()throws Exception{
  long id=tenant("pa-novendor");var created=post("/api/v1/platform/tenants/"+id+"/vendor-admin",Map.of("username","pa-admin2"),op("pa-root","PLATFORM_TENANT_CREATE")).andExpect(status().isCreated()).andReturn();
  String password=JsonPath.read(created.getResponse().getContentAsString(),"$.temporaryPassword");activate(id);
  var token=bearer("pa-admin2",password);
  mvc.perform(get("/api/v1/platform/overview").with(token)).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/auth/me").with(token)).andExpect(jsonPath("$.tenantId").value(id));
  var me=mvc.perform(get("/api/v1/auth/me").with(token)).andReturn().getResponse().getContentAsString();assertThat(JsonPath.<List<String>>read(me,"$.permissions")).noneMatch(p->p.startsWith("PLATFORM_"));
 }
 @Test void noPlatformPermissionIsReferencedOutsidePlatformIdentityAndMembershipGuards()throws Exception{
  Path root=Path.of("src/main/java");Assumptions.assumeTrue(Files.isDirectory(root));
  try(Stream<Path> files=Files.walk(root)){
   var offenders=files.filter(f->f.toString().endsWith(".java")).filter(f->{try{return Files.readString(f).contains("PLATFORM_");}catch(Exception e){return false;}})
    .map(f->f.toString().replace('\\','/')).filter(f->!f.contains("/com/saumya/pos/platform/")&&!f.contains("/com/saumya/pos/identity/")&&!f.contains("/com/saumya/pos/membership/")).toList();
   assertThat(offenders).as("vendor/commercial code must not mention PLATFORM_* (membership only filters it out)").isEmpty();
  }
 }
 // ---- Tenant lifecycle and onboarding ----
 @Test void tenantLifecycleHonoursPermissionsTransitionsAndStorefrontGate()throws Exception{
  long id=tenant("pa-life");assertThat(tenantStatus(id)).isEqualTo("PENDING");
  mvc.perform(get("/api/marketplace/vendors").param("q","pa-life")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  move(id,"ACTIVE","Skipping review","PLATFORM_TENANT_UPDATE").andExpect(status().isConflict());
  activate(id);assertThat(tenantStatus(id)).isEqualTo("ACTIVE");
  mvc.perform(get("/api/marketplace/vendors").param("q","pa-life")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
  move(id,"SUSPENDED","Compliance hold","PLATFORM_TENANT_UPDATE").andExpect(status().isForbidden());assertThat(tenantStatus(id)).isEqualTo("ACTIVE");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM platform_audit_events WHERE action='TENANT_SUSPENDED' AND outcome='DENIED' AND tenant_id=?",Integer.class,id)).isEqualTo(1);
  move(id,"SUSPENDED",null,"PLATFORM_TENANT_SUSPEND").andExpect(status().isBadRequest());
  move(id,"SUSPENDED","Compliance hold","PLATFORM_TENANT_SUSPEND").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED")).andExpect(jsonPath("$.statusReason").value("Compliance hold"));
  mvc.perform(get("/api/marketplace/vendors").param("q","pa-life")).andExpect(jsonPath("$.totalElements").value(0));
  move(id,"ACTIVE","Hold cleared","PLATFORM_TENANT_UPDATE").andExpect(status().isOk());
  move(id,"PENDING","Back to pending","PLATFORM_TENANT_UPDATE").andExpect(status().isConflict());
  move(id,"CLOSED","Contract ended","PLATFORM_TENANT_UPDATE").andExpect(status().isForbidden());
  move(id,"CLOSED","Contract ended","PLATFORM_TENANT_CLOSE").andExpect(status().isOk());assertThat(tenantStatus(id)).isEqualTo("CLOSED");
  move(id,"ACTIVE","Reopen","PLATFORM_TENANT_UPDATE").andExpect(status().isConflict());
  post("/api/v1/platform/tenants/"+id+"/vendor-admin",Map.of("username","pa-late"),op("pa-root","PLATFORM_TENANT_CREATE")).andExpect(status().isConflict());
  mvc.perform(put("/api/v1/platform/tenants/"+id).with(csrf()).with(op("pa-root","PLATFORM_TENANT_UPDATE")).contentType(JSON).content(body(Map.of("name","N","legalName","L","contactEmail","a@b.co")))).andExpect(status().isConflict());
  for(String action:List.of("TENANT_CREATED","TENANT_UNDER_REVIEW","TENANT_ACTIVE","TENANT_SUSPENDED","TENANT_CLOSED"))assertThat(audits(action)).as(action).isGreaterThanOrEqualTo(1);
 }
 @Test void onboardingIsIdempotentAndValidated()throws Exception{
  var request=Map.of("slug","pa-idem","name","Pa Store","legalName","Pa Store Pvt Ltd","contactEmail","owner@example.com");
  long first=tenant("pa-idem");
  post("/api/v1/platform/tenants",request,op("pa-root","PLATFORM_TENANT_CREATE")).andExpect(status().isOk()).andExpect(jsonPath("$.created").value(false)).andExpect(jsonPath("$.tenant.id").value(first));
  post("/api/v1/platform/tenants",Map.of("slug","pa-idem","name","Other","legalName","Other Ltd","contactEmail","x@y.co"),op("pa-root","PLATFORM_TENANT_CREATE")).andExpect(status().isConflict());
  for(String bad:List.of("Bad Slug","admin","a","-start","UPPER"))post("/api/v1/platform/tenants",Map.of("slug",bad,"name","N","legalName","L","contactEmail","a@b.co"),op("pa-root","PLATFORM_TENANT_CREATE")).andExpect(status().isBadRequest());
  post("/api/v1/platform/tenants",Map.of("slug","pa-bademail","name","N","legalName","L","contactEmail","not-an-email"),op("pa-root","PLATFORM_TENANT_CREATE")).andExpect(status().isBadRequest());
  assertThat(audits("TENANT_CREATED")).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM tenants WHERE slug LIKE 'pa-%'",Integer.class)).isEqualTo(1);
 }
 @Test void firstVendorAdminIsProvisionedOnceAndGatedByLifecycle()throws Exception{
  long id=tenant("pa-admin");
  var created=post("/api/v1/platform/tenants/"+id+"/vendor-admin",Map.of("username","pa-admin1"),op("pa-root","PLATFORM_TENANT_CREATE")).andExpect(status().isCreated()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store"))).andReturn();
  String password=JsonPath.read(created.getResponse().getContentAsString(),"$.temporaryPassword");assertThat(password).hasSize(20);
  assertThat(db.queryForObject("SELECT tenant_id FROM employees WHERE username='pa-admin1'",Long.class)).isEqualTo(id);
  assertThat(db.queryForList("SELECT permission FROM employee_permissions WHERE username='pa-admin1' AND tenant_id=?",String.class,id)).contains("POS_SALE_CREATE","PRODUCT_CREATE").noneMatch(p->p.startsWith("PLATFORM_"));
  assertThat(db.queryForObject("SELECT password_hash FROM users WHERE username='pa-admin1'",String.class)).isNotEqualTo(password).startsWith("$2");
  mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login").with(csrf()).contentType(JSON).content(body(Map.of("username","pa-admin1","password",password)))).andExpect(status().isUnauthorized());
  assertThatThrownBy(()->pos.scope("pa-admin1")).isInstanceOf(ResponseStatusException.class);
  activate(id);assertThat(pos.scope("pa-admin1").tenant()).isEqualTo(id);
  mvc.perform(get("/api/v1/auth/me").with(bearer("pa-admin1",password))).andExpect(status().isOk()).andExpect(jsonPath("$.tenantId").value(id));
  post("/api/v1/platform/tenants/"+id+"/vendor-admin",Map.of("username","pa-admin1"),op("pa-root","PLATFORM_TENANT_CREATE")).andExpect(status().isOk()).andExpect(jsonPath("$.created").value(false)).andExpect(jsonPath("$.temporaryPassword").isEmpty());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM employees WHERE tenant_id=?",Integer.class,id)).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM branches WHERE tenant_id=?",Integer.class,id)).isEqualTo(1);
  long other=tenant("pa-admin-b");post("/api/v1/platform/tenants/"+other+"/vendor-admin",Map.of("username","pa-admin1"),op("pa-root","PLATFORM_TENANT_CREATE")).andExpect(status().isConflict());
  globalUser("pa-customer@example.com","S3cret-pass-12");post("/api/v1/platform/tenants/"+id+"/vendor-admin",Map.of("username","pa-customer@example.com"),op("pa-root","PLATFORM_TENANT_CREATE")).andExpect(status().isConflict());
  post("/api/v1/platform/tenants/"+id+"/vendor-admin",Map.of("username","X Y"),op("pa-root","PLATFORM_TENANT_CREATE")).andExpect(status().isBadRequest());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM platform_audit_events WHERE details LIKE ?",Integer.class,"%"+password+"%")).isZero();
  assertThat(audits("VENDOR_ADMIN_PROVISIONED")).isEqualTo(1);assertThat(audits("VENDOR_ADMIN_PROVISION_REPLAYED")).isEqualTo(1);
 }
 @Test void overviewIsAggregateOnly()throws Exception{
  tenant("pa-agg");
  var text=mvc.perform(get("/api/v1/platform/overview").with(op("pa-root","PLATFORM_ANALYTICS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.tenantsByStatus.PENDING").isNumber()).andReturn().getResponse().getContentAsString();
  assertThat(JsonPath.<Map<String,Object>>read(text,"$").keySet()).containsExactlyInAnyOrder("tenantsByStatus","subscriptionsByStatus","subscriptionsByPlan","openInvoices","generatedAt");
 }

 // ---- Plans, subscriptions, payments ----
 @Test void planVersionsAreImmutableAndSubscriptionsKeepTheirAgreedTerms()throws Exception{
  String code=plan("pa-basic","1000.00");long id=tenant("pa-sub");
  var assigned=post("/api/v1/platform/tenants/"+id+"/subscriptions",Map.of("planCode",code),op("pa-root","PLATFORM_SUBSCRIPTION_ADJUST")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.planVersion").value(1)).andReturn();
  post("/api/v1/platform/plans/"+code+"/versions",terms("2000.00"),op("pa-root","PLATFORM_PLAN_MANAGE")).andExpect(status().isOk()).andExpect(jsonPath("$.versions.length()").value(2)).andExpect(jsonPath("$.versions[0].version").value(2));
  mvc.perform(get("/api/v1/platform/tenants/"+id+"/subscriptions").with(op("pa-root","PLATFORM_SUBSCRIPTION_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.current.price").value(1000.00)).andExpect(jsonPath("$.current.planVersion").value(1));
  post("/api/v1/platform/tenants/"+id+"/subscriptions/invoices",Map.of(),op("pa-root","PLATFORM_SUBSCRIPTION_ADJUST")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1180.00)).andExpect(jsonPath("$.tax").value(180.00));
  assertThat(db.queryForObject("SELECT price FROM subscription_plan_versions WHERE plan_code=? AND version=1",java.math.BigDecimal.class,code)).isEqualByComparingTo("1000.00");
  post("/api/v1/platform/tenants/"+id+"/subscriptions",Map.of("planCode",code),op("pa-root","PLATFORM_SUBSCRIPTION_ADJUST")).andExpect(status().isConflict());
  post("/api/v1/platform/tenants/"+id+"/subscriptions/status",Map.of("status","ACTIVE","reason","Approved start"),op("pa-root","PLATFORM_SUBSCRIPTION_ADJUST")).andExpect(status().isOk());
  post("/api/v1/platform/tenants/"+id+"/subscriptions",Map.of("planCode",code),op("pa-root","PLATFORM_SUBSCRIPTION_ADJUST")).andExpect(status().isOk()).andExpect(jsonPath("$.planVersion").value(2)).andExpect(jsonPath("$.price").value(2000.00));
  mvc.perform(get("/api/v1/platform/tenants/"+id+"/subscriptions").with(op("pa-root","PLATFORM_SUBSCRIPTION_VIEW"))).andExpect(jsonPath("$.history.length()").value(2)).andExpect(jsonPath("$.history[1].status").value("CANCELLED")).andExpect(jsonPath("$.invoices[0].total").value(1180.00));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM tenant_subscriptions WHERE tenant_id=? AND effective=1",Integer.class,id)).isEqualTo(1);
  post("/api/v1/platform/plans/"+code+"/active",Map.of("active",false),op("pa-root","PLATFORM_PLAN_MANAGE")).andExpect(status().isOk());
  post("/api/v1/platform/tenants/"+tenant("pa-sub2")+"/subscriptions",Map.of("planCode",code),op("pa-root","PLATFORM_SUBSCRIPTION_ADJUST")).andExpect(status().isConflict());
  var bad=terms("-1.00");post("/api/v1/platform/plans",Map.of("code","pa-bad","name","Bad","terms",bad),op("pa-root","PLATFORM_PLAN_MANAGE")).andExpect(status().isBadRequest());
  bad=terms("10.00");bad.put("currency","inr");post("/api/v1/platform/plans",Map.of("code","pa-bad2","name","Bad","terms",bad),op("pa-root","PLATFORM_PLAN_MANAGE")).andExpect(status().isBadRequest());
  post("/api/v1/platform/plans",Map.of("code",code,"name","Dup","terms",terms("1.00")),op("pa-root","PLATFORM_PLAN_MANAGE")).andExpect(status().isConflict());
 }
 @Test void subscriptionPaymentsUsePlatformMerchantAndNeverTouchCustomerPaymentData()throws Exception{
  var vendorBefore=new HashMap<String,Integer>();for(String vendorTable:List.of("vendor_payment_attempts","orders","order_tenders"))vendorBefore.put(vendorTable,db.queryForObject("SELECT COUNT(*) FROM "+vendorTable,Integer.class));
  String code=plan("pa-pay","1000.00");long id=tenant("pa-pay-tenant");activate(id);
  post("/api/v1/platform/tenants/"+id+"/subscriptions",Map.of("planCode",code),op("pa-root","PLATFORM_SUBSCRIPTION_ADJUST")).andExpect(status().isOk());
  var invoice=post("/api/v1/platform/tenants/"+id+"/subscriptions/invoices",Map.of(),op("pa-root","PLATFORM_SUBSCRIPTION_ADJUST")).andExpect(status().isOk()).andReturn();
  String invoiceId=JsonPath.read(invoice.getResponse().getContentAsString(),"$.id");
  post("/api/v1/platform/tenants/"+id+"/subscriptions/invoices",Map.of(),op("pa-root","PLATFORM_SUBSCRIPTION_ADJUST")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(invoiceId));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM subscription_invoices",Integer.class)).isEqualTo(1);
  var pay=Map.of("amount",1180.00,"method","OFFLINE","succeeded",true);
  post("/api/v1/platform/subscription-invoices/"+invoiceId+"/payments",pay,op("pa-root","PLATFORM_SUBSCRIPTION_ADJUST")).andExpect(status().isConflict());
  mvc.perform(put("/api/v1/platform/configuration/platform.subscription.payment.merchant_id").with(csrf()).with(op("pa-root","PLATFORM_SUBSCRIPTION_ADJUST")).contentType(JSON).content("{\"value\":\"acct_platform_1\"}")).andExpect(status().isOk());
  post("/api/v1/platform/subscription-invoices/"+invoiceId+"/payments",Map.of("amount",1000.00,"method","OFFLINE","succeeded",true),op("pa-root","PLATFORM_SUBSCRIPTION_ADJUST")).andExpect(status().isBadRequest());
  post("/api/v1/platform/subscription-invoices/"+invoiceId+"/payments",Map.of("amount",1180.00,"method","GATEWAY","succeeded",true),op("pa-root","PLATFORM_SUBSCRIPTION_ADJUST")).andExpect(status().isBadRequest());
  post("/api/v1/platform/subscription-invoices/"+invoiceId+"/payments",pay,op("pa-root","PLATFORM_SUBSCRIPTION_ADJUST")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID")).andExpect(jsonPath("$.payments[0].merchantId").value("acct_platform_1"));
  post("/api/v1/platform/subscription-invoices/"+invoiceId+"/payments",pay,op("pa-root","PLATFORM_SUBSCRIPTION_ADJUST")).andExpect(status().isConflict());
  assertThat(db.queryForObject("SELECT status FROM tenant_subscriptions WHERE tenant_id=? AND effective=1",String.class,id)).isEqualTo("ACTIVE");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM subscription_payments WHERE platform_merchant_id='acct_platform_1'",Integer.class)).isEqualTo(1);
  for(String vendorTable:List.of("vendor_payment_attempts","orders","order_tenders"))assertThat(db.queryForObject("SELECT COUNT(*) FROM "+vendorTable,Integer.class)).as(vendorTable+" untouched by subscription payments").isEqualTo(vendorBefore.get(vendorTable));
 }
 @Test void subscriptionStateIsSeparateFromTenantLifecycle()throws Exception{
  String code=plan("pa-sep","500.00");long id=tenant("pa-sep-tenant");activate(id);
  post("/api/v1/platform/tenants/"+id+"/subscriptions",Map.of("planCode",code),op("pa-root","PLATFORM_SUBSCRIPTION_ADJUST")).andExpect(status().isOk());
  for(String step:List.of("ACTIVE","PAST_DUE","GRACE_PERIOD","SUSPENDED"))post("/api/v1/platform/tenants/"+id+"/subscriptions/status",Map.of("status",step,"reason","Dunning step "+step),op("pa-root","PLATFORM_SUBSCRIPTION_ADJUST")).andExpect(status().isOk());
  assertThat(tenantStatus(id)).as("subscription suspension does not suspend the tenant (SUB-008)").isEqualTo("ACTIVE");
  post("/api/v1/platform/tenants/"+id+"/subscriptions/status",Map.of("status","PAST_DUE","reason","Invalid"),op("pa-root","PLATFORM_SUBSCRIPTION_ADJUST")).andExpect(status().isConflict());
  post("/api/v1/platform/tenants/"+id+"/subscriptions/status",Map.of("status","ACTIVE"),op("pa-root","PLATFORM_SUBSCRIPTION_ADJUST")).andExpect(status().isBadRequest());
  move(id,"SUSPENDED","Security hold","PLATFORM_TENANT_SUSPEND").andExpect(status().isOk());
  post("/api/v1/platform/tenants/"+id+"/subscriptions/status",Map.of("status","ACTIVE","reason","Paid up"),op("pa-root","PLATFORM_SUBSCRIPTION_ADJUST")).andExpect(status().isOk());
  assertThat(tenantStatus(id)).as("reactivating the subscription never clears a manual tenant suspension").isEqualTo("SUSPENDED");
  assertThat(audits("SUBSCRIPTION_REACTIVATED")).isEqualTo(1);
 }

 // ---- Audit, configuration, support access, production guards ----
 @Test void platformAuditIsRecordedFilterableAndPermissionGated()throws Exception{
  long id=tenant("pa-audit");activate(id);
  var all=mvc.perform(get("/api/v1/platform/audit").with(op("pa-root","PLATFORM_AUDIT_VIEW"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertThat(JsonPath.<List<String>>read(all,"$.items[*].action")).contains("TENANT_CREATED","TENANT_UNDER_REVIEW","TENANT_ACTIVE");assertThat(JsonPath.<List<String>>read(all,"$.items[*].actor")).containsOnly("pa-root");
  mvc.perform(get("/api/v1/platform/audit").param("action","TENANT_ACTIVE").param("tenantId",String.valueOf(id)).with(op("pa-root","PLATFORM_AUDIT_VIEW"))).andExpect(jsonPath("$.total").value(1));
  mvc.perform(get("/api/v1/platform/audit").param("tenantId","999999").with(op("pa-root","PLATFORM_AUDIT_VIEW"))).andExpect(jsonPath("$.total").value(0));
  mvc.perform(get("/api/v1/platform/audit").param("size","101").with(op("pa-root","PLATFORM_AUDIT_VIEW"))).andExpect(status().isBadRequest());
  mvc.perform(get("/api/v1/platform/audit").with(op("pa-root","PLATFORM_ANALYTICS_VIEW"))).andExpect(status().isForbidden());
 }
 @Test void configurationNeverStoresOrReturnsSecrets()throws Exception{
  String key="/api/v1/platform/configuration/platform.subscription.payment.api_secret_ref";
  mvc.perform(put(key).with(csrf()).with(op("pa-root","PLATFORM_SUBSCRIPTION_ADJUST")).contentType(JSON).content("{\"value\":\"sk_live_abcdef123456\"}")).andExpect(status().isBadRequest());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM platform_configuration",Integer.class)).isZero();
  var saved=mvc.perform(put(key).with(csrf()).with(op("pa-root","PLATFORM_SUBSCRIPTION_ADJUST")).contentType(JSON).content("{\"value\":\"env:PA_GATEWAY_SECRET\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.value").value("env:********")).andExpect(jsonPath("$.secret").value(true)).andReturn();
  assertThat(saved.getResponse().getContentAsString()).doesNotContain("PA_GATEWAY_SECRET");
  var listed=mvc.perform(get("/api/v1/platform/configuration").with(op("pa-root","PLATFORM_ANALYTICS_VIEW"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertThat(listed).doesNotContain("PA_GATEWAY_SECRET").contains("env:********");
  assertThat(db.queryForObject("SELECT details FROM platform_audit_events WHERE action='CONFIG_CHANGED'",String.class)).doesNotContain("PA_GATEWAY_SECRET").contains("env:********");
  mvc.perform(put("/api/v1/platform/configuration/unknown.key").with(csrf()).with(op("pa-root","PLATFORM_SUBSCRIPTION_ADJUST")).contentType(JSON).content("{\"value\":\"x\"}")).andExpect(status().isBadRequest());
  mvc.perform(put("/api/v1/platform/configuration/platform.notification.sender").with(csrf()).with(op("pa-root","PLATFORM_SUBSCRIPTION_ADJUST")).contentType(JSON).content("{\"value\":\"billing@example.com\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.value").value("billing@example.com")).andExpect(jsonPath("$.version").value(1));
  mvc.perform(put("/api/v1/platform/configuration/platform.notification.sender").with(csrf()).with(op("pa-root","PLATFORM_SUBSCRIPTION_ADJUST")).contentType(JSON).content("{\"value\":\"ops@example.com\"}")).andExpect(jsonPath("$.version").value(2));
  mvc.perform(get("/api/v1/platform/health").with(op("pa-root","PLATFORM_ANALYTICS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.databaseReachable").value(true)).andExpect(jsonPath("$.status").value("DEGRADED"));
 }
 @Test void mockProvidersAreRejectedInProduction(){
  var production=new ConfigurationService(configs,audit,clock,"production");var tx=new TransactionTemplate(transactions);
  assertThat(production.production()).isTrue();
  tx.executeWithoutResult(s->assertThatThrownBy(()->production.put("pa-root","platform.subscription.payment.provider","MOCK")).isInstanceOf(ResponseStatusException.class));
  tx.executeWithoutResult(s->assertThatThrownBy(()->production.put("pa-root","platform.subscription.payment.provider","MOCK_GATEWAY")).isInstanceOf(ResponseStatusException.class));
  tx.executeWithoutResult(s->production.put("pa-root","platform.subscription.payment.provider","ACME_PAY"));
  assertThat(configs.findById("platform.subscription.payment.provider").orElseThrow().value()).isEqualTo("ACME_PAY");
  var env=new org.springframework.mock.env.MockEnvironment();env.setActiveProfiles("production");
  assertThatThrownBy(()->new PaymentPolicy(true,env)).isInstanceOf(IllegalStateException.class).hasMessageContaining("forbidden in production");
 }
 @Test void supportAccessIsExplicitTimeLimitedPurposeBoundAndAudited()throws Exception{
  long id=tenant("pa-support");db.update("INSERT INTO pos_audit(id,tenant_id,actor,action,resource_id) VALUES('pa-audit-1',?,'cashier-1','SALE_CASH','order-private-77')",id);
  String path="/api/v1/platform/tenants/"+id+"/support-access";var support=op("pa-support-1","PLATFORM_SUPPORT_ACCESS");
  for(var invalid:List.of(Map.of("reason","short","ticket","SUP-1","minutes",30),Map.of("reason","Investigating a failed receipt reprint","ticket","bad ticket","minutes",30),Map.of("reason","Investigating a failed receipt reprint","ticket","SUP-1","minutes",241),Map.of("reason","Investigating a failed receipt reprint","ticket","SUP-1","minutes",4)))
   post(path,invalid,support).andExpect(status().isBadRequest());
  post(path,Map.of("reason","Investigating a failed receipt reprint","ticket","SUP-1001","minutes",30),op("pa-nosupport","PLATFORM_AUDIT_VIEW")).andExpect(status().isForbidden());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM support_access_grants",Integer.class)).isZero();
  var granted=post(path,Map.of("reason","Investigating a failed receipt reprint","ticket","SUP-1001","minutes",30),support).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.scope").value("VENDOR_AUDIT_READ")).andReturn();
  String grant=JsonPath.read(granted.getResponse().getContentAsString(),"$.id");String read="/api/v1/platform/support-access/"+grant+"/vendor-audit";
  mvc.perform(get(read).with(support)).andExpect(status().isOk()).andExpect(jsonPath("$.entries[0].resourceId").value("order-private-77"));
  mvc.perform(get(read).with(op("pa-support-2","PLATFORM_SUPPORT_ACCESS"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/platform/support-access/unknown/vendor-audit").with(support)).andExpect(status().isForbidden());
  db.update("UPDATE support_access_grants SET granted_at=?,expires_at=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(7200)),Timestamp.from(Instant.now().minusSeconds(3600)),grant);
  mvc.perform(get(read).with(support)).andExpect(status().isForbidden());
  var second=post(path,Map.of("reason","Second investigation of the same ticket","ticket","SUP-1001","minutes",60),support).andExpect(status().isOk()).andReturn();
  String grant2=JsonPath.read(second.getResponse().getContentAsString(),"$.id");
  post("/api/v1/platform/support-access/"+grant2+"/revoke",Map.of(),support).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
  post("/api/v1/platform/support-access/"+grant2+"/revoke",Map.of(),support).andExpect(status().isConflict());
  mvc.perform(get("/api/v1/platform/support-access/"+grant2+"/vendor-audit").with(support)).andExpect(status().isForbidden());
  assertThat(audits("SUPPORT_ACCESS_GRANTED")).isEqualTo(2);assertThat(audits("SUPPORT_ACCESS_READ")).isEqualTo(1);assertThat(audits("SUPPORT_ACCESS_REVOKED")).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM platform_audit_events WHERE action='SUPPORT_ACCESS_DENIED' AND outcome='DENIED'",Integer.class)).isEqualTo(4);
  assertThat(db.queryForObject("SELECT details FROM platform_audit_events WHERE action='SUPPORT_ACCESS_READ'",String.class)).contains("entries=1").doesNotContain("order-private-77");
  mvc.perform(get(path).with(support)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
 }
}
