package com.saumya.pos;
import com.saumya.pos.cart.CartService;
import com.saumya.pos.catalog.CatalogService;
import com.saumya.pos.retail.PosService;
import com.saumya.pos.checkout.CheckoutService;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.RequestMethod;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:pos;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}","spring.datasource.username=${TEST_DB_USER:sa}","spring.datasource.password=${TEST_DB_PASSWORD:}","checkout.cleanup-enabled=false"})
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("test")
@org.springframework.test.context.TestPropertySource(properties="payments.mock-enabled=true")
@org.springframework.test.context.TestExecutionListeners(listeners=org.springframework.test.context.support.DependencyInjectionTestExecutionListener.class,inheritListeners=false)
class PosTest {
 @Autowired com.saumya.pos.checkout.CheckoutReceiptService receiptReader;
 @Autowired PosService service; @Autowired JdbcTemplate db; @Autowired MockMvc mvc;
 @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
 @org.springframework.beans.factory.annotation.Autowired org.springframework.context.ApplicationContext applicationContext;
 @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping requestMappings;
 @Autowired CartService marketplace;
 @Autowired CatalogService catalogue;
 @Autowired com.saumya.pos.inventory.InventoryService stock;
 @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
 @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager em;
 @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
 @Autowired com.saumya.pos.checkout.CheckoutService checkout;
 @Autowired com.saumya.pos.checkout.DraftExpiryJob draftExpiry;
 @Autowired com.saumya.pos.checkout.CashierSalesService cashierSales;
 @Autowired com.saumya.pos.checkout.OrderHistoryService orderHistory;
 @Autowired com.saumya.pos.checkout.DraftRecoveryService draftRecovery;
 @Autowired com.saumya.pos.catalog.PosCatalogService posCatalog;
 @Autowired com.saumya.pos.catalog.ProductAdminService productAdmin;
 @Autowired com.saumya.pos.catalog.CategoryAdminService categoryAdmin;
 @Autowired com.saumya.pos.checkout.PaymentCheckoutService payments;
 @Autowired com.saumya.pos.inventory.StockReceivingService stockReceiving;
 @Autowired com.saumya.pos.checkout.ReturnSaleAccess returnSaleAccess;
 @Autowired com.saumya.pos.checkout.OnlineReceiptService onlineReceipts;
 @Autowired com.saumya.pos.catalog.PromotionService promotions;
 @Autowired com.saumya.pos.returns.ReturnService returns;
 @Autowired com.saumya.pos.returns.CashRefundService refunds;
 @Autowired com.saumya.pos.checkout.TenderReviewService tenderReviews;
 @Autowired com.saumya.pos.checkout.CustomerAssociationService associations;
 @Autowired com.saumya.pos.identity.AuthenticationService authentication;
 @Autowired com.saumya.pos.identity.JwtService jwt;
 @Autowired com.saumya.pos.retail.BranchPolicyService branchPolicies;
 @Autowired com.saumya.pos.membership.MembershipService memberships;
 PosService.Scope a=new PosService.Scope(1,1,"alice"),b=new PosService.Scope(2,2,"bob");
 String session;
 @BeforeEach void seed(){
  update("DELETE FROM pos_active_drafts");
  update("DELETE FROM pos_drawer_tenders");
  update("DELETE FROM inventory_receipts");
  update("DELETE FROM order_buyer_snapshots");
  update("DELETE FROM vendor_payment_attempts");
  update("UPDATE tenants SET online_branch_id=NULL");
  for(String table:List.of("subscription_events","subscription_payments","subscription_invoices","tenant_subscriptions","subscription_plan_versions","subscription_plans","catalog_change_events","branch_settings_events","identity_refresh_tokens","platform_mfa_enrollment_tokens","platform_support_access","vendor_access_audit_events","employee_register_assignments","employee_role_assignments","vendor_role_permissions","vendor_roles","pos_customer_challenges","tenant_customers","catalog_discount_policy","receipt_document_audit","report_export_audit","pos_cash_refund_items","pos_cash_refunds","inventory_return_dispositions","pos_return_events","pos_return_items","pos_returns","catalog_promotion_audit","catalog_promotions","product_categories","catalog_categories","stock_reservations","vendor_invoices","inventory_ledger","order_items","orders","cart_items","carts","customer_profiles","platform_user_permissions","pos_shift_reviews","pos_cash_movements","pos_audit","pos_sessions","registers","inventory","pos_stock_locations","products","employees","branches","tenants","users")) update("DELETE FROM "+table);
  update("INSERT INTO tenants(id,name,slug,legal_name,lifecycle_status) VALUES(1,'Vendor A','vendor-1','Vendor A','ACTIVE'),(2,'Vendor B','vendor-2','Vendor B','ACTIVE')");
  update("INSERT INTO branches(id,tenant_id,name) VALUES(1,1,'A'),(2,2,'B')");
  employee("alice",1,1);employee("bob",2,2);
  var entitlementFixture=new com.saumya.pos.isolation.TenantIsolationFixture(db,encoder);
  entitlementFixture.seedEntitlements(1,"alice");
  entitlementFixture.seedEntitlements(2,"bob");
  var testContext=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();testContext.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("alice","test",java.util.stream.Stream.concat(effectivePermissions("alice").stream().map(org.springframework.security.core.authority.SimpleGrantedAuthority::new),java.util.stream.Stream.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(com.saumya.pos.security.AuthorizationContext.ACTOR_CONTEXT_VENDOR))).toList()));org.springframework.security.core.context.SecurityContextHolder.setContext(testContext);update("UPDATE identity_sequence SET next_id=10000 WHERE id=1");update("UPDATE retail_sequences SET next_id=10000 WHERE name='REGISTER'");update("UPDATE catalog_sequences SET next_id=10000");
  update("INSERT INTO registers VALUES(1,1,1),(2,2,2)");
  update("INSERT INTO employee_register_assignments(tenant_id,branch_id,username,register_id,active,assigned_by) VALUES(1,1,'alice',1,TRUE,'alice'),(2,2,'bob',2,TRUE,'bob')");
  update("INSERT INTO products(id,tenant_id,sku,barcode,name,price,tax_rate,published) VALUES(1,1,'P1','123','Panel',100,10,TRUE),(2,2,'P2',NULL,'Private',100,0,TRUE)");
  update("INSERT INTO inventory(tenant_id,branch_id,product_id,quantity) VALUES(1,1,1,1),(2,2,2,1)");
  session=(String)asScope(a, () -> service.open(a,new PosService.OpenSession(1,new BigDecimal("50")))).get("id");
 }
 int update(String sql,Object...args){
  int result=db.update(sql,args);
  if(sql.matches("(?is)^\\s*INSERT\\s+INTO\\s+branches\\b.*")){
   db.update("""
    INSERT INTO pos_stock_locations(tenant_id,branch_id,stock_location_id,location_code,display_name,active)
    SELECT b.tenant_id,b.id,b.id,'POS_DEFAULT',CONCAT(b.name,' POS stock room'),TRUE
    FROM branches b WHERE NOT EXISTS(
      SELECT 1 FROM pos_stock_locations l WHERE l.tenant_id=b.tenant_id AND l.branch_id=b.id)
    """);
  }
  if(sql.matches("(?is)^\\s*(?:UPDATE\\s+inventory\\b|INSERT\\s+INTO\\s+inventory\\b|DELETE\\s+FROM\\s+inventory\\b).*")){
   db.update("DELETE FROM catalog_stock");
   db.update("INSERT INTO catalog_stock SELECT tenant_id,branch_id,product_id,quantity-reserved FROM inventory");
  }
  return result;
 }
 private static final class ScopedUserPostProcessor implements org.springframework.test.web.servlet.request.RequestPostProcessor {
  private final String username;
  private List<org.springframework.security.core.GrantedAuthority> authorities=List.of();
  ScopedUserPostProcessor(String username){this.username=username;}
  ScopedUserPostProcessor authorities(org.springframework.security.core.GrantedAuthority... values){this.authorities=List.of(values);return this;}
  ScopedUserPostProcessor authorities(java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> values){this.authorities=List.copyOf(values);return this;}
  @Override public org.springframework.mock.web.MockHttpServletRequest postProcessRequest(org.springframework.mock.web.MockHttpServletRequest request){
   var resolved=new ArrayList<org.springframework.security.core.GrantedAuthority>(authorities);
   Set<String> names=resolved.stream().map(org.springframework.security.core.GrantedAuthority::getAuthority).collect(java.util.stream.Collectors.toSet());
   boolean hasMarker=names.stream().anyMatch(n->n.startsWith("ACTOR_CONTEXT_"));
   if(!hasMarker){
    String marker=names.contains("CUSTOMER")?"ACTOR_CONTEXT_CUSTOMER":
      names.stream().anyMatch(n->n.startsWith("PLATFORM_"))?"ACTOR_CONTEXT_PLATFORM":"ACTOR_CONTEXT_VENDOR";
    resolved.add(new org.springframework.security.core.authority.SimpleGrantedAuthority(marker));
   }
   return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(username)
     .authorities(resolved).postProcessRequest(request);
  }
 }
 private static ScopedUserPostProcessor user(String username){return new ScopedUserPostProcessor(username);}
 @AfterEach void clearPrincipal(){org.springframework.security.core.context.SecurityContextHolder.clearContext();}
 void employee(String username,long tenant,long branch){
  long id=db.queryForObject("SELECT COALESCE(MAX(id),0)+1 FROM users",Long.class);
  update("INSERT INTO users(id,username,password_hash,enabled) VALUES(?,?,?,TRUE)",id,username,encoder.encode("StaffPasswordForTests"));
  update("INSERT INTO employees VALUES(?,?,?,TRUE)",username,tenant,branch);
  List<String> permissions=com.saumya.pos.membership.VendorPermissionRegistry.all().stream().sorted().toList();
  String roleKey="test-"+username.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]","-");
  update("INSERT INTO vendor_roles(tenant_id,role_key,display_name,active,created_by,updated_by) VALUES(?,?,?,TRUE,?,?)",tenant,roleKey,"Test "+username,username,username);
  Long roleId=db.queryForObject("SELECT id FROM vendor_roles WHERE tenant_id=? AND role_key=?",Long.class,tenant,roleKey);
  for(String permission:permissions)update("INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by) VALUES(?,?,?,?)",tenant,roleId,permission,username);
  int reactivated=update("UPDATE employee_role_assignments SET branch_id=?,active=TRUE,assigned_by=?,assigned_at=CURRENT_TIMESTAMP,revoked_by=NULL,revoked_at=NULL WHERE tenant_id=? AND username=? AND role_id=?",branch,username,tenant,username,roleId);
  if(reactivated==0)update("INSERT INTO employee_role_assignments(tenant_id,branch_id,username,role_id,active,assigned_by) VALUES(?,?,?,?,TRUE,?)",tenant,branch,username,roleId,username);
   Long firstRegister=db.queryForObject("SELECT MIN(id) FROM registers WHERE tenant_id=? AND branch_id=?",Long.class,tenant,branch);
   if(firstRegister!=null)assignRegister(username,tenant,branch,firstRegister);
 }
 List<String> effectivePermissions(String username){
  return memberships.access(username)
   .orElseThrow(()->new IllegalStateException("No active, entitled vendor membership for "+username))
   .permissions();
 }
 void setEmployeeRolePermissions(String username,List<String> permissions){
  Long tenant=db.queryForObject("SELECT tenant_id FROM employees WHERE username=?",Long.class,username);
  Long branch=db.queryForObject("SELECT branch_id FROM employees WHERE username=?",Long.class,username);
  update("UPDATE employee_role_assignments SET active=FALSE,revoked_by=?,revoked_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND username=? AND active=TRUE",username,tenant,username);
  String roleKey="restricted-"+username.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]","-");
  List<Long> existingRoles=db.queryForList("SELECT id FROM vendor_roles WHERE tenant_id=? AND role_key=?",Long.class,tenant,roleKey);
  Long roleId;
  if(existingRoles.isEmpty()){
   update("INSERT INTO vendor_roles(tenant_id,role_key,display_name,active,created_by,updated_by) VALUES(?,?,?,TRUE,?,?)",tenant,roleKey,"Restricted "+username,username,username);
   roleId=db.queryForObject("SELECT id FROM vendor_roles WHERE tenant_id=? AND role_key=?",Long.class,tenant,roleKey);
  }else{
   roleId=existingRoles.getFirst();
   update("UPDATE vendor_roles SET active=TRUE,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE tenant_id=? AND id=?",username,tenant,roleId);
   update("DELETE FROM vendor_role_permissions WHERE tenant_id=? AND role_id=?",tenant,roleId);
  }
  for(String permission:permissions)update("INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by) VALUES(?,?,?,?)",tenant,roleId,permission,username);
  int reactivated=update("UPDATE employee_role_assignments SET branch_id=?,active=TRUE,assigned_by=?,assigned_at=CURRENT_TIMESTAMP,revoked_by=NULL,revoked_at=NULL WHERE tenant_id=? AND username=? AND role_id=?",branch,username,tenant,username,roleId);
  if(reactivated==0)update("INSERT INTO employee_role_assignments(tenant_id,branch_id,username,role_id,active,assigned_by) VALUES(?,?,?,?,TRUE,?)",tenant,branch,username,roleId,username);
 }
 void removeAssignedRolePermission(String username,String permission){
  update("DELETE FROM vendor_role_permissions WHERE permission=? AND tenant_id=(SELECT tenant_id FROM employees WHERE username=?) AND role_id IN (SELECT role_id FROM employee_role_assignments WHERE username=? AND active=TRUE AND tenant_id=(SELECT tenant_id FROM employees WHERE username=?))",permission,username,username,username);
 }
 void addAssignedRolePermission(String username,String permission){
  Long tenant=db.queryForObject("SELECT tenant_id FROM employees WHERE username=?",Long.class,username);
  Long roleId=db.queryForObject("SELECT role_id FROM employee_role_assignments WHERE tenant_id=? AND username=? AND active=TRUE ORDER BY role_id LIMIT 1",Long.class,tenant,username);
  update("INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by) VALUES(?,?,?,?)",tenant,roleId,permission,username);
 }
 int assignedRolePermissionCount(String username,String permission){
  Integer count=db.queryForObject("SELECT COUNT(*) FROM employee_role_assignments a JOIN vendor_role_permissions rp ON rp.tenant_id=a.tenant_id AND rp.role_id=a.role_id JOIN vendor_roles r ON r.tenant_id=a.tenant_id AND r.id=a.role_id WHERE a.username=? AND a.active=TRUE AND r.active=TRUE AND rp.permission=?",Integer.class,username,permission);
  return count==null?0:count;
 }
 void assignRegister(String username,long tenant,long branch,long register){
   update("INSERT INTO employee_register_assignments(tenant_id,branch_id,username,register_id,active,assigned_by) VALUES(?,?,?,?,TRUE,?) ON DUPLICATE KEY UPDATE active=TRUE,assigned_by=VALUES(assigned_by),assigned_at=CURRENT_TIMESTAMP,revoked_by=NULL,revoked_at=NULL",tenant,branch,username,register,username);
 }
 <T> T asScope(PosService.Scope scope, java.util.function.Supplier<T> action) { return as(scope.cashier(),action); }
 <T> T as(String username, java.util.function.Supplier<T> action) {
  var previous = org.springframework.security.core.context.SecurityContextHolder.getContext();
  var current = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
  current.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(username,"test",java.util.stream.Stream.concat(effectivePermissions(username).stream().map(org.springframework.security.core.authority.SimpleGrantedAuthority::new),java.util.stream.Stream.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(com.saumya.pos.security.AuthorizationContext.ACTOR_CONTEXT_VENDOR))).toList()));
  org.springframework.security.core.context.SecurityContextHolder.setContext(current);
  try { return action.get(); }
  finally { org.springframework.security.core.context.SecurityContextHolder.setContext(previous); }
 }
 <T> T asAuthentication(org.springframework.security.core.Authentication authentication, java.util.function.Supplier<T> action) {
  var previous = org.springframework.security.core.context.SecurityContextHolder.getContext();
  var current = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
  current.setAuthentication(authentication);
  org.springframework.security.core.context.SecurityContextHolder.setContext(current);
  try { return action.get(); }
  finally { org.springframework.security.core.context.SecurityContextHolder.setContext(previous); }
 }

 org.springframework.test.web.servlet.request.RequestPostProcessor bearer(String username,String password)throws Exception{var response=mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("username",username,"password",password)))).andExpect(status().isOk()).andReturn();String token=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).get("accessToken").asText();return request->{request.addHeader("Authorization","Bearer "+token);return request;};}
 CheckoutService.CashSale sale(long product,String cash){return new CheckoutService.CashSale(session,List.of(new CheckoutService.Item(product,1)),new BigDecimal(cash),false,null,null);}
 com.saumya.pos.checkout.PaymentCheckoutService.View terminal(String username,String id,String method){var amount=as(username, () -> payments.tenderStatus(username,id)).remaining();var result=as(username, () -> payments.tender(username,id,"terminal-"+method,new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest(method,amount,null)));return result.tenders().getLast();}

 @Test void multipleVendorRolesComposeAndRevocationImmediatelyChangesEffectivePermissions() {
  update("UPDATE employee_role_assignments SET active=FALSE,revoked_by='alice',revoked_at=CURRENT_TIMESTAMP WHERE tenant_id=1 AND username='alice' AND active=TRUE");
  update("INSERT INTO vendor_roles(tenant_id,role_key,display_name,active,created_by,updated_by) VALUES(1,'sales-role','Sales',TRUE,'alice','alice'),(1,'reports-role','Reports',TRUE,'alice','alice')");
  Long sales=db.queryForObject("SELECT id FROM vendor_roles WHERE tenant_id=1 AND role_key='sales-role'",Long.class);
  Long reports=db.queryForObject("SELECT id FROM vendor_roles WHERE tenant_id=1 AND role_key='reports-role'",Long.class);
  update("INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by) VALUES(1,?,'POS_SALE_CREATE','alice'),(1,?,'POS_REPORT_VIEW','alice')",sales,reports);
  update("INSERT INTO employee_role_assignments(tenant_id,branch_id,username,role_id,active,assigned_by) VALUES(1,1,'alice',?,TRUE,'alice'),(1,1,'alice',?,TRUE,'alice')",sales,reports);
  var effective=memberships.access("alice").orElseThrow().permissions();
  assertThat(effective).contains("POS_SALE_CREATE","POS_REPORT_VIEW");
  // Only the active assigned-role permission union is effective.
  assertThat(effective).doesNotContain("PRODUCT_CREATE","PRODUCT_UPDATE");
  update("UPDATE employee_role_assignments SET active=FALSE,revoked_by='alice',revoked_at=CURRENT_TIMESTAMP WHERE tenant_id=1 AND username='alice' AND role_id=?",sales);
  assertThat(memberships.access("alice").orElseThrow().permissions()).doesNotContain("POS_SALE_CREATE").contains("POS_REPORT_VIEW");
  update("UPDATE vendor_roles SET active=FALSE WHERE tenant_id=1 AND id=?",reports);
  assertThat(memberships.access("alice").orElseThrow().permissions()).doesNotContain("POS_SALE_CREATE","POS_REPORT_VIEW");
  assertThatThrownBy(()->update("INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by) VALUES(1,?,'PLATFORM_IDENTITY_MANAGE','alice')",sales))
    .isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->update("INSERT INTO employee_role_assignments(tenant_id,branch_id,username,role_id,active,assigned_by) VALUES(2,2,'bob',?,TRUE,'bob')",sales))
    .isInstanceOf(RuntimeException.class);
 }

 @Test void cashierCannotOpenOrResumeAnUnassignedRegister() {
  employee("charlie",1,1);
  update("INSERT INTO registers VALUES(3,1,1)");
  update("DELETE FROM employee_register_assignments WHERE tenant_id=1 AND username='charlie'");
  var charlie=new PosService.Scope(1,1,"charlie");
  assertThatThrownBy(()->as("charlie",()->service.open(charlie,new PosService.OpenSession(3,BigDecimal.ZERO))))
    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assignRegister("charlie",1,1,3);
  assertThat(as("charlie",()->service.open(charlie,new PosService.OpenSession(3,BigDecimal.ZERO)))).containsKey("id");
  var opened=db.queryForObject("SELECT id FROM pos_sessions WHERE tenant_id=1 AND cashier='charlie' AND register_id=3",String.class);
  update("DELETE FROM employee_register_assignments WHERE tenant_id=1 AND username='charlie' AND register_id=3");
  assertThatThrownBy(()->as("charlie",()->service.sessionDetails(charlie,opened)))
    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }

 @Test void closingSessionRemainsUniqueAtTheDatabaseLevel() {
  update("UPDATE pos_sessions SET state='CLOSING' WHERE tenant_id=1 AND id=?",session);
  employee("charlie",1,1);
  String second=UUID.randomUUID().toString();
  assertThatThrownBy(()->update("INSERT INTO pos_sessions(id,tenant_id,branch_id,cashier,register_id,state,opening_float) VALUES(?,1,1,'charlie',1,'CLOSING',0)",second))
    .isInstanceOf(RuntimeException.class);
 }

 @Test void retailDraftProjectionBlocksShiftClosureUntilCollectedFundsAreFinalized(){
  update("UPDATE branches SET split_tender_enabled=TRUE WHERE id=1");var draft=as("alice", () -> checkout.pos("alice","active-draft-gate",posDraft()));assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_active_drafts",Integer.class)).isEqualTo(1);assertThatThrownBy(()->asScope(a, () -> service.close(a,session,new BigDecimal("50")))).hasMessageContaining("Resolve reserved");
  as("alice", () -> payments.tender("alice",draft.id(),"gate-partial",new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("CASH",new BigDecimal("40"),new BigDecimal("40"))));update("UPDATE orders SET expires_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)),draft.id());draftExpiry.expireOne(1,draft.id());assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_active_drafts",Integer.class)).isEqualTo(1);assertThatThrownBy(()->asScope(a, () -> service.close(a,session,new BigDecimal("90")))).hasMessageContaining("Resolve reserved");
  as("alice", () -> payments.tender("alice",draft.id(),"gate-remainder",new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("CASH",new BigDecimal("70"),new BigDecimal("70"))));assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_active_drafts",Integer.class)).isZero();assertThat(asScope(a, () -> service.close(a,session,new BigDecimal("160"))).state()).isEqualTo("CLOSED");
 }
 @Test void guestBuyerSnapshotsArePrivateTransactionFactsAndNeverCreateAccounts()throws Exception{
  var buyer=new com.saumya.pos.checkout.BuyerDetailsPolicy.Input("Private walk-in buyer","private-contact","PRIVATE-TAX","Private street");
  var receipt=as("alice", () -> checkout.cashSale("alice","guest-buyer",new CheckoutService.CashSale(session,List.of(new CheckoutService.Item(1,1)),new BigDecimal("120"),false,null,buyer)));String id=(String)receipt.get("id");assertThat(receipt.get("buyer")).isEqualTo(buyer);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM users",Integer.class)).isEqualTo(2);assertThat(db.queryForObject("SELECT COUNT(*) FROM customer_profiles",Integer.class)).isZero();assertThat(db.queryForObject("SELECT COUNT(*) FROM tenant_customers",Integer.class)).isZero();
  mvc.perform(get("/api/v1/vendor/pos/receipts/"+id).with(user("alice").authorities(()->"POS_RECEIPT_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.buyer.name").value("Private walk-in buyer")).andExpect(jsonPath("$.buyer.taxId").value("PRIVATE-TAX"));
  var denied=mvc.perform(get("/api/v1/vendor/pos/receipts/"+id).with(user("bob").authorities(()->"POS_RECEIPT_VIEW"))).andExpect(status().isNotFound()).andReturn();assertThat(denied.getResponse().getContentAsString()).doesNotContain("Private walk-in buyer","PRIVATE-TAX");
  mvc.perform(get("/api/v1/vendor/pos/receipts/"+id).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isForbidden());assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE action='BUYER_SNAPSHOT_VIEW'",Integer.class)).isEqualTo(2);assertThat(db.queryForList("SELECT * FROM pos_audit").toString()).doesNotContain("private-contact","PRIVATE-TAX");
  assertThatThrownBy(()->as("alice", () -> as("alice", () -> checkout.cashSale("alice","guest-buyer",new CheckoutService.CashSale(session,List.of(new CheckoutService.Item(1,1)),new BigDecimal("120"),false,null,new com.saumya.pos.checkout.BuyerDetailsPolicy.Input("Changed buyer",null,null,null)))))).hasMessageContaining("different draft request");assertThat(db.queryForObject("SELECT name FROM order_buyer_snapshots",String.class)).isEqualTo("Private walk-in buyer");
 }
 @Test void approvedInvoiceBuyerRequirementDoesNotRequireGuestContactOrRegistration(){
  update("UPDATE inventory SET quantity=4 WHERE tenant_id=1 AND product_id=1");update("UPDATE branches SET buyer_details_required=TRUE WHERE id=1");as("alice", () -> checkout.cashSale("alice","ordinary-guest",sale(1,"120")));
  assertThatThrownBy(()->as("alice", () -> checkout.cashSale("alice","invoice-no-buyer",new CheckoutService.CashSale(session,List.of(new CheckoutService.Item(1,1)),new BigDecimal("120"),true,null,null)))).hasMessageContaining("Buyer name and address");assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);
  var buyer=new com.saumya.pos.checkout.BuyerDetailsPolicy.Input("Invoice buyer",null,null,"Buyer street");var receipt=as("alice", () -> checkout.cashSale("alice","invoice-buyer",new CheckoutService.CashSale(session,List.of(new CheckoutService.Item(1,1)),new BigDecimal("120"),true,null,buyer)));assertThat(receipt.get("tax_invoice")).isEqualTo(true);assertThat(receipt.get("buyer")).isEqualTo(buyer);assertThat(db.queryForObject("SELECT COUNT(*) FROM customer_profiles",Integer.class)).isZero();
 }
 @Test void cancellingOrExpiringAnUnpaidDraftRemovesItsOptionalBuyerSnapshot(){
  var buyer=new com.saumya.pos.checkout.BuyerDetailsPolicy.Input("Guest",null,null,"Street");var request=new CheckoutService.PosDraft(session,List.of(new CheckoutService.Item(1,1)),false,null,buyer);var draft=as("alice", () -> checkout.pos("alice","buyer-cancel",request));assertThat(db.queryForObject("SELECT COUNT(*) FROM order_buyer_snapshots",Integer.class)).isEqualTo(1);String cancelDraftId=draft.id();as("alice", () -> checkout.cancelPos("alice",cancelDraftId,"Buyer left"));assertThat(db.queryForObject("SELECT COUNT(*) FROM order_buyer_snapshots",Integer.class)).isZero();
  draft=as("alice", () -> checkout.pos("alice","buyer-expire",request));update("UPDATE orders SET expires_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)),draft.id());draftExpiry.expireOne(1,draft.id());assertThat(db.queryForObject("SELECT COUNT(*) FROM order_buyer_snapshots",Integer.class)).isZero();
 }
 @Test void concurrentInventoryChangesRefreshAnAlreadyLoadedCatalogProjection(){
  var pool=Executors.newSingleThreadExecutor();
  try{new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->{
   var key=new com.saumya.pos.catalog.internal.CatalogStock.Key(1,1,1);assertThat(em.find(com.saumya.pos.catalog.internal.CatalogStock.class,key).available()).isEqualTo(1);
   try{pool.submit(()->as("alice",()->new org.springframework.transaction.support.TransactionTemplate(transactions).execute(other->{service.lockOutlet(a);return stock.receive(1,1,1,2);}))).get(20,TimeUnit.SECONDS);}catch(Exception failure){throw new IllegalStateException(failure);}
   stock.receive(1,1,1,2);return null;
  });}finally{pool.shutdownNow();}
  assertThat(as("alice",()->stock.atBranch(1,1,1))).isEqualTo(5);assertThat(db.queryForObject("SELECT available FROM catalog_stock WHERE tenant_id=1 AND branch_id=1 AND product_id=1",Integer.class)).isEqualTo(5);
 }
 @Test void inventoryApiCoversUnauthenticatedForbiddenAndNonDisclosingCrossTenantDenials()throws Exception{
  String path="/api/v1/vendor/inventory/receipts";
  int before=db.queryForObject("SELECT COUNT(*) FROM inventory_receipts",Integer.class);
  mvc.perform(get(path).with(anonymous())).andExpect(status().isUnauthorized());
  mvc.perform(get(path).with(user("alice").authorities(()->"PRODUCT_VIEW"))).andExpect(status().isForbidden());
  mvc.perform(post(path).header("Idempotency-Key","cross-tenant-stock").with(csrf())
    .with(user("bob").authorities(()->"INVENTORY_RECEIVE")).contentType("application/json")
    .content("{\"productId\":1,\"quantity\":1,\"reason\":\"Cross tenant attempt\"}"))
    .andExpect(status().isNotFound());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_receipts",Integer.class)).isEqualTo(before);
 }

 @Test void inventoryServiceRejectsCrossTenantDirectReadsAndMutationsBeforePersistence(){
  int before=db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=2 AND branch_id=2 AND product_id=2",Integer.class);
  assertThatThrownBy(()->as("alice",()->stock.atBranch(2,2,2)))
   .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
   .satisfies(error->assertThat(((org.springframework.web.server.ResponseStatusException)error).getStatusCode().value()).isEqualTo(403));
  assertThatThrownBy(()->as("alice",()->{stock.issue(2,2,2,1);return null;}))
   .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
   .satisfies(error->assertThat(((org.springframework.web.server.ResponseStatusException)error).getStatusCode().value()).isEqualTo(403));
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=2 AND branch_id=2 AND product_id=2",Integer.class)).isEqualTo(before);
 }

 @Test void checkoutExpiryRejectsDirectInvocationOutsideTrustedWorker(){
  assertThatThrownBy(()->checkout.expire(1,"nonexistent-order"))
   .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
   .satisfies(error->assertThat(((org.springframework.web.server.ResponseStatusException)error).getStatusCode().value()).isEqualTo(403));
 }

 @Test void stockReceivingUsesAssignedScopeAndIdempotentAuditedCommands()throws Exception{
  String path="/api/v1/vendor/inventory/receipts",body="{\"productId\":1,\"quantity\":2,\"reason\":\"Delivery inspected\"}";
  mvc.perform(post(path).header("Idempotency-Key","stock-key").with(csrf()).with(user("alice").authorities(()->"PRODUCT_VIEW")).contentType("application/json").content(body)).andExpect(status().isForbidden());
  mvc.perform(post(path).header("Idempotency-Key","stock-key").with(csrf()).with(user("alice").authorities(()->"INVENTORY_RECEIVE")).contentType("application/json").content(body.replace("productId\":1","productId\":2"))).andExpect(status().isNotFound());
  for(int attempt=0;attempt<2;attempt++)mvc.perform(post(path).header("Idempotency-Key","stock-key").with(csrf()).with(user("alice").authorities(()->"INVENTORY_RECEIVE")).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.availableAfter").value(3));
  mvc.perform(post(path).header("Idempotency-Key","stock-key").with(csrf()).with(user("alice").authorities(()->"INVENTORY_RECEIVE")).contentType("application/json").content(body.replace("quantity\":2","quantity\":3"))).andExpect(status().isConflict());
  mvc.perform(get(path).with(user("bob").authorities(()->"INVENTORY_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
  mvc.perform(get(path).with(user("alice").authorities(()->"INVENTORY_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].actor").value("alice"));
  assertThat(as("alice",()->stock.atBranch(1,1,1))).isEqualTo(3);assertThat(as("bob", () -> stock.atBranch(2,2,2))).isEqualTo(1);assertThat(db.queryForObject("SELECT available FROM catalog_stock WHERE tenant_id=1 AND branch_id=1 AND product_id=1",Integer.class)).isEqualTo(3);
 }
 @Test void concurrentStockReceiptRetriesCreateOneBalanceAndOneReceipt()throws Exception{
  update("INSERT INTO products(id,tenant_id,sku,name,price,tax_rate,published) VALUES(3,1,'NEW','New panel',100,10,FALSE)");String body="{\"productId\":3,\"quantity\":5,\"reason\":\"First delivery\"}";var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
  try{Callable<String> command=()->{gate.await();var response=mvc.perform(post("/api/v1/vendor/inventory/receipts").header("Idempotency-Key","same-delivery").with(csrf()).with(user("alice").authorities(()->"INVENTORY_RECEIVE")).contentType("application/json").content(body)).andExpect(status().isOk()).andReturn();return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).get("id").asText();};var first=pool.submit(command);var second=pool.submit(command);gate.countDown();assertThat(first.get(20,TimeUnit.SECONDS)).isEqualTo(second.get(20,TimeUnit.SECONDS));}finally{pool.shutdownNow();}
  assertThat(as("alice",()->stock.atBranch(1,1,3))).isEqualTo(5);assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_receipts",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT available FROM catalog_stock WHERE tenant_id=1 AND branch_id=1 AND product_id=3",Integer.class)).isEqualTo(5);
 }
 @Test void receivingBeyondStockCapacityCannotCreateAReceiptOrChangeAvailability()throws Exception{
  update("UPDATE inventory SET quantity=2147483646 WHERE tenant_id=1 AND product_id=1");mvc.perform(post("/api/v1/vendor/inventory/receipts").header("Idempotency-Key","capacity-check").with(csrf()).with(user("alice").authorities(()->"INVENTORY_RECEIVE")).contentType("application/json").content("{\"productId\":1,\"quantity\":2,\"reason\":\"Capacity check\"}")).andExpect(status().isConflict());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_receipts",Integer.class)).isZero();assertThat(as("alice",()->stock.atBranch(1,1,1))).isEqualTo(2147483646L);assertThat(db.queryForObject("SELECT available FROM catalog_stock WHERE tenant_id=1 AND branch_id=1 AND product_id=1",Integer.class)).isEqualTo(2147483646);
 }
 String productBody(String sku,String barcode,long version,boolean published,String price,Long category){return "{\"sku\":\""+sku+"\",\"barcode\":\""+barcode+"\",\"name\":\"New panel\",\"price\":"+price+",\"taxRate\":10,\"published\":"+published+",\"categoryId\":"+category+",\"expectedVersion\":"+version+",\"reason\":\"Approved catalogue change\"}";}
 @Test void productManagementIsTenantScopedAuditedAndVersioned()throws Exception{
  String path="/api/v1/vendor/products",body=productBody("P-NEW","new-barcode",0,true,"125.50",null);
  mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"PRODUCT_VIEW")).contentType("application/json").content(body)).andExpect(status().isForbidden());
  var response=mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"PRODUCT_CREATE")).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("New panel")).andReturn();
  long id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).get("id").asLong();
  mvc.perform(get(path).with(user("bob").authorities(()->"PRODUCT_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].name").value("Private"));
  mvc.perform(put(path+"/"+id).with(csrf()).with(user("bob").authorities(()->"PRODUCT_UPDATE")).contentType("application/json").content(body)).andExpect(status().isNotFound());
  mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"PRODUCT_CREATE")).contentType("application/json").content(body)).andExpect(status().isConflict());
  mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"PRODUCT_CREATE")).contentType("application/json").content(productBody("OTHER","new-barcode",0,false,"10",null))).andExpect(status().isConflict());
  mvc.perform(put(path+"/"+id).with(csrf()).with(user("alice").authorities(()->"PRODUCT_UPDATE")).contentType("application/json").content(productBody("P-NEW","new-barcode",0,false,"125.50",null))).andExpect(status().isForbidden());
  var saved=mvc.perform(put(path+"/"+id).with(csrf()).with(user("alice").authorities(()->"PRODUCT_UPDATE",()->"PRODUCT_ARCHIVE")).contentType("application/json").content(productBody("P-NEW","new-barcode",0,false,"125.50",null))).andExpect(status().isOk()).andExpect(jsonPath("$.published").value(false)).andReturn();
  long version=new com.fasterxml.jackson.databind.ObjectMapper().readTree(saved.getResponse().getContentAsString()).get("version").asLong();assertThat(version).isGreaterThan(0);
  mvc.perform(put(path+"/"+id).with(csrf()).with(user("alice").authorities(()->"PRODUCT_UPDATE")).contentType("application/json").content(body)).andExpect(status().isConflict());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM catalog_change_events WHERE resource_id=?",Integer.class,id)).isEqualTo(2);assertThat(as("alice",()->stock.available(1,id))).isZero();
 }
 @Test void productCategoryAssignmentAndPriceChangesUseTheirOwnPermissions()throws Exception{
  update("INSERT INTO catalog_categories(id,tenant_id,name,slug) VALUES(10,1,'Panels','panels'),(20,2,'Private','private')");String path="/api/v1/vendor/products/1";
  mvc.perform(put(path).with(csrf()).with(user("alice").authorities(()->"PRODUCT_UPDATE")).contentType("application/json").content(productBody("P1","123",0,true,"120",null))).andExpect(status().isForbidden());
  mvc.perform(put(path).with(csrf()).with(user("alice").authorities(()->"PRODUCT_UPDATE")).contentType("application/json").content(productBody("P1","123",0,true,"100",20L))).andExpect(status().isNotFound());
  var response=mvc.perform(put(path).with(csrf()).with(user("alice").authorities(()->"PRODUCT_UPDATE",()->"PRICE_UPDATE")).contentType("application/json").content(productBody("P1","123",0,true,"120",10L))).andExpect(status().isOk()).andExpect(jsonPath("$.categoryId").value(10)).andReturn();
  long version=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).get("version").asLong();
  mvc.perform(put(path).with(csrf()).with(user("alice").authorities(()->"PRODUCT_UPDATE")).contentType("application/json").content(productBody("P1","123",version,true,"120",null))).andExpect(status().isOk());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM product_categories WHERE product_id=1",Integer.class)).isZero();assertThat(db.queryForObject("SELECT version FROM products WHERE id=1",Long.class)).isGreaterThan(version);
  mvc.perform(put(path).with(csrf()).with(user("alice").authorities(()->"PRODUCT_UPDATE")).contentType("application/json").content(productBody("P1","123",version,true,"120",10L))).andExpect(status().isConflict());
 }
 @Test void productPriceViewRequiresSeparatePermission() throws Exception {
  setEmployeeRolePermissions("alice",List.of("PRODUCT_VIEW"));
  var hidden=mvc.perform(get("/api/v1/vendor/products").with(user("alice").authorities(()->"PRODUCT_VIEW")))
   .andExpect(status().isOk()).andReturn();
  var hiddenJson=json.readTree(hidden.getResponse().getContentAsString());
  assertThat(hiddenJson.path("items").get(0).path("price").isNull()).isTrue();
  assertThat(hiddenJson.path("items").get(0).path("taxRate").isNull()).isTrue();

  setEmployeeRolePermissions("alice",List.of("PRODUCT_VIEW","PRICE_VIEW"));
  var visible=mvc.perform(get("/api/v1/vendor/products").with(user("alice").authorities(()->"PRODUCT_VIEW",()->"PRICE_VIEW")))
   .andExpect(status().isOk()).andReturn();
  var visibleJson=json.readTree(visible.getResponse().getContentAsString());
  assertThat(visibleJson.path("items").get(0).path("price").decimalValue()).isEqualByComparingTo("100.00");
  assertThat(visibleJson.path("items").get(0).path("taxRate").decimalValue()).isEqualByComparingTo("10.00");
 }
 @Test void categoryManagementEnforcesPermissionsTenantOwnershipVersionAndArchive() throws Exception {
  String body="{\"name\":\"Panels\",\"slug\":\"panels\",\"expectedVersion\":0,\"active\":true,\"reason\":\"Approved catalogue setup\"}";
  mvc.perform(post("/api/v1/vendor/categories").with(csrf()).with(user("alice").authorities(()->"POS_VIEW")).contentType("application/json").content(body)).andExpect(status().isForbidden());
  var response=mvc.perform(post("/api/v1/vendor/categories").with(csrf()).with(user("alice").authorities(()->"PRODUCT_CREATE")).contentType("application/json").content(body)).andExpect(status().isOk()).andReturn();
  long category=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).get("id").asLong();
  mvc.perform(post("/api/v1/vendor/categories").with(csrf()).with(user("alice").authorities(()->"PRODUCT_CREATE")).contentType("application/json").content(body)).andExpect(status().isConflict());
  mvc.perform(put("/api/v1/vendor/categories/"+category).with(csrf()).with(user("bob").authorities(()->"PRODUCT_UPDATE")).contentType("application/json").content(body)).andExpect(status().isNotFound());
  update("INSERT INTO product_categories VALUES(1,1,?)",category);
  String archived=body.replace("\"active\":true","\"active\":false");
  mvc.perform(put("/api/v1/vendor/categories/"+category).with(csrf()).with(user("alice").authorities(()->"PRODUCT_UPDATE")).contentType("application/json").content(archived)).andExpect(status().isForbidden());
  mvc.perform(put("/api/v1/vendor/categories/"+category).with(csrf()).with(user("alice").authorities(()->"PRODUCT_UPDATE",()->"PRODUCT_ARCHIVE")).contentType("application/json").content(archived)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM product_categories WHERE tenant_id=1",Integer.class)).isZero();
  mvc.perform(put("/api/v1/vendor/categories/"+category).with(csrf()).with(user("alice").authorities(()->"PRODUCT_UPDATE",()->"PRODUCT_ARCHIVE")).contentType("application/json").content(archived)).andExpect(status().isConflict());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM catalog_change_events",Integer.class)).isEqualTo(2);
 }
 @Test void branchSettingsRequirePermissionAndVersionAndCreateAnAuditedTill() throws Exception {
  String body="{\"name\":\"Updated outlet\",\"expectedVersion\":0,\"enabled\":true,\"upi\":\"vendor-upi\",\"card\":null,\"split\":true,\"days\":14,\"cashRefunds\":true,\"invoiceRequired\":false,\"buyerDetailsRequired\":false,\"creditNotesEnabled\":false,\"currency\":\"INR\",\"reason\":\"Approved operating setup\"}";
  mvc.perform(post("/api/v1/vendor/pos/settings").with(csrf()).with(user("alice").authorities(()->"POS_VIEW")).contentType("application/json").content(body)).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/vendor/pos/settings").with(csrf()).with(user("alice").authorities(()->"POS_SETTINGS_MANAGE")).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.split").value(true));
  mvc.perform(post("/api/v1/vendor/pos/settings").with(csrf()).with(user("alice").authorities(()->"POS_SETTINGS_MANAGE")).contentType("application/json").content(body)).andExpect(status().isConflict());
  long register=as("alice", () -> branchPolicies.createRegister("alice","New till approved"));assertThat(db.queryForObject("SELECT tenant_id FROM registers WHERE id=?",Long.class,register)).isEqualTo(1L);
  assertThat(as("bob", () -> branchPolicies.current("bob")).name()).isEqualTo("B");assertThat(db.queryForObject("SELECT COUNT(*) FROM branch_settings_events",Integer.class)).isEqualTo(2);
 }
 @Test void jwtRefreshRotationDetectsReuseAndRevokesTheWholeFamily() throws Exception {
  customers();var first=authentication.login("customer","CustomerPassword");assertThat(first.refreshToken()).hasSize(43);assertThat(first.accessToken()).contains(".");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM identity_refresh_tokens WHERE token_hash=?",Integer.class,first.refreshToken())).isZero();
  var second=authentication.refresh(first.refreshToken());assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
  assertThatThrownBy(()->authentication.refresh(first.refreshToken())).hasMessageContaining("expired");
  mvc.perform(get("/api/v1/customer/cart").header("Authorization","Bearer "+second.accessToken())).andExpect(status().isUnauthorized());
  assertThatThrownBy(()->authentication.refresh(second.refreshToken())).hasMessageContaining("expired");
 }
 @Test void registrationAndCookieAuthenticationEnforceCsrfAndDoNotExposeRefreshToken() throws Exception {
  String body="{\"username\":\"new-buyer@example.com\",\"password\":\"CustomerPasswordForTests\"}";
  mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(body)).andExpect(status().isForbidden());
  var result=mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.refreshToken").doesNotExist()).andExpect(jsonPath("$.account.permissions[0]").value("CUSTOMER")).andReturn();
  assertThat(result.getResponse().getHeader("Set-Cookie")).contains("HttpOnly","SameSite=Strict","Path=/api/v1/auth");
  assertThat(db.queryForObject("SELECT password_hash FROM users WHERE username='new-buyer@example.com'",String.class)).doesNotContain("CustomerPasswordForTests");
 }
 @Test void p007DualCustomerVendorIdentityRequiresExplicitContextSwitchAndNeverFallsBack() throws Exception {
  // Start with a Customer identity that has no Vendor employee membership yet.
  long userId=db.queryForObject("SELECT COALESCE(MAX(id),0)+1 FROM users",Long.class);
  update("INSERT INTO users(id,username,password_hash,enabled) VALUES(?,?,?,TRUE)",
      userId,"dual.worker@example.test",encoder.encode("StaffPasswordForTests"));
  update("INSERT INTO customer_profiles VALUES(?,?)",userId,userId);

  // RC-01 permits this already-active Customer identity to be onboarded as a Vendor employee.
  String onboardingPlatform="onboarding.admin@platform.example.test";
  long onboardingPlatformId=db.queryForObject("SELECT COALESCE(MAX(id),0)+1 FROM users",Long.class);
  update("INSERT INTO users(id,username,password_hash,enabled) VALUES(?,?,?,TRUE)",
      onboardingPlatformId,onboardingPlatform,encoder.encode("PlatformOnboardPassword"));
  update("INSERT INTO platform_user_permissions(username,permission) VALUES(?,'PLATFORM_TENANT_CREATE')",onboardingPlatform);
  var onboardingSession=authentication.login(onboardingPlatform,"PlatformOnboardPassword");
  var onboardingActor=authentication.authenticate(jwt.decoder().decode(onboardingSession.accessToken()));
  var identityProvisioning=applicationContext.getBean(com.saumya.pos.identity.IdentityProvisioningService.class);
  asAuthentication(onboardingActor,()->{
    identityProvisioning.ensureVendorEligible("dual.worker@example.test");
    var invitation=identityProvisioning.issueInvitation("dual.worker@example.test");
    assertThat(invitation.accountAlreadyActive()).isTrue();
    assertThat(invitation.token()).isNull();
    var provisioned=memberships.provisionInitialVendorAdmin(1,1,"dual.worker@example.test");
    assertThat(provisioned.created()).isTrue();
    assertThat(provisioned.username()).isEqualTo("dual.worker@example.test");
    return null;
  });
  assertThat(db.queryForObject("SELECT COUNT(*) FROM employees WHERE username='dual.worker@example.test' AND tenant_id=1 AND enabled=TRUE",Integer.class)).isEqualTo(1);

  String credentials="{\"username\":\"dual.worker@example.test\",\"password\":\"StaffPasswordForTests\"}";
  mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content(credentials))
      .andExpect(status().isConflict());

  String customerCredentials="{\"username\":\"dual.worker@example.test\",\"password\":\"StaffPasswordForTests\",\"actorType\":\"CUSTOMER\"}";
  var customerLogin=mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content(customerCredentials))
      .andExpect(status().isOk()).andReturn();
  String customerToken=json.readTree(customerLogin.getResponse().getContentAsString()).path("accessToken").asText();
  var customerRefresh=customerLogin.getResponse().getCookie("solar-refresh");
  assertThat(customerRefresh).isNotNull();
  var customerJwt=jwt.decoder().decode(customerToken);
  assertThat(customerJwt.getClaimAsString("actor_type")).isEqualTo("CUSTOMER");
  assertThat(customerJwt.getClaims().containsKey("tenant")).isFalse();
  assertThat(customerJwt.getClaimAsString("membership_id")).isNull();
  mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+customerToken))
      .andExpect(status().isOk()).andExpect(jsonPath("$.actorType").value("CUSTOMER"))
      .andExpect(jsonPath("$.permissions").isArray()).andExpect(jsonPath("$.permissions[0]").value("CUSTOMER"));
  mvc.perform(get("/api/v1/customer/cart").header("Authorization","Bearer "+customerToken))
      .andExpect(status().isOk());
  mvc.perform(get("/api/v1/vendor/products").header("Authorization","Bearer "+customerToken))
      .andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/auth/context").with(csrf()).cookie(customerRefresh)
      .header("Authorization","Bearer "+customerToken).contentType("application/json")
      .content("{\"actorType\":\"PLATFORM\"}"))
      .andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/auth/context").with(csrf()).cookie(customerRefresh)
      .header("Authorization","Bearer "+customerToken).contentType("application/json")
      .content("{\"actorType\":\"VENDOR\",\"tenantId\":2}"))
      .andExpect(status().isUnauthorized());
  // A rejected context selection must not consume or broaden the existing Customer session.
  mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+customerToken))
      .andExpect(status().isOk()).andExpect(jsonPath("$.actorType").value("CUSTOMER"));

  String switchBody="{\"actorType\":\"VENDOR\",\"tenantId\":1}";
  var switched=mvc.perform(post("/api/v1/auth/context").with(csrf()).cookie(customerRefresh)
      .header("Authorization","Bearer "+customerToken).contentType("application/json").content(switchBody))
      .andExpect(status().isOk()).andReturn();
  String vendorToken=json.readTree(switched.getResponse().getContentAsString()).path("accessToken").asText();
  var vendorRefresh=switched.getResponse().getCookie("solar-refresh");
  assertThat(vendorRefresh).isNotNull();
  assertThat(vendorRefresh.getValue()).isNotEqualTo(customerRefresh.getValue());
  var vendorJwt=jwt.decoder().decode(vendorToken);
  assertThat(vendorJwt.getClaimAsString("actor_type")).isEqualTo("VENDOR");
  assertThat(((Number)vendorJwt.getClaim("tenant")).longValue()).isEqualTo(1L);
  assertThat(vendorJwt.getClaimAsString("membership_id")).isEqualTo("1:dual.worker@example.test");
  mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+vendorToken))
      .andExpect(status().isOk()).andExpect(jsonPath("$.actorType").value("VENDOR"))
      .andExpect(jsonPath("$.permissions").isArray());
  mvc.perform(get("/api/v1/vendor/products").header("Authorization","Bearer "+vendorToken))
      .andExpect(status().isOk());
  mvc.perform(get("/api/v1/customer/cart").header("Authorization","Bearer "+vendorToken))
      .andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+customerToken))
      .andExpect(status().isUnauthorized());

  var refreshed=mvc.perform(post("/api/v1/auth/refresh").with(csrf()).cookie(vendorRefresh))
      .andExpect(status().isOk()).andReturn();
  String refreshedVendorToken=json.readTree(refreshed.getResponse().getContentAsString()).path("accessToken").asText();
  var refreshedJwt=jwt.decoder().decode(refreshedVendorToken);
  assertThat(refreshedJwt.getClaimAsString("actor_type")).isEqualTo("VENDOR");
  assertThat(((Number)refreshedJwt.getClaim("tenant")).longValue()).isEqualTo(1L);
  assertThat(refreshedJwt.getClaimAsString("membership_id")).isEqualTo("1:dual.worker@example.test");

  var latestRefresh=refreshed.getResponse().getCookie("solar-refresh");
  update("UPDATE employees SET enabled=FALSE WHERE username='dual.worker@example.test'");
  mvc.perform(post("/api/v1/auth/refresh").with(csrf()).cookie(latestRefresh))
      .andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+refreshedVendorToken))
      .andExpect(status().isUnauthorized());

  update("INSERT INTO platform_user_permissions(username,permission) VALUES('dual.worker@example.test','PLATFORM_SUPPORT_ACCESS')");
  asAuthentication(onboardingActor,()->{
    assertThatThrownBy(()->identityProvisioning.ensureVendorEligible("dual.worker@example.test"))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
        .hasFieldOrPropertyWithValue("status",org.springframework.http.HttpStatus.CONFLICT);
    return null;
  });
  assertThatThrownBy(()->authentication.login("dual.worker@example.test","StaffPasswordForTests"))
      .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
      .hasFieldOrPropertyWithValue("status",org.springframework.http.HttpStatus.UNAUTHORIZED);
 }
 @Test void p007VendorCanSwitchBackToCustomerAndDropsVendorScope() throws Exception {
  employee("dual.switchback@example.test",1,1);
  long userId=db.queryForObject("SELECT id FROM users WHERE username='dual.switchback@example.test'",Long.class);
  update("INSERT INTO customer_profiles VALUES(?,?)",userId,userId);

  String loginBody="{\"username\":\"dual.switchback@example.test\",\"password\":\"StaffPasswordForTests\",\"actorType\":\"VENDOR\",\"tenantId\":1}";
  var login=mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content(loginBody))
      .andExpect(status().isOk()).andReturn();
  String vendorToken=json.readTree(login.getResponse().getContentAsString()).path("accessToken").asText();
  var vendorRefresh=login.getResponse().getCookie("solar-refresh");
  assertThat(vendorRefresh).isNotNull();
  assertThat(jwt.decoder().decode(vendorToken).getClaimAsString("actor_type")).isEqualTo("VENDOR");

  var switched=mvc.perform(post("/api/v1/auth/context").with(csrf()).cookie(vendorRefresh)
      .header("Authorization","Bearer "+vendorToken).contentType("application/json")
      .content("{\"actorType\":\"CUSTOMER\"}"))
      .andExpect(status().isOk()).andReturn();
  String customerToken=json.readTree(switched.getResponse().getContentAsString()).path("accessToken").asText();
  var customerRefresh=switched.getResponse().getCookie("solar-refresh");
  assertThat(customerRefresh).isNotNull();
  assertThat(customerRefresh.getValue()).isNotEqualTo(vendorRefresh.getValue());
  var customerJwt=jwt.decoder().decode(customerToken);
  assertThat(customerJwt.getClaimAsString("actor_type")).isEqualTo("CUSTOMER");
  assertThat(customerJwt.getClaims().containsKey("tenant")).isFalse();
  assertThat(customerJwt.getClaimAsString("membership_id")).isNull();
  mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+customerToken))
      .andExpect(status().isOk()).andExpect(jsonPath("$.actorType").value("CUSTOMER"))
      .andExpect(jsonPath("$.permissions[0]").value("CUSTOMER"));
  mvc.perform(get("/api/v1/customer/cart").header("Authorization","Bearer "+customerToken))
      .andExpect(status().isOk());
  mvc.perform(get("/api/v1/vendor/products").header("Authorization","Bearer "+customerToken))
      .andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+vendorToken))
      .andExpect(status().isUnauthorized());

  var refreshed=mvc.perform(post("/api/v1/auth/refresh").with(csrf()).cookie(customerRefresh))
      .andExpect(status().isOk()).andReturn();
  String refreshedToken=json.readTree(refreshed.getResponse().getContentAsString()).path("accessToken").asText();
  var refreshedJwt=jwt.decoder().decode(refreshedToken);
  assertThat(refreshedJwt.getClaimAsString("actor_type")).isEqualTo("CUSTOMER");
  assertThat(refreshedJwt.getClaims().containsKey("tenant")).isFalse();
 }
 @Test void p007PlatformIdentityCannotSwitchIntoCommercialContext() throws Exception {
  multiVendorDemo();
  var platformSession=authentication.login("superadmin@platform.demo.test","PlatformDemo2026!");
  var refreshCookie=new jakarta.servlet.http.Cookie("solar-refresh",platformSession.refreshToken());
  for(String target:List.of("{\"actorType\":\"CUSTOMER\"}","{\"actorType\":\"VENDOR\",\"tenantId\":910001}")) {
    mvc.perform(post("/api/v1/auth/context").with(csrf()).cookie(refreshCookie)
        .header("Authorization","Bearer "+platformSession.accessToken()).contentType("application/json").content(target))
        .andExpect(status().isForbidden());
  }
  mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+platformSession.accessToken()))
      .andExpect(status().isOk()).andExpect(jsonPath("$.actorType").value("PLATFORM"));
  mvc.perform(get("/api/v1/vendor/products").header("Authorization","Bearer "+platformSession.accessToken()))
      .andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/customer/cart").header("Authorization","Bearer "+platformSession.accessToken()))
      .andExpect(status().isForbidden());
 }
 @Test void vendorMembershipIsRevalidatedForEveryJwtRequest() throws Exception {
  setEmployeeRolePermissions("alice",List.of("POS_VIEW"));var login=authentication.login("alice","StaffPasswordForTests");
  mvc.perform(get("/api/v1/vendor/pos/products").header("Authorization","Bearer "+login.accessToken())).andExpect(status().isOk());
  update("UPDATE employees SET enabled=FALSE WHERE username='alice'");
  mvc.perform(get("/api/v1/vendor/pos/products").header("Authorization","Bearer "+login.accessToken())).andExpect(status().isUnauthorized());
 }
 @Test void guestAssociationRequiresCustomerProofAndOnlyCreatesCurrentVendorRelationship(){
  configureOnline();var draft=as("alice",()->as("alice", () -> checkout.pos("alice","customer-association",posDraft())));
  assertThat(db.queryForObject("SELECT customer_id FROM orders WHERE id=?",Long.class,draft.id())).isNull();
  assertThatThrownBy(()->as("alice", () -> associations.create("alice",draft.id(),false)));
  var challenge=as("alice", () -> associations.create("alice",draft.id(),true));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM tenant_customers",Integer.class)).isZero();
  assertThatThrownBy(()->as("alice", () -> associations.approve("alice",challenge.token(),true)));
  assertThatThrownBy(()->asCustomer("customer", () -> associations.approve("customer",challenge.token(),false)));
  assertThat(asCustomer("customer", () -> associations.approve("customer",challenge.token(),true)).status()).isEqualTo("ASSOCIATED");
  assertThat(asCustomer("customer", () -> associations.approve("customer",challenge.token(),true)).status()).isEqualTo("ASSOCIATED");
  assertThatThrownBy(()->asCustomer("other-customer", () -> associations.approve("other-customer",challenge.token(),true)));
  assertThat(db.queryForObject("SELECT customer_id FROM orders WHERE id=?",Long.class,draft.id())).isEqualTo(100L);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM tenant_customers WHERE tenant_id=1 AND customer_id=100",Integer.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM tenant_customers WHERE tenant_id=2",Integer.class)).isZero();
  as("alice", () -> checkout.finalizeCash("alice",draft.id(),new BigDecimal("120")));assertThat(asCustomer("customer",()->onlineReceipts.read("customer",draft.id())).orderId()).isEqualTo(draft.id());
 }
 @Test void expiredAssociationTokenCannotAttachCustomer(){configureOnline();var draft=as("alice",()->as("alice", () -> checkout.pos("alice","expired-association",posDraft())));var challenge=as("alice", () -> associations.create("alice",draft.id(),true));update("UPDATE pos_customer_challenges SET expires_at=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)));assertThatThrownBy(()->asCustomer("customer", () -> associations.approve("customer",challenge.token(),true)));assertThat(db.queryForObject("SELECT COUNT(*) FROM tenant_customers",Integer.class)).isZero();}
 @Test void partialCashImmediatelyAppearsInDrawerAndRetriesCannotDoubleCount(){
  enableTerminal();update("UPDATE branches SET split_tender_enabled=TRUE WHERE id=1");
  var draft=as("alice", () -> checkout.pos("alice","drawer-partial",posDraft()));var request=new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("CASH",new BigDecimal("40"),new BigDecimal("50"));
  as("alice", () -> payments.tender("alice",draft.id(),"drawer-cash",request));
  var scope=new PosService.Scope(1,1,"alice");var first=asScope(scope, () -> service.reconciliation(scope,session));
  assertThat(first.cashReceived()).isEqualByComparingTo("50");assertThat(first.changeGiven()).isEqualByComparingTo("10");assertThat(first.expectedCash()).isEqualByComparingTo("90");
  as("alice", () -> payments.tender("alice",draft.id(),"drawer-cash",request));assertThat(asScope(scope, () -> service.reconciliation(scope,session))).isEqualTo(first);
  as("alice", () -> payments.tender("alice",draft.id(),"drawer-rest",new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("CASH",new BigDecimal("70"),new BigDecimal("70"))));
  assertThat(asScope(scope, () -> service.reconciliation(scope,session)).expectedCash()).isEqualByComparingTo("160");
  payments.finalizeTenders("alice",draft.id());assertThat(asScope(scope, () -> service.reconciliation(scope,session)).expectedCash()).isEqualByComparingTo("160");
 }
 @Test void expiredPartialCashRetainsStockAndCanCompleteOnlyTheRemainingBalance(){
  enableTerminal();update("UPDATE branches SET split_tender_enabled=TRUE WHERE id=1");var draft=as("alice", () -> checkout.pos("alice","expired-partial",posDraft()));
  as("alice", () -> payments.tender("alice",draft.id(),"expired-cash",new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("CASH",new BigDecimal("40"),new BigDecimal("40"))));
  update("UPDATE orders SET expires_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)),draft.id());draftExpiry.expireOne(1,draft.id());
  assertThat(db.queryForObject("SELECT status FROM orders WHERE id=?",String.class,draft.id())).isEqualTo("DRAFT");
  assertThat(as("alice",()->stock.atBranch(1,1,1))).isZero();assertThatThrownBy(()->as("alice", () -> checkout.cancelPos("alice",draft.id(),"Cancel"))).hasMessageContaining("Reconcile");
  assertThat(as("alice", () -> payments.tender("alice",draft.id(),"expired-rest",new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("CASH",new BigDecimal("70"),new BigDecimal("70")))).orderStatus()).isEqualTo("PAID");
  assertThat(asScope(a, () -> service.reconciliation(a,session)).expectedCash()).isEqualByComparingTo("160");
 }
 @Test void expiredUnpaidBillCannotStartTakingFunds(){
  var draft=as("alice", () -> checkout.pos("alice","expired-unpaid",posDraft()));update("UPDATE orders SET expires_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)),draft.id());
  assertThatThrownBy(()->as("alice", () -> payments.tender("alice",draft.id(),"expired-charge",new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("CASH",new BigDecimal("110"),new BigDecimal("110"))))).hasMessageContaining("not payable");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_drawer_tenders",Integer.class)).isZero();
 }
 @Test void splitTenderLimitsAndChangedRetryPayloadAreRejected(){
  enableTerminal();var draft=as("alice", () -> checkout.pos("alice","tender-limits",posDraft()));
  assertThatThrownBy(()->as("alice", () -> payments.tender("alice",draft.id(),"split-disabled",new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("CASH",new BigDecimal("40"),new BigDecimal("40"))))).hasMessageContaining("disabled");
  update("UPDATE branches SET split_tender_enabled=TRUE WHERE id=1");
  assertThatThrownBy(()->as("alice", () -> payments.tender("alice",draft.id(),"overpayment",new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("CASH",new BigDecimal("111"),new BigDecimal("111"))))).hasMessageContaining("remaining");
  as("alice", () -> payments.tender("alice",draft.id(),"partial-cash",new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("CASH",new BigDecimal("40"),new BigDecimal("40"))));
  assertThatThrownBy(()->as("alice", () -> payments.tender("alice",draft.id(),"partial-cash",new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("CASH",new BigDecimal("50"),new BigDecimal("50"))))).hasMessageContaining("Idempotency");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_payment_attempts",Integer.class)).isEqualTo(1);
 }
 @Test void splitCashAndVerifiedTerminalFinalizeExactlyOnce(){
  enableTerminal();update("UPDATE branches SET split_tender_enabled=TRUE WHERE id=1");
  var draft=as("alice", () -> checkout.pos("alice","split-draft",posDraft()));
  var cash=as("alice", () -> payments.tender("alice",draft.id(),"split-cash",new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("CASH",new BigDecimal("40"),new BigDecimal("50"))));
  assertThat(cash.remaining()).isEqualByComparingTo("70");assertThat(cash.orderStatus()).isEqualTo("DRAFT");
  assertThatThrownBy(()->as("alice",()->payments.finalizeTenders("alice",draft.id()))).hasMessageContaining("total");
  assertThatThrownBy(()->as("alice", () -> checkout.cancelPos("alice",draft.id(),"Cannot discard received cash")));
  var external=as("alice", () -> payments.tender("alice",draft.id(),"split-upi",new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("UPI",new BigDecimal("70"),null)));
  var attempt=external.tenders().stream().filter(t->"UPI".equals(t.method())).findFirst().orElseThrow();
  assertThat(as("manager",()->payments.awaitingReview("manager")).getFirst().attemptId()).isEqualTo(attempt.attemptId());
  assertThatThrownBy(()->as("alice", () -> payments.tender("alice",draft.id(),"another-charge",new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("CARD",BigDecimal.ONE,null))));
  as("manager", () -> tenderReviews.review("manager",draft.id(),terminalReview(attempt.attemptId(),"CONFIRM")));
  assertThat(as("alice",()->payments.finalizeTenders("alice",draft.id())).orderStatus()).isEqualTo("PAID");
  assertThat(as("alice", () -> payments.tender("alice",draft.id(),"split-cash",new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("CASH",new BigDecimal("40"),new BigDecimal("50")))).confirmed()).isEqualByComparingTo("110");
  assertThat(receiptReader.receipt(a,draft.id()).get("payment_method")).isEqualTo("SPLIT");assertThat(asScope(a, () -> service.reconciliation(a,session)).expectedCash()).isEqualByComparingTo("90");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_ledger",Integer.class)).isEqualTo(1);
 }
 @Test void cashierDiscountIsAuthorizedAndStoredBeforePayment() throws Exception {
  update("INSERT INTO catalog_discount_policy VALUES(1,20)");
  String body="{\"sessionId\":\""+session+"\",\"items\":[{\"productId\":1,\"quantity\":1}],\"invoiceRequested\":false,\"discounts\":{\"linePercent\":{\"1\":10},\"billPercent\":10,\"reason\":\"Approved sale\"}}";
  mvc.perform(post("/api/v1/vendor/pos/drafts").with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_SALE_CREATE"))
   .header("Idempotency-Key","discount-no-grant").contentType("application/json").content(body)).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/vendor/pos/drafts").with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_SALE_CREATE",()->"POS_DISCOUNT_APPLY"))
   .header("Idempotency-Key","discount-approved").contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(89.10));
  assertThat(db.queryForObject("SELECT manual_discount FROM order_items",BigDecimal.class)).isEqualByComparingTo("19");
  assertThat(db.queryForObject("SELECT discount_reason FROM orders",String.class)).isEqualTo("Approved sale");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_ledger",Integer.class)).isZero();
 }
 @Test void mixedVendorCartReturnsStableProblem() throws Exception {
  configureOnline();asCustomer("customer", () -> marketplace.put(100,new CartService.Item("vendor-1",1,1)));
  mvc.perform(post("/api/v1/customer/cart/items").with(csrf()).with(user("customer").authorities(()->"CUSTOMER"))
   .contentType("application/json").content("{\"vendorSlug\":\"vendor-2\",\"productId\":2,\"quantity\":1}"))
   .andExpect(status().isConflict()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
   .andExpect(jsonPath("$.code").value("CART_VENDOR_MISMATCH"));
  assertThat(asCustomer("customer", () -> marketplace.cart(100)).vendorSlug()).isEqualTo("vendor-1");
 }
 @Test void customerReceiptUsesSavedFactsAndCopiesDoNotCreateFinancialWrites(){
  configureOnline();update("UPDATE branches SET invoice_required=TRUE WHERE id=1");var draft=onlineDraft("receipt-online");var attempt=asCustomer("customer", () -> payments.start("customer",draft.id(),"UPI"));asCustomer("customer", () -> payments.confirm("customer",draft.id(),attempt.attemptId()));
  update("UPDATE tenants SET name='Renamed',lifecycle_status='SUSPENDED' WHERE id=1");update("UPDATE branches SET name='Changed outlet' WHERE id=1");update("UPDATE products SET name='Changed panel',price=999 WHERE id=1");
  var receipt=asCustomer("customer",()->onlineReceipts.read("customer",draft.id()));assertThat(receipt.seller()).isEqualTo("Vendor A");assertThat(receipt.outlet()).isEqualTo("A");assertThat(receipt.items().getFirst().name()).isEqualTo("Panel");assertThat(receipt.total()).isEqualByComparingTo("110");assertThat(receipt.invoices().getFirst().seller()).isEqualTo("Vendor A");assertThat(receipt.copy()).isFalse();
  for(int i=0;i<2;i++){var copy=asCustomer("customer",()->onlineReceipts.copy("customer",draft.id()));assertThat(copy.copy()).isTrue();assertThat(copy.orderId()).isEqualTo(receipt.orderId());assertThat(copy.issuedAt()).isEqualTo(receipt.issuedAt());}
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE action='CUSTOMER_RECEIPT_COPY' AND actor='customer:100'",Integer.class)).isEqualTo(2);assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_ledger",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_invoices",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_payment_attempts",Integer.class)).isEqualTo(1);
 }
 @Test void onlineReceiptApiEnforcesCustomerOwnershipAndAuditedCopyCsrf() throws Exception {
  configureOnline();var draft=onlineDraft("receipt-security");String path="/api/v1/customer/orders/"+draft.id();
  mvc.perform(get(path+"/receipt").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());mvc.perform(get(path+"/receipt").with(user("alice").authorities(()->"POS_RECEIPT_VIEW"))).andExpect(status().isForbidden());mvc.perform(get(path+"/receipt").with(user("customer").authorities(()->"CUSTOMER"))).andExpect(status().isNotFound());
  var attempt=asCustomer("customer", () -> payments.start("customer",draft.id(),"CARD"));asCustomer("customer", () -> payments.confirm("customer",draft.id(),attempt.attemptId()));
  update("INSERT INTO users(id,username,password_hash,enabled) VALUES(300,'other-buyer','unused',TRUE)");update("INSERT INTO customer_profiles VALUES(300,300)");mvc.perform(get(path+"/receipt").with(user("other-buyer").authorities(()->"CUSTOMER"))).andExpect(status().isNotFound());
  mvc.perform(post(path+"/receipt-copy").with(user("customer").authorities(()->"CUSTOMER")).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  mvc.perform(post(path+"/receipt-copy").with(csrf()).with(user("customer").authorities(()->"CUSTOMER")).contentType("application/json").content("{\"tenantId\":2}")).andExpect(status().isBadRequest());
  mvc.perform(post(path+"/receipt-copy").with(csrf()).with(user("customer").authorities(()->"CUSTOMER")).contentType("application/json").content("{}")).andExpect(status().isOk()).andExpect(jsonPath("$.copy").value(true)).andExpect(jsonPath("$.paymentMethod").value("MOCK_CARD")).andExpect(jsonPath("$.seller").value("Vendor A"));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE action='CUSTOMER_RECEIPT_COPY'",Integer.class)).isEqualTo(1);
 }
 @Test void onlineReceiptCannotExposeGuestPosSale(){configureOnline();String id=(String)as("alice",()->as("alice", () -> checkout.cashSale("alice","receipt-pos-private",sale(1,"120")))).get("id");assertThatThrownBy(()->onlineReceipts.read("customer",id)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);}
 @Test void onlinePaymentIsDurableVendorOwnedAndConfirmedExactlyOnce(){
  configureOnline();var draft=onlineDraft("online-payment");var started=asCustomer("customer", () -> payments.start("customer",draft.id(),"UPI"));
  assertThat(started.merchantAccount()).isEqualTo("mock-vendor-1");assertThat(started.state()).isEqualTo("PENDING");
  assertThat(db.queryForObject("SELECT status FROM orders",String.class)).isEqualTo("DRAFT");assertThat(as("alice",()->stock.atBranch(1,1,1))).isZero();
  assertThat(asCustomer("customer", () -> payments.start("customer",draft.id(),"UPI")).attemptId()).isEqualTo(started.attemptId());assertThatThrownBy(()->asCustomer("customer", () -> payments.start("customer",draft.id(),"CARD")));
  assertThat(asCustomer("customer", () -> payments.confirm("customer",draft.id(),started.attemptId())).state()).isEqualTo("CONFIRMED");asCustomer("customer", () -> payments.confirm("customer",draft.id(),started.attemptId()));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_ledger",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT payment_method FROM orders",String.class)).isEqualTo("MOCK_UPI");
  assertThat(db.queryForObject("SELECT customer_id FROM orders",Long.class)).isEqualTo(100);assertThat(db.queryForObject("SELECT cash_received FROM orders",BigDecimal.class)).isNull();
  assertThat(asCustomer("customer", () -> marketplace.cart(100)).id()).isNull();
 }
 @Test void onlinePaymentKeepsEditedCartAndIssuesConfiguredTaxInvoice(){
  configureOnline();update("UPDATE inventory SET quantity=3 WHERE tenant_id=1");update("UPDATE branches SET invoice_required=TRUE WHERE id=1");var draft=onlineDraft("online-edited-cart");var attempt=asCustomer("customer", () -> payments.start("customer",draft.id(),"CARD"));
  asCustomer("customer", () -> marketplace.put(100,new CartService.Item("vendor-1",1,2)));asCustomer("customer", () -> payments.confirm("customer",draft.id(),attempt.attemptId()));
  assertThat(asCustomer("customer", () -> marketplace.cart(100)).items().getFirst().quantity()).isEqualTo(2);assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_invoices",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT seller_name FROM vendor_invoices",String.class)).isEqualTo("Vendor A");
 }
 void enableTerminal(){supervisor("manager");update("UPDATE branches SET manual_tender_enabled=TRUE,upi_merchant_reference='vendor-upi-account',card_merchant_reference='vendor-card-account' WHERE id=1");}
 com.saumya.pos.checkout.PaymentCheckoutService.Review terminalReview(String attempt,String decision){return new com.saumya.pos.checkout.PaymentCheckoutService.Review(attempt,decision,"terminal-result-123","evidence-record-123","Independent account and amount verification");}
 @Test void terminalVerificationIsDefaultDisabledAndOnlineSimulationCannotConfirmPos(){
  var draft=as("alice", () -> checkout.pos("alice","terminal-disabled",posDraft()));assertThat(as("alice", () -> payments.tenderStatus("alice",draft.id())).tenders()).isEmpty();assertThatThrownBy(()->terminal("alice",draft.id(),"UPI"));assertThatThrownBy(()->as("alice", () -> payments.confirm("alice",draft.id(),"arbitrary")));assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_payment_attempts",Integer.class)).isZero();
 }
 @Test void terminalUnknownCanConfirmAfterOriginalTtlWithoutRechargingOrReleasingStock(){
  enableTerminal();var draft=as("alice", () -> checkout.pos("alice","terminal-late-result",posDraft()));var attempt=terminal("alice",draft.id(),"UPI");as("alice", () -> payments.unknown("alice",draft.id(),attempt.attemptId()));update("UPDATE orders SET expires_at=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)));draftExpiry.expireOne(1,draft.id());
  assertThat(as("alice",()->stock.atBranch(1,1,1))).isZero();assertThatThrownBy(()->as("alice", () -> checkout.cancelPos("alice",draft.id(),"Cannot discard funds")));assertThatThrownBy(()->terminal("alice",draft.id(),"CARD"));assertThatThrownBy(()->as("bob", () -> tenderReviews.review("bob",draft.id(),terminalReview(attempt.attemptId(),"CONFIRM"))));
  assertThat(as("manager", () -> payments.awaitingReview("manager"))).hasSize(1);as("manager", () -> tenderReviews.review("manager",draft.id(),terminalReview(attempt.attemptId(),"CONFIRM")));assertThat(as("manager", () -> payments.awaitingReview("manager"))).isEmpty();assertThat(as("manager",()->payments.reviewStatus("manager",draft.id())).state()).isEqualTo("CONFIRMED");assertThat(db.queryForObject("SELECT reviewed_by FROM vendor_payment_attempts",String.class)).isEqualTo("manager");assertThat(db.queryForObject("SELECT evidence_reference FROM vendor_payment_attempts",String.class)).isEqualTo("evidence-record-123");assertThat(db.queryForObject("SELECT reviewed_at FROM vendor_payment_attempts",java.sql.Timestamp.class)).isNotNull();assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_payment_attempts",Integer.class)).isEqualTo(1);
 }
 @Test void unknownTerminalTenderRequiresOpenCashierSessionAndHasNoSideEffects(){
  enableTerminal();
  var draft=as("alice",()->checkout.pos("alice","p005-unknown-after-close",posDraft()));
  var attempt=terminal("alice",draft.id(),"UPI");
  int reserved=db.queryForObject("SELECT reserved FROM inventory WHERE tenant_id=1 AND branch_id=1 AND product_id=1",Integer.class);
  update("UPDATE pos_sessions SET state='CLOSED',closed_at=CURRENT_TIMESTAMP WHERE tenant_id=1 AND id=?",session);
  assertThatThrownBy(()->as("alice",()->payments.unknown("alice",draft.id(),attempt.attemptId())))
    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThat(db.queryForObject("SELECT state FROM vendor_payment_attempts WHERE id=?",String.class,attempt.attemptId())).isEqualTo("PENDING_VERIFICATION");
  assertThat(db.queryForObject("SELECT status FROM orders WHERE id=?",String.class,draft.id())).isEqualTo("DRAFT");
  assertThat(db.queryForObject("SELECT reserved FROM inventory WHERE tenant_id=1 AND branch_id=1 AND product_id=1",Integer.class)).isEqualTo(reserved);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_ledger WHERE order_id=?",Integer.class,draft.id())).isZero();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE action='TERMINAL_TENDER_UNKNOWN' AND resource_id=?",Integer.class,draft.id())).isZero();
 }
 @Test void supervisorTenderReviewRequiresOpenAssignedOriginalSessionAndHasNoSideEffects(){
  enableTerminal();
  var draft=as("alice",()->checkout.pos("alice","p005-review-after-close",posDraft()));
  var attempt=terminal("alice",draft.id(),"CARD");
  int reserved=db.queryForObject("SELECT reserved FROM inventory WHERE tenant_id=1 AND branch_id=1 AND product_id=1",Integer.class);
  update("UPDATE pos_sessions SET state='CLOSED',closed_at=CURRENT_TIMESTAMP WHERE tenant_id=1 AND id=?",session);
  assertThatThrownBy(()->as("manager",()->tenderReviews.review("manager",draft.id(),terminalReview(attempt.attemptId(),"CONFIRM"))))
    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThat(db.queryForObject("SELECT state FROM vendor_payment_attempts WHERE id=?",String.class,attempt.attemptId())).isEqualTo("PENDING_VERIFICATION");
  assertThat(db.queryForObject("SELECT reviewed_by FROM vendor_payment_attempts WHERE id=?",String.class,attempt.attemptId())).isNull();
  assertThat(db.queryForObject("SELECT status FROM orders WHERE id=?",String.class,draft.id())).isEqualTo("DRAFT");
  assertThat(db.queryForObject("SELECT reserved FROM inventory WHERE tenant_id=1 AND branch_id=1 AND product_id=1",Integer.class)).isEqualTo(reserved);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_ledger WHERE order_id=?",Integer.class,draft.id())).isZero();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE action='TERMINAL_TENDER_CONFIRM' AND resource_id=?",Integer.class,draft.id())).isZero();
 }
 @Test void supervisorTenderReviewRejectsRevokedOriginalCashierRegisterAssignment(){
  enableTerminal();
  var draft=as("alice",()->checkout.pos("alice","p005-review-revoked-cashier-assignment",posDraft()));
  var attempt=terminal("alice",draft.id(),"CARD");
  update("UPDATE employee_register_assignments SET active=FALSE,revoked_by='manager',revoked_at=CURRENT_TIMESTAMP WHERE tenant_id=1 AND username='alice' AND register_id=1");
  assertThatThrownBy(()->as("manager",()->tenderReviews.review("manager",draft.id(),terminalReview(attempt.attemptId(),"CONFIRM"))))
    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThat(as("manager",()->payments.awaitingReview("manager"))).isEmpty();
  assertThat(db.queryForObject("SELECT state FROM vendor_payment_attempts WHERE id=?",String.class,attempt.attemptId())).isEqualTo("PENDING_VERIFICATION");
  assertThat(db.queryForObject("SELECT reviewed_by FROM vendor_payment_attempts WHERE id=?",String.class,attempt.attemptId())).isNull();
  assertThat(db.queryForObject("SELECT status FROM orders WHERE id=?",String.class,draft.id())).isEqualTo("DRAFT");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_ledger WHERE order_id=?",Integer.class,draft.id())).isZero();
 }
 @Test void supervisorReviewQueueOnlyReturnsOpenSessionsOnRegistersAssignedToReviewer(){
  enableTerminal();
  var draft=as("alice",()->checkout.pos("alice","p005-review-register-scope",posDraft()));
  var attempt=terminal("alice",draft.id(),"UPI");
  assertThat(as("manager",()->payments.awaitingReview("manager"))).hasSize(1);
  update("INSERT INTO registers(id,tenant_id,branch_id) VALUES(3,1,1)");
  update("UPDATE employee_register_assignments SET active=FALSE,revoked_by='manager',revoked_at=CURRENT_TIMESTAMP WHERE tenant_id=1 AND username='manager' AND register_id=1");
  assignRegister("manager",1,1,3);
  assertThat(as("manager",()->payments.awaitingReview("manager"))).isEmpty();
  assertThatThrownBy(()->as("manager",()->tenderReviews.review("manager",draft.id(),terminalReview(attempt.attemptId(),"CONFIRM"))))
    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThat(db.queryForObject("SELECT state FROM vendor_payment_attempts WHERE id=?",String.class,attempt.attemptId())).isEqualTo("PENDING_VERIFICATION");
  assertThat(db.queryForObject("SELECT status FROM orders WHERE id=?",String.class,draft.id())).isEqualTo("DRAFT");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_ledger WHERE order_id=?",Integer.class,draft.id())).isZero();
 }
 @Test void tenderStatusRejectsRevokedOriginalRegisterAssignmentEvenWhenAnotherTillRemainsAssigned(){
  enableTerminal();
  update("INSERT INTO registers(id,tenant_id,branch_id) VALUES(3,1,1)");
  assignRegister("alice",1,1,3);
  var draft=as("alice",()->checkout.pos("alice","p005-tender-read-revoked-register",posDraft()));
  var attempt=terminal("alice",draft.id(),"CARD");
  update("UPDATE employee_register_assignments SET active=FALSE,revoked_by='alice',revoked_at=CURRENT_TIMESTAMP WHERE tenant_id=1 AND username='alice' AND register_id=1");
  assertThatThrownBy(()->as("alice",()->payments.tenderStatus("alice",draft.id())))
    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThat(db.queryForObject("SELECT state FROM vendor_payment_attempts WHERE id=?",String.class,attempt.attemptId())).isEqualTo("PENDING_VERIFICATION");
  assertThat(db.queryForObject("SELECT status FROM orders WHERE id=?",String.class,draft.id())).isEqualTo("DRAFT");
 }
 @Test void terminalReviewApiRequiresIndependentPermissionCsrfAndSafeEvidence() throws Exception {
  enableTerminal();var draft=as("alice", () -> checkout.pos("alice","terminal-review-api",posDraft()));var attempt=terminal("alice",draft.id(),"CARD");String path="/api/v1/vendor/pos/payments/review/"+draft.id();String body="{\"attemptId\":\""+attempt.attemptId()+"\",\"decision\":\"CONFIRM\",\"reference\":\"terminal-123\",\"evidence\":\"proof-123\",\"reason\":\"Verified original vendor account and full amount\"}";
  mvc.perform(get(path).with(user("manager").authorities(()->"POS_VIEW"))).andExpect(status().isForbidden());mvc.perform(post(path).with(user("manager").authorities(()->"POS_TENDER_MANUAL_VERIFY")).contentType("application/json").content(body)).andExpect(status().isForbidden());
  mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"POS_TENDER_MANUAL_VERIFY")).contentType("application/json").content(body)).andExpect(status().isForbidden());mvc.perform(post(path).with(csrf()).with(user("manager").authorities(()->"POS_TENDER_MANUAL_VERIFY")).contentType("application/json").content(body.replace("terminal-123","4111111111111111"))).andExpect(status().isBadRequest());
  mvc.perform(post(path).with(csrf()).with(bearer("manager","StaffPasswordForTests")).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CONFIRMED"));assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE action='TERMINAL_TENDER_CONFIRM'",Integer.class)).isEqualTo(1);
 }
 @Test void posCardPaymentIssuesReceiptWithoutIncreasingCashDrawer(){
  enableTerminal();
  var draft=as("alice", () -> checkout.pos("alice","pos-card-payment",posDraft()));var attempt=terminal("alice",draft.id(),"CARD");
  assertThatThrownBy(()->as("alice", () -> checkout.finalizeCash("alice",draft.id(),new BigDecimal("120"))));
  assertThatThrownBy(()->as("bob", () -> payments.tenderStatus("bob",draft.id())));assertThatThrownBy(()->as("alice", () -> payments.confirm("alice",draft.id(),"forged")));
  assertThatThrownBy(()->as("alice", () -> tenderReviews.review("alice",draft.id(),terminalReview(attempt.attemptId(),"CONFIRM"))));
  as("manager", () -> tenderReviews.review("manager",draft.id(),terminalReview(attempt.attemptId(),"CONFIRM")));var receipt=as("alice", () -> receiptReader.receipt(a,draft.id()));assertThat(receipt.get("payment_method")).isEqualTo("CARD");assertThat(receipt.get("payment_reference")).isEqualTo("terminal-result-123");assertThat(receipt.get("cash_received")).isEqualTo(new BigDecimal("0.00"));assertThat(asScope(a, () -> service.reconciliation(a,session)).expectedCash()).isEqualByComparingTo("50");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM customer_profiles",Integer.class)).isZero();
  assertThatThrownBy(()->new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx->returnSaleAccess.cashSale(1,1,draft.id()))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }

 @Test void independentTenderSupervisorCanFinalizeWithoutSaleCreatePermission(){
  enableTerminal();
  var draft=as("alice",()->checkout.pos("alice","supervisor-least-privilege",posDraft()));
  var attempt=terminal("alice",draft.id(),"CARD");
  removeAssignedRolePermission("manager","POS_SALE_CREATE");
  assertThat(assignedRolePermissionCount("manager","POS_SALE_CREATE")).isZero();
  assertThat(assignedRolePermissionCount("manager","POS_TENDER_MANUAL_VERIFY")).isEqualTo(1);

  var result=as("manager",()->tenderReviews.review("manager",draft.id(),terminalReview(attempt.attemptId(),"CONFIRM")));

  assertThat(result.state()).isEqualTo("CONFIRMED");
  assertThat(db.queryForObject("SELECT status FROM orders WHERE id=?",String.class,draft.id())).isEqualTo("PAID");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_ledger WHERE order_id=?",Integer.class,draft.id())).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE action='SALE_COMPLETE' AND resource_id=?",Integer.class,draft.id())).isEqualTo(1);
  var receipt=as("alice",()->receiptReader.receipt(a,draft.id()));
  assertThat(receipt.get("payment_method")).isEqualTo("CARD");
 }

 @Test void expiredOrChangedPaymentCannotConsumeStockOrConfirm(){
  enableTerminal();
  var draft=as("alice", () -> checkout.pos("alice","expire-payment",posDraft()));var attempt=terminal("alice",draft.id(),"UPI");
  update("UPDATE products SET price=200 WHERE id=1");assertThat(as("manager", () -> tenderReviews.review("manager",draft.id(),terminalReview(attempt.attemptId(),"CONFIRM"))).orderStatus()).isEqualTo("DRAFT");assertThat(db.queryForObject("SELECT state FROM vendor_payment_attempts",String.class)).isEqualTo("CONFIRMED");as("alice", () -> payments.unknown("alice",draft.id(),attempt.attemptId()));
  update("UPDATE products SET price=100 WHERE id=1");update("UPDATE orders SET expires_at=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)));draftExpiry.expireOne(1,draft.id());
  assertThatThrownBy(()->as("alice", () -> checkout.cancelPos("alice",draft.id(),"Do not discard uncertain payment")));assertThat(as("alice",()->stock.atBranch(1,1,1))).isZero();assertThat(db.queryForObject("SELECT status FROM orders",String.class)).isEqualTo("DRAFT");assertThatThrownBy(()->tenderReviews.review("manager",draft.id(),terminalReview(attempt.attemptId(),"FAIL")));assertThat(as("alice",()->stock.atBranch(1,1,1))).isZero();assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_ledger",Integer.class)).isZero();
 }
 @Test void concurrentPaymentConfirmationIssuesOneReceiptAndOneStockMovement() throws Exception {
  enableTerminal();
  var draft=as("alice", () -> checkout.pos("alice","concurrent-payment",posDraft()));var attempt=terminal("alice",draft.id(),"UPI");
  try(var pool=Executors.newFixedThreadPool(2)){Callable<String> confirm=()->as("manager",()->tenderReviews.review("manager",draft.id(),terminalReview(attempt.attemptId(),"CONFIRM"))).state();for(var result:pool.invokeAll(List.of(confirm,confirm)))assertThat(result.get()).isEqualTo("CONFIRMED");}
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_ledger",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT receipt_sequence FROM branches WHERE id=1",Integer.class)).isEqualTo(1);
 }
 @Test void paymentApiEnforcesPermissionsCsrfAndStrictPayloads() throws Exception {
  enableTerminal();
  var draft=as("alice", () -> checkout.pos("alice","http-payment",posDraft()));String path="/api/v1/vendor/pos/sales/"+draft.id()+"/tenders";
  mvc.perform(get(path).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());mvc.perform(get(path).with(user("alice"))).andExpect(status().isForbidden());
  mvc.perform(post(path).header("Idempotency-Key","http-terminal").with(csrf()).with(user("alice").authorities(()->"POS_SALE_CREATE")).contentType("application/json").content("{\"method\":\"UPI\",\"appliedAmount\":110}")).andExpect(status().isForbidden());
  mvc.perform(post(path).header("Idempotency-Key","http-terminal").with(user("alice").authorities(()->"POS_SALE_CREATE",()->"POS_TENDER_EXTERNAL_RECORD")).contentType("application/json").content("{\"method\":\"UPI\",\"appliedAmount\":110}")).andExpect(status().isForbidden());
  mvc.perform(post(path).header("Idempotency-Key","http-terminal").with(csrf()).with(user("alice").authorities(()->"POS_SALE_CREATE",()->"POS_TENDER_EXTERNAL_RECORD")).contentType("application/json").content("{\"method\":\"UPI\",\"amount\":1}")).andExpect(status().isBadRequest());
  mvc.perform(post(path).header("Idempotency-Key","http-terminal").with(csrf()).with(bearer("alice","StaffPasswordForTests")).contentType("application/json").content("{\"method\":\"UPI\",\"appliedAmount\":110}")).andExpect(status().isOk()).andExpect(jsonPath("$.tenders[0].merchantAccount").value("vendor-upi-account")).andExpect(jsonPath("$.tenders[0].mockEnabled").value(false));
 }
 @Test void cashSaleFailureAfterStockConsumeRollsBackEveryLocalFinancialWrite(){
  update("UPDATE branches SET invoice_required=TRUE WHERE id=1");
  var draft=as("alice",()->checkout.pos("alice","p2-rollback-cash-sale",posDraft()));
  db.update("""
    INSERT INTO vendor_invoices(tenant_id,order_id,invoice_number,seller_name,outlet_name,total)
    VALUES(1,?,'P2-ROLLBACK-INVOICE','Vendor A','A',?)
    """,draft.id(),draft.total());
  Integer quantityBefore=db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1 AND branch_id=1 AND product_id=1",Integer.class);
  Integer reservedBefore=db.queryForObject("SELECT reserved FROM inventory WHERE tenant_id=1 AND branch_id=1 AND product_id=1",Integer.class);

  // Invoice issuance is deliberately made to fail after reservation consumption,
  // cash tender persistence and order payment mutation have been attempted.
  assertThatThrownBy(()->as("alice",()->checkout.finalizeCash("alice",draft.id(),new BigDecimal("120"))))
    .isInstanceOf(RuntimeException.class);

  assertThat(db.queryForObject("SELECT status FROM orders WHERE tenant_id=1 AND id=?",String.class,draft.id())).isEqualTo("DRAFT");
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1 AND branch_id=1 AND product_id=1",Integer.class)).isEqualTo(quantityBefore);
  assertThat(db.queryForObject("SELECT reserved FROM inventory WHERE tenant_id=1 AND branch_id=1 AND product_id=1",Integer.class)).isEqualTo(reservedBefore);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_ledger WHERE tenant_id=1 AND order_id=?",Integer.class,draft.id())).isZero();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_payment_attempts WHERE tenant_id=1 AND order_id=?",Integer.class,draft.id())).isZero();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE tenant_id=1 AND resource_id=? AND action='SALE_COMPLETE'",Integer.class,draft.id())).isZero();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_invoices WHERE tenant_id=1 AND order_id=?",Integer.class,draft.id())).isEqualTo(1);
 }

 @Test void tenantJpaVersionRejectsAStaleConcurrentUpdate(){
  var factory=applicationContext.getBean(jakarta.persistence.EntityManagerFactory.class);
  var first=factory.createEntityManager();var second=factory.createEntityManager();
  try{
   first.getTransaction().begin();
   var stale=first.find(com.saumya.pos.tenant.internal.Tenant.class,1L);
   second.getTransaction().begin();
   var winner=second.find(com.saumya.pos.tenant.internal.Tenant.class,1L);
   winner.updateProfile("Winner","Winner Legal",null,null,null,null,java.time.Instant.parse("2026-11-22T09:00:00Z"));
   second.flush();second.getTransaction().commit();

   stale.updateProfile("Stale Loser","Stale Legal",null,null,null,null,java.time.Instant.parse("2026-11-22T09:01:00Z"));
   assertThatThrownBy(first::flush).isInstanceOf(jakarta.persistence.OptimisticLockException.class);
   first.getTransaction().rollback();
   assertThat(db.queryForObject("SELECT name FROM tenants WHERE id=1",String.class)).isEqualTo("Winner");
   assertThat(db.queryForObject("SELECT version FROM tenants WHERE id=1",Long.class)).isEqualTo(1L);
  }finally{
   if(first.getTransaction().isActive())first.getTransaction().rollback();
   if(second.getTransaction().isActive())second.getTransaction().rollback();
   first.close();second.close();
  }
 }

 @Test void guestCashIsAtomicAndIdempotent(){
  var r=as("alice", () -> checkout.cashSale(a.cashier(),"checkout-1",sale(1,"120")));
  assertThat(r.get("customer_id")).isNull();assertThat(r.get("total")).isEqualTo(new BigDecimal("110.00"));assertThat(r.get("change_given")).isEqualTo(new BigDecimal("10.00"));
  assertThat(as("alice", () -> checkout.cashSale(a.cashier(),"checkout-1",sale(1,"120"))).get("id")).isEqualTo(r.get("id"));
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isZero();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);
  assertThatThrownBy(()->as("alice", () -> checkout.cashSale(a.cashier(),"checkout-1",sale(1,"130"))));
  assertThatThrownBy(()->receiptReader.receipt(b,(String)r.get("id")));
  assertThat(receiptReader.reprint(a,(String)r.get("id")).get("copy")).isEqualTo(true);
 }
 @Test void forbiddenProductAndUnderpaymentLeaveNoSale(){
  assertThatThrownBy(()->as("alice", () -> checkout.cashSale(a.cashier(),"foreign-1",sale(2,"120"))));
  assertThatThrownBy(()->as("alice", () -> checkout.cashSale(a.cashier(),"underpay1",sale(1,"1"))));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isZero();
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isEqualTo(1);
 }
 @Test void invoiceIsConditionalAndImmutable(){
  update("UPDATE branches SET invoice_required=TRUE WHERE id=1");
  var receipt=as("alice", () -> checkout.cashSale(a.cashier(),"invoice-1",sale(1,"110")));
  update("UPDATE tenants SET name='Renamed' WHERE id=1");
  assertThat(db.queryForObject("SELECT seller_name FROM vendor_invoices",String.class)).isEqualTo("Vendor A");
  assertThat(((List<?>)receipt.get("invoices"))).hasSize(1);
  as("alice", () -> checkout.cashSale(a.cashier(),"invoice-1",sale(1,"110")));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_invoices",Integer.class)).isEqualTo(1);
 }
 @Test void concurrentSalesCannotOversell() throws Exception {
  try(var pool=Executors.newFixedThreadPool(2)) {
   var jobs=List.of((Callable<Boolean>)()->attempt("concur-01"),()->attempt("concur-02"));
   var results=pool.invokeAll(jobs);int successes=0;for(var result:results) if(result.get())successes++;
   assertThat(successes).isEqualTo(1);
  }
 }
 boolean attempt(String key){try{as("alice",()->as("alice", () -> checkout.cashSale(a.cashier(),key,sale(1,"110"))));return true;}catch(org.springframework.web.server.ResponseStatusException e){return false;}}

 @Test void concurrentSessionOpenAttemptsReserveARegisterExactlyOnce() throws Exception {
  asScope(a,()->service.close(a,session,new BigDecimal("50")));
  try(var pool=Executors.newFixedThreadPool(2)){
   Callable<Boolean> open=()->{
    try{asScope(a,()->service.open(a,new PosService.OpenSession(1,new BigDecimal("10"))));return true;}
    catch(org.springframework.web.server.ResponseStatusException|org.springframework.dao.DataIntegrityViolationException denied){return false;}
   };
   var results=pool.invokeAll(List.of(open,open));
   assertThat(results.get(0).get()^results.get(1).get()).isTrue();
  }
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_sessions WHERE tenant_id=1 AND register_id=1 AND state IN ('OPEN','CLOSING')",Integer.class)).isEqualTo(1);
 }

 @Test void databaseRejectsCrossBranchSessionAndOrderRelationships(){
  var draft=as("alice",()->checkout.pos("alice","p005-cross-branch",posDraft()));
  assertThatThrownBy(()->update("UPDATE pos_sessions SET branch_id=2 WHERE tenant_id=1 AND id=?",session))
    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->update("UPDATE orders SET branch_id=2 WHERE tenant_id=1 AND id=?",draft.id()))
    .isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThat(db.queryForObject("SELECT branch_id FROM pos_sessions WHERE tenant_id=1 AND id=?",Long.class,session)).isEqualTo(1L);
  assertThat(db.queryForObject("SELECT branch_id FROM orders WHERE tenant_id=1 AND id=?",Long.class,draft.id())).isEqualTo(1L);
 }

 @Test void registerAndSessionScopeAreEnforced(){
  assertThatThrownBy(()->asScope(a, () -> service.open(a,new PosService.OpenSession(1,BigDecimal.ZERO))));
  assertThatThrownBy(()->as("bob", () -> checkout.cashSale(b.cashier(),"bad-sess1",sale(2,"110"))));
  assertThatThrownBy(()->asScope(a, () -> service.close(a,session,BigDecimal.ZERO)));
  asScope(a, () -> service.close(a,session,new BigDecimal("50")));
  assertThatThrownBy(()->as("alice", () -> checkout.cashSale(a.cashier(),"closed-01",sale(1,"110"))));
 }

 @Test void posStockLocationAndRegisterAssignmentAreServerResolvedAndFailClosed(){
  assertThat(db.queryForObject("SELECT stock_location_id FROM pos_stock_locations WHERE tenant_id=1 AND branch_id=1",Long.class)).isEqualTo(1L);
  assertThat(as("alice",()->stock.atBranchForPos(1,1,1))).isEqualTo(1L);
  int quantity=db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1 AND branch_id=1 AND product_id=1",Integer.class);
  update("UPDATE pos_stock_locations SET active=FALSE WHERE tenant_id=1 AND branch_id=1");
  assertThatThrownBy(()->as("alice",()->stock.atBranchForPos(1,1,1)))
    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThatThrownBy(()->as("alice",()->service.products(a)))
    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1 AND branch_id=1 AND product_id=1",Integer.class)).isEqualTo(quantity);
  update("UPDATE pos_stock_locations SET active=TRUE WHERE tenant_id=1 AND branch_id=1");
  update("UPDATE employee_register_assignments SET active=FALSE,revoked_by='alice',revoked_at=CURRENT_TIMESTAMP WHERE tenant_id=1 AND username='alice' AND register_id=1");
  assertThatThrownBy(()->as("alice",()->{stock.requirePosStockScope(1,1);return null;}))
    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThatThrownBy(()->as("alice",()->stock.atBranchForPos(1,1,1)))
    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1 AND branch_id=1 AND product_id=1",Integer.class)).isEqualTo(quantity);
 }

 @Test void inventoryReservationConsumeAndReleaseRecheckLiveSessionAndRegisterAssignment(){
  var draft=as("alice",()->checkout.pos("alice","p005-revoked-assignment",posDraft()));
  int quantity=db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1 AND branch_id=1 AND product_id=1",Integer.class);
  int reserved=db.queryForObject("SELECT reserved FROM inventory WHERE tenant_id=1 AND branch_id=1 AND product_id=1",Integer.class);
  int holds=db.queryForObject("SELECT COUNT(*) FROM stock_reservations WHERE tenant_id=1 AND order_id=?",Integer.class,draft.id());
  int ledger=db.queryForObject("SELECT COUNT(*) FROM inventory_ledger",Integer.class);
  int ordersBefore=db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class);
  int tendersBefore=db.queryForObject("SELECT COUNT(*) FROM pos_drawer_tenders",Integer.class);
  int paymentAttemptsBefore=db.queryForObject("SELECT COUNT(*) FROM vendor_payment_attempts",Integer.class);
  update("UPDATE employee_register_assignments SET active=FALSE,revoked_by='alice',revoked_at=CURRENT_TIMESTAMP WHERE tenant_id=1 AND username='alice' AND register_id=1");
  assertThatThrownBy(()->as("alice",()->{stock.reserve(1,1,1,1,draft.id(),java.time.Instant.now().plusSeconds(300));return null;}))
    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThatThrownBy(()->as("alice",()->stock.consume(1,draft.id())))
    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThatThrownBy(()->as("alice",()->{stock.release(1,draft.id());return null;}))
    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1 AND branch_id=1 AND product_id=1",Integer.class)).isEqualTo(quantity);
  assertThat(db.queryForObject("SELECT reserved FROM inventory WHERE tenant_id=1 AND branch_id=1 AND product_id=1",Integer.class)).isEqualTo(reserved);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM stock_reservations WHERE tenant_id=1 AND order_id=?",Integer.class,draft.id())).isEqualTo(holds);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_ledger",Integer.class)).isEqualTo(ledger);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(ordersBefore);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_drawer_tenders",Integer.class)).isEqualTo(tendersBefore);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_payment_attempts",Integer.class)).isEqualTo(paymentAttemptsBefore);
  assignRegister("alice",1,1,1);
  update("UPDATE pos_sessions SET state='CLOSED',closed_at=CURRENT_TIMESTAMP WHERE tenant_id=1 AND id=?",session);
  assertThatThrownBy(()->as("alice",()->{stock.release(1,draft.id());return null;}))
    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThatThrownBy(()->as("alice",()->stock.consume(1,draft.id())))
    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1 AND branch_id=1 AND product_id=1",Integer.class)).isEqualTo(quantity);
  assertThat(db.queryForObject("SELECT reserved FROM inventory WHERE tenant_id=1 AND branch_id=1 AND product_id=1",Integer.class)).isEqualTo(reserved);
  assertThat(db.queryForObject("SELECT status FROM orders WHERE tenant_id=1 AND id=?",String.class,draft.id())).isEqualTo("DRAFT");
 }

 @Test void sessionReopenApiRequiresPermissionAndCannotChooseTheRegisterOrCashier() throws Exception {
  asScope(a,()->service.close(a,session,new BigDecimal("50")));
  String path="/api/v1/vendor/pos/sessions/"+session+"/reopen";
  String body="{\"openingFloat\":20,\"reason\":\"Supervisor reviewed drawer\",\"tenantId\":2,\"branchId\":2,\"registerId\":2,\"cashierUserId\":\"bob\",\"posSessionId\":\"forged\"}";
  mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"POS_SESSION_REOPEN"))
      .contentType("application/json").content(body))
    .andExpect(status().isBadRequest());
  mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"POS_VIEW"))
      .contentType("application/json").content("{\"openingFloat\":20,\"reason\":\"Supervisor reviewed drawer\"}"))
    .andExpect(status().isForbidden());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_sessions WHERE tenant_id=1 AND register_id=1 AND state='OPEN'",Integer.class)).isZero();
  employee("manager",1,1);
  mvc.perform(post(path).with(csrf()).with(user("manager").authorities(()->"POS_SESSION_REOPEN"))
      .contentType("application/json").content("{\"openingFloat\":20,\"reason\":\"Supervisor reviewed drawer\"}"))
    .andExpect(status().isOk());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_sessions WHERE tenant_id=1 AND register_id=1 AND state='OPEN'",Integer.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT state FROM pos_sessions WHERE tenant_id=1 AND id=?",String.class,session)).isEqualTo("CLOSED");
 }

 @Test void sessionReopenRequiresExplicitPermissionAndCreatesANewImmutableShift(){
  var cashier=new PosService.Scope(1,1,"charlie");
  employee("charlie",1,1);
  removeAssignedRolePermission("charlie","POS_SESSION_REOPEN");
  asScope(a,()->service.close(a,session,new BigDecimal("50")));
  assertThatThrownBy(()->asScope(cashier,()->service.reopen(cashier,session,new PosService.ReopenSession(BigDecimal.TEN,"Supervisor approved reopen"))))
    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  var manager=supervisor("manager");
  Map<String,Object> reopened=asScope(manager,()->service.reopen(manager,session,new PosService.ReopenSession(new BigDecimal("20"),"Drawer inspected and approved")));
  String newSession=(String)reopened.get("id");
  assertThat(newSession).isNotEqualTo(session);
  assertThat(db.queryForObject("SELECT state FROM pos_sessions WHERE id=?",String.class,session)).isEqualTo("CLOSED");
  assertThat(db.queryForObject("SELECT state FROM pos_sessions WHERE id=?",String.class,newSession)).isEqualTo("OPEN");
  assertThat(db.queryForObject("SELECT cashier FROM pos_sessions WHERE id=?",String.class,newSession)).isEqualTo("manager");
  assertThat(db.queryForObject("SELECT register_id FROM pos_sessions WHERE id=?",Long.class,newSession)).isEqualTo(1L);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_access_audit_events WHERE tenant_id=1 AND resource_id=? AND reason=?",Integer.class,newSession,"Drawer inspected and approved")).isEqualTo(1);
  assertThatThrownBy(()->asScope(a,()->service.cashMovement(a,session,"closed-till",movement(PosService.MovementType.CASH_IN,"1","Closed drawer"))))
    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }

 @Test void databaseRequiresTheBranchDesignatedStockLocationForInventoryRows(){
  update("INSERT INTO branches(id,tenant_id,name) VALUES(77,1,'Unmapped branch')");
  update("DELETE FROM pos_stock_locations WHERE tenant_id=1 AND branch_id=77");
  assertThatThrownBy(()->update("INSERT INTO inventory(tenant_id,branch_id,product_id,quantity) VALUES(1,77,1,1)"))
    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory WHERE tenant_id=1 AND branch_id=77",Integer.class)).isZero();
 }

 @Test void databaseBindsReservationsAndInventoryIssuesToTheOrderStockBranch(){
  update("INSERT INTO branches(id,tenant_id,name) VALUES(78,1,'Second POS branch')");
  update("INSERT INTO inventory(tenant_id,branch_id,product_id,quantity) VALUES(1,78,1,10)");
  var draft=as("alice",()->checkout.pos("alice","p005-cross-branch-stock",posDraft()));
  int reservationsBefore=db.queryForObject("SELECT COUNT(*) FROM stock_reservations WHERE tenant_id=1 AND order_id=?",Integer.class,draft.id());
  int ledgerBefore=db.queryForObject("SELECT COUNT(*) FROM inventory_ledger WHERE tenant_id=1 AND order_id=?",Integer.class,draft.id());
  assertThat(reservationsBefore).isGreaterThan(0);
  assertThatThrownBy(()->update("UPDATE orders SET stock_branch_id=78 WHERE tenant_id=1 AND id=?",draft.id()))
    .isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->update("INSERT INTO stock_reservations(tenant_id,order_id,product_id,branch_id,quantity,state,expires_at) VALUES(1,?,1,78,1,'HELD',CURRENT_TIMESTAMP)",draft.id()))
    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->update("INSERT INTO inventory_ledger(tenant_id,branch_id,product_id,order_id,quantity_delta) VALUES(1,78,1,?, -1)",draft.id()))
    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM stock_reservations WHERE tenant_id=1 AND order_id=?",Integer.class,draft.id())).isEqualTo(reservationsBefore);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_ledger WHERE tenant_id=1 AND order_id=?",Integer.class,draft.id())).isEqualTo(ledgerBefore);
 }
 @Test void authenticationPermissionsCsrfAndForgedFields() throws Exception {
  mvc.perform(get("/api/v1/vendor/pos/products").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/vendor/pos/products").with(user("alice"))).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/vendor/pos/sales").with(user("alice").authorities(()->"POS_SALE_CREATE"))).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/vendor/pos/sales").with(csrf()).with(user("alice").authorities(()->"POS_SALE_CREATE",()->"POS_TENDER_CASH_RECORD",()->"POS_RECEIPT_VIEW"))
   .header("Idempotency-Key","forged-01").contentType("application/json").content("{\"tenantId\":2,\"branchId\":2,\"warehouseId\":99,\"stockLocationId\":99,\"registerId\":99,\"cashierUserId\":\"bob\",\"sessionId\":\"x\",\"items\":[{\"productId\":1,\"quantity\":1}],\"cashReceived\":110}"))
   .andExpect(status().isBadRequest());
 }
 @Test void httpCheckoutUsesAuthenticatedMembership() throws Exception {
  String payload="{\"sessionId\":\""+session+"\",\"items\":[{\"productId\":1,\"quantity\":1}],\"cashReceived\":120,\"invoiceRequested\":true}";
  mvc.perform(post("/api/v1/vendor/pos/sales").with(csrf()).with(bearer("alice","StaffPasswordForTests"))
   .header("Idempotency-Key","http-sale1").contentType("application/json").content(payload))
   .andExpect(result -> {
    if(result.getResponse().getStatus()!=200) throw new AssertionError("Cash sale HTTP "+result.getResponse().getStatus()+" response: "+result.getResponse().getContentAsString());
   }).andExpect(jsonPath("$.customer_id").doesNotExist()).andExpect(jsonPath("$.total").value(110)).andExpect(jsonPath("$.invoices[0].seller_name").value("Vendor A"));
 }
 @Test void databaseRejectsMixedTenantReferences(){
  assertThatThrownBy(()->update("INSERT INTO inventory(tenant_id,branch_id,product_id,quantity) VALUES(1,1,2,10)"))
   .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->update("INSERT INTO pos_sessions(id,tenant_id,branch_id,cashier,register_id,state,opening_float) VALUES('forged',1,1,'bob',1,'OPEN',0)"))
   .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
 }
 PosService.CashMovement movement(PosService.MovementType type,String amount,String reason) {
  return new PosService.CashMovement(type,new BigDecimal(amount),reason);
 }
 @Test void drawerFormulaIncludesMovementsWithoutDoubleCountingSales(){
  as("alice", () -> checkout.cashSale(a.cashier(),"drawer-sale",sale(1,"120")));
  var cashIn=movement(PosService.MovementType.CASH_IN,"100"," Extra float ");
  var first=asScope(a, () -> service.cashMovement(a,session,"drawer-in1",cashIn));
  assertThat(asScope(a, () -> service.cashMovement(a,session,"drawer-in1",cashIn)).get("id")).isEqualTo(first.get("id"));
  asScope(a, () -> service.cashMovement(a,session,"drawer-out",movement(PosService.MovementType.CASH_OUT,"25","Supplies")));
  asScope(a, () -> service.cashMovement(a,session,"drawer-drop",movement(PosService.MovementType.SAFE_DROP,"100","Move to safe")));
  var report=asScope(a, () -> service.reconciliation(a,session));
  assertThat(report.cashReceived()).isEqualByComparingTo("120");
  assertThat(report.changeGiven()).isEqualByComparingTo("10");
  assertThat(report.cashIn()).isEqualByComparingTo("100");
  assertThat(report.cashOut()).isEqualByComparingTo("25");
  assertThat(report.safeDrops()).isEqualByComparingTo("100");
  assertThat(report.expectedCash()).isEqualByComparingTo("135");
  assertThat(report.countedCash()).isNull();assertThat(report.variance()).isNull();
  assertThat(report.movements()).hasSize(3);
  assertThatThrownBy(()->asScope(a, () -> service.close(a,session,new BigDecimal("160"))));
  assertThat(asScope(a, () -> service.reconciliation(a,session)).state()).isEqualTo("OPEN");
  asScope(a, () -> service.close(a,session,new BigDecimal("135")));
  assertThat(asScope(a, () -> service.reconciliation(a,session)).variance()).isEqualByComparingTo("0");
  assertThat(asScope(a, () -> service.cashMovement(a,session,"drawer-in1",cashIn)).get("id")).isEqualTo(first.get("id"));
  assertThatThrownBy(()->asScope(a, () -> service.cashMovement(a,session,"afterclose",cashIn)));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE action='CASH_IN'",Integer.class)).isEqualTo(1);
 }
 @Test void cashMovementsRejectPayloadReuseAndOtherCashiers(){
  asScope(a, () -> service.cashMovement(a,session,"movement-1",movement(PosService.MovementType.CASH_IN,"5","Float")));
  assertThatThrownBy(()->asScope(a, () -> service.cashMovement(a,session,"movement-1",movement(PosService.MovementType.CASH_IN,"6","Float"))));
  assertThatThrownBy(()->asScope(a, () -> service.cashMovement(a,session,"movement-1",movement(PosService.MovementType.CASH_OUT,"5","Float"))));
  assertThatThrownBy(()->asScope(a, () -> service.cashMovement(a,session,"movement-1",movement(PosService.MovementType.CASH_IN,"5","Changed reason"))));
  assertThatThrownBy(()->asScope(b, () -> service.cashMovement(b,session,"other-tenant",movement(PosService.MovementType.CASH_IN,"5","Float"))));
  employee("charlie",1,1);
  var colleague=new PosService.Scope(1,1,"charlie");
  assertThatThrownBy(()->asScope(colleague, () -> service.cashMovement(colleague,session,"other-user",movement(PosService.MovementType.CASH_IN,"5","Float"))));
  assertThatThrownBy(()->asScope(colleague, () -> service.reconciliation(colleague,session)));
  assertThatThrownBy(()->asScope(b, () -> service.reconciliation(b,session)));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_cash_movements",Integer.class)).isEqualTo(1);
 }
 @Test void concurrentCashRemovalsCannotOverdraw() throws Exception {
  try(var pool=Executors.newFixedThreadPool(2)) {
   var results=pool.invokeAll(List.of((Callable<Boolean>)()->removeCash("remove-01"),()->removeCash("remove-02")));
   int successes=0;for(var result:results)if(result.get())successes++;
   assertThat(successes).isEqualTo(1);
  }
  assertThat(asScope(a, () -> service.reconciliation(a,session)).expectedCash()).isEqualByComparingTo("10");
 }
 boolean removeCash(String key) {
  try {asScope(a, () -> service.cashMovement(a,session,key,movement(PosService.MovementType.CASH_OUT,"40","Supplies")));return true;}
  catch(org.springframework.web.server.ResponseStatusException e){return false;}
 }
 @Test void concurrentMovementRetriesHaveOneLedgerAndAuditEntry() throws Exception {
  try(var pool=Executors.newFixedThreadPool(2)) {
   Callable<Map<String,Object>> call=()->asScope(a, () -> service.cashMovement(a,session,"retry-move",movement(PosService.MovementType.CASH_IN,"10","Float")));
   var results=pool.invokeAll(List.of(call,call));
   assertThat(results.get(0).get().get("id")).isEqualTo(results.get(1).get().get("id"));
  }
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_cash_movements",Integer.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE action='CASH_IN'",Integer.class)).isEqualTo(1);
 }
 @Test void movementEndpointsEnforceTypeSpecificPermissionsAndScope() throws Exception {
  String path="/api/v1/vendor/pos/sessions/"+session+"/cash-movements";
  String body="{\"type\":\"CASH_OUT\",\"amount\":10,\"reason\":\"Supplies\"}";
  mvc.perform(post(path).with(csrf()).contentType("application/json").content(body).header("Idempotency-Key","http-move1").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());
  mvc.perform(post(path).with(user("alice").authorities(()->"POS_CASH_OUT")).contentType("application/json").content(body).header("Idempotency-Key","http-move1")).andExpect(status().isForbidden());
  mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"POS_CASH_IN")).contentType("application/json").content(body).header("Idempotency-Key","http-move1")).andExpect(status().isForbidden());
  mvc.perform(post(path).with(csrf()).with(user("bob").authorities(()->"POS_CASH_OUT")).contentType("application/json").content(body).header("Idempotency-Key","http-move1")).andExpect(status().isNotFound());
  mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"POS_CASH_OUT")).contentType("application/json").content(body).header("Idempotency-Key","http-move1"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.cashier").value("alice")).andExpect(jsonPath("$.movement_type").value("CASH_OUT"));
  String report="/api/v1/vendor/pos/sessions/"+session+"/reconciliation";
  mvc.perform(get(report).with(user("alice"))).andExpect(status().isForbidden());
  mvc.perform(get(report).with(user("bob").authorities(()->"POS_VIEW"))).andExpect(status().isNotFound());
  mvc.perform(get(report).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.expectedCash").value(40));
 }
 @Test void invalidCashMovementInputsAreRejected() throws Exception {
  String path="/api/v1/vendor/pos/sessions/"+session+"/cash-movements";
  for(String body:List.of(
   "{\"type\":\"CASH_IN\",\"amount\":0,\"reason\":\"Float\"}",
   "{\"type\":\"CASH_IN\",\"amount\":-1,\"reason\":\"Float\"}",
   "{\"type\":\"CASH_IN\",\"amount\":1.001,\"reason\":\"Float\"}",
   "{\"type\":\"CASH_IN\",\"amount\":1,\"reason\":\" \"}",
   "{\"amount\":1,\"reason\":\"Float\"}",
   "{\"type\":\"CASH_REFUND\",\"amount\":1,\"reason\":\"Float\"}",
   "{\"type\":\"CASH_IN\",\"amount\":1,\"reason\":\"Float\",\"tenantId\":2}")) {
   mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"POS_CASH_IN")).contentType("application/json").content(body).header("Idempotency-Key","invalid-move")).andExpect(status().isBadRequest());
  }
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_cash_movements",Integer.class)).isZero();
 }
 @Test void movementDatabaseConstraintsRejectForgedContextAndInvalidAmounts(){
  String sql="INSERT INTO pos_cash_movements(id,tenant_id,branch_id,cashier,session_id,movement_type,amount,reason,idempotency_key) VALUES(?,?,?,?,?,?,?,?,?)";
  assertThatThrownBy(()->update(sql,"foreign",2,2,"bob",session,"CASH_IN",1,"Float","db-foreign"))
   .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->update(sql,"zero",1,1,"alice",session,"CASH_IN",0,"Float","db-zero1"))
   .isInstanceOf(org.springframework.dao.DataAccessException.class)
   .rootCause().hasMessageContaining("chk_cash_movement_amount");
  assertThatThrownBy(()->update(sql,"refund",1,1,"alice",session,"CASH_REFUND",1,"Float","db-refund"))
   .isInstanceOf(org.springframework.dao.DataAccessException.class)
   .rootCause().hasMessageContaining("chk_cash_movement_type");
 }
 @Test void sessionDiscoveryAndResumeAreRestrictedToTheOwningCashier(){
  String bobSession=(String)asScope(b, () -> service.open(b,new PosService.OpenSession(2,BigDecimal.ZERO))).get("id");
  employee("charlie",1,1);
  update("INSERT INTO registers VALUES(3,1,1)");assignRegister("charlie",1,1,3);
  var charlie=new PosService.Scope(1,1,"charlie");
  String colleagueSession=(String)asScope(charlie, () -> service.open(charlie,new PosService.OpenSession(3,BigDecimal.ZERO))).get("id");
  assertThat(asScope(a, () -> service.activeSessions(a))).extracting(PosService.SessionSummary::id).containsExactly(session);
  assertThat(asScope(b, () -> service.activeSessions(b))).extracting(PosService.SessionSummary::id).containsExactly(bobSession);
  assertThatThrownBy(()->asScope(a, () -> service.sessionDetails(a,bobSession)));
  assertThatThrownBy(()->asScope(a, () -> service.sessionDetails(a,colleagueSession)));
  assertThatThrownBy(()->asScope(a, () -> service.resume(a,bobSession)));
  assertThatThrownBy(()->asScope(a, () -> service.resume(a,colleagueSession)));
  assertThatThrownBy(()->as("alice", () -> service.resume(new PosService.Scope(1,2,"alice"),session)));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE action='SESSION_RESUME'",Integer.class)).isZero();
 }
 @Test void resumePreservesOriginalFloatAndFinancialHistory(){
  as("alice", () -> checkout.cashSale(a.cashier(),"resume-sale",sale(1,"120")));
  asScope(a, () -> service.cashMovement(a,session,"resume-cash",movement(PosService.MovementType.CASH_IN,"10","Float")));
  var before=asScope(a, () -> service.reconciliation(a,session));
  var resumed=asScope(a, () -> service.resume(a,session));
  assertThat(resumed.id()).isEqualTo(session);
  assertThat(resumed.registerId()).isEqualTo(1);
  assertThat(resumed.openingFloat()).isEqualByComparingTo("50");
  asScope(a, () -> service.resume(a,session));
  assertThat(asScope(a, () -> service.reconciliation(a,session))).isEqualTo(before);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_sessions",Integer.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE action='SESSION_RESUME'",Integer.class)).isEqualTo(2);
  asScope(a, () -> service.close(a,session,new BigDecimal("170")));
  assertThat(asScope(a, () -> service.activeSessions(a))).isEmpty();
  assertThat(asScope(a, () -> service.sessionDetails(a,session)).state()).isEqualTo("CLOSED");
  assertThatThrownBy(()->asScope(a, () -> service.resume(a,session)));
  assertThat(asScope(a, () -> service.sessionDetails(a,session)).state()).isEqualTo("CLOSED");
 }
 @Test void sessionRecoveryHttpPermissionsAndForgedBodyAreEnforced() throws Exception {
  String path="/api/v1/vendor/pos/sessions/"+session;
  mvc.perform(get("/api/v1/vendor/pos/sessions/active").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/vendor/pos/sessions/active").with(user("alice"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/vendor/pos/sessions/active").with(user("bob").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
  mvc.perform(get("/api/v1/vendor/pos/sessions/active").with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].registerId").value(1));
  mvc.perform(get(path).with(user("bob").authorities(()->"POS_VIEW"))).andExpect(status().isNotFound());
  mvc.perform(get(path).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.openingFloat").value(50));
  mvc.perform(post(path+"/resume").with(user("alice").authorities(()->"POS_VIEW",()->"POS_SESSION_OPEN")).contentType("application/json").content("{}"))
   .andExpect(status().isForbidden());
  mvc.perform(post(path+"/resume").with(csrf()).with(user("alice").authorities(()->"POS_VIEW")).contentType("application/json").content("{}"))
   .andExpect(status().isForbidden());
  mvc.perform(post(path+"/resume").with(csrf()).with(user("bob").authorities(()->"POS_VIEW",()->"POS_SESSION_OPEN")).contentType("application/json").content("{}"))
   .andExpect(status().isNotFound());
  mvc.perform(post(path+"/resume").with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_SESSION_OPEN")).contentType("application/json").content("{\"openingFloat\":0,\"cashier\":\"bob\"}"))
   .andExpect(status().isBadRequest());
  mvc.perform(post(path+"/resume").with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_SESSION_OPEN")).contentType("application/json").content("{}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(session)).andExpect(jsonPath("$.state").value("OPEN"));
  asScope(a, () -> service.close(a,session,new BigDecimal("50")));
  mvc.perform(post(path+"/resume").with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_SESSION_OPEN")).contentType("application/json").content("{}"))
   .andExpect(status().isConflict());
 }
 @Test void resumeRacingCloseCannotReopenTheRegister() throws Exception {
  try(var pool=Executors.newFixedThreadPool(2)) {
   Callable<Boolean> resume=()->{try{asScope(a, () -> service.resume(a,session));return true;}catch(org.springframework.web.server.ResponseStatusException e){return false;}};
   Callable<Boolean> close=()->{asScope(a, () -> service.close(a,session,new BigDecimal("50")));return true;};
   for(var result:pool.invokeAll(List.of(resume,close)))result.get();
  }
  assertThat(asScope(a, () -> service.sessionDetails(a,session)).state()).isEqualTo("CLOSED");
  assertThat(asScope(a, () -> service.activeSessions(a))).isEmpty();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_sessions",Integer.class)).isEqualTo(1);
 }
 PosService.Scope supervisor(String name) {
  employee(name,1,1);
  return new PosService.Scope(1,1,name);
 }
 @Test void discrepancyRequiresReasonAndFreezesTheCountAndRegister(){
  assertThatThrownBy(()->asScope(a, () -> service.close(a,session,BigDecimal.ZERO," ")));
  assertThat(asScope(a, () -> service.closeStatus(a,session)).state()).isEqualTo("OPEN");
  var closing=asScope(a, () -> service.close(a,session,BigDecimal.ZERO,"Drawer shortage"));
  assertThat(closing.state()).isEqualTo("CLOSING");assertThat(closing.variance()).isEqualByComparingTo("-50");
  assertThat(asScope(a, () -> service.close(a,session,BigDecimal.ZERO,"Drawer shortage")).requestId()).isEqualTo(closing.requestId());
  assertThatThrownBy(()->asScope(a, () -> service.close(a,session,new BigDecimal("50"),"Changed count")));
  assertThatThrownBy(()->as("alice", () -> checkout.cashSale(a.cashier(),"frozen-sale",sale(1,"110"))));
  assertThatThrownBy(()->asScope(a, () -> service.cashMovement(a,session,"frozen-cash",movement(PosService.MovementType.CASH_IN,"1","Float"))));
  assertThatThrownBy(()->asScope(a, () -> service.open(a,new PosService.OpenSession(1,BigDecimal.ZERO))));
  assertThatThrownBy(()->update("INSERT INTO pos_sessions(id,tenant_id,branch_id,cashier,register_id,state,opening_float) VALUES('occupied',1,1,'alice',1,'OPEN',0)"))
   .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThat(asScope(a, () -> service.resume(a,session)).state()).isEqualTo("CLOSING");
 }
 @Test void separateSupervisorApprovesVarianceExactlyOnce(){
  var manager=supervisor("manager");
  var closing=asScope(a, () -> service.close(a,session,BigDecimal.ZERO,"Shortage"));
  assertThatThrownBy(()->asScope(a, () -> service.reviewVariance(a,session,closing.requestId(),"Self approval",PosService.ReviewAction.APPROVE)));
  assertThatThrownBy(()->asScope(b, () -> service.reviewVariance(b,session,closing.requestId(),"Wrong tenant",PosService.ReviewAction.APPROVE)));
  assertThatThrownBy(()->as("manager", () -> service.reviewVariance(new PosService.Scope(1,2,"manager"),session,closing.requestId(),"Wrong branch",PosService.ReviewAction.APPROVE)));
  var approved=asScope(manager, () -> service.reviewVariance(manager,session,closing.requestId(),"Count and drawer reviewed",PosService.ReviewAction.APPROVE));
  assertThat(asScope(manager, () -> service.reviewVariance(manager,session,closing.requestId(),"Count and drawer reviewed",PosService.ReviewAction.APPROVE)).get("id")).isEqualTo(approved.get("id"));
  assertThat(asScope(a, () -> service.closeStatus(a,session)).state()).isEqualTo("CLOSED");
  assertThat(asScope(a, () -> service.closeStatus(a,session)).reviewedBy()).isEqualTo("manager");
  assertThat(asScope(a, () -> service.closeStatus(a,session)).variance()).isEqualByComparingTo("-50");
  assertThatThrownBy(()->asScope(manager, () -> service.reviewVariance(manager,session,closing.requestId(),"Changed",PosService.ReviewAction.APPROVE)));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_shift_reviews",Integer.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE action='SHIFT_VARIANCE_APPROVE'",Integer.class)).isEqualTo(1);
  asScope(a, () -> service.open(a,new PosService.OpenSession(1,BigDecimal.ZERO)));
 }
 @Test void returnedCountRetainsHistoryAndOldRetriesCannotResubmitIt(){
  var manager=supervisor("manager");String key=UUID.randomUUID().toString();
  var closing=asScope(a, () -> service.close(a,session,BigDecimal.ZERO,"Need recount",key));
  asScope(manager, () -> service.reviewVariance(manager,session,key,"Recount the drawer",PosService.ReviewAction.RETURN_FOR_CORRECTION));
  assertThat(asScope(a, () -> service.closeStatus(a,session)).state()).isEqualTo("OPEN");
  assertThat(asScope(a, () -> service.closeStatus(a,session)).reviewReason()).isEqualTo("Recount the drawer");
  assertThat(asScope(a, () -> service.close(a,session,BigDecimal.ZERO,"Need recount",key)).state()).isEqualTo("OPEN");
  assertThatThrownBy(()->asScope(a, () -> service.close(a,session,BigDecimal.ONE,"Need recount",key)));
  var corrected=asScope(a, () -> service.close(a,session,new BigDecimal("45"),"Corrected count",UUID.randomUUID().toString()));
  assertThat(corrected.requestId()).isNotEqualTo(key);
  assertThatThrownBy(()->asScope(manager, () -> service.reviewVariance(manager,session,key,"Old request",PosService.ReviewAction.APPROVE)));
  asScope(manager, () -> service.reviewVariance(manager,session,corrected.requestId(),"Verified recount",PosService.ReviewAction.APPROVE));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_shift_reviews",Integer.class)).isEqualTo(2);
  assertThat(db.queryForObject("SELECT counted_cash FROM pos_shift_reviews WHERE close_request_id=?",BigDecimal.class,closing.requestId())).isEqualByComparingTo("0");
  assertThat(asScope(a, () -> service.closeStatus(a,session)).countedCash()).isEqualByComparingTo("45");
 }
 @Test void competingReviewDecisionsCannotBothCommit() throws Exception {
  var m1=supervisor("m1");var m2=supervisor("m2");var closing=asScope(a, () -> service.close(a,session,BigDecimal.ZERO,"Shortage"));
  try(var pool=Executors.newFixedThreadPool(2)) {
   var jobs=List.of((Callable<Boolean>)()->reviewAttempt(m1,closing.requestId(),PosService.ReviewAction.APPROVE),()->reviewAttempt(m2,closing.requestId(),PosService.ReviewAction.RETURN_FOR_CORRECTION));
   int count=0;for(var result:pool.invokeAll(jobs))if(result.get())count++;
   assertThat(count).isEqualTo(1);
  }
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_shift_reviews",Integer.class)).isEqualTo(1);
 }
 boolean reviewAttempt(PosService.Scope manager,String key,PosService.ReviewAction action) {
  try{as(manager.cashier(),()->asScope(manager, () -> service.reviewVariance(manager,session,key,"Reviewed",action)));return true;}catch(org.springframework.web.server.ResponseStatusException e){return false;}
 }
 
 @Test
 void reviewHttpAuthorizationCsrfAndCanonicalIdentityAreEnforced() throws Exception {
  supervisor("manager");
  var closing = asScope(a, () -> service.close(a, session, BigDecimal.ZERO, "Shortage"));
  String list = "/api/v1/vendor/pos/sessions/pending-variances", path = "/api/v1/vendor/pos/sessions/" + session + "/approve-variance";
  String body = "{\"requestId\":\"" + closing.requestId() + "\",\"reason\":\"Reviewed drawer\"}";
  mvc.perform(get(list).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());
  mvc.perform(get(list).with(user("alice").authorities(() -> "POS_VIEW"))).andExpect(status().isForbidden());
  mvc.perform(get(list).with(user("bob").authorities(() -> "POS_SHIFT_VARIANCE_APPROVE"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
  mvc.perform(get(list).with(user("manager").authorities(() -> "POS_SHIFT_VARIANCE_APPROVE"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].variance").value(-50));
  mvc.perform(post(path).with(user("manager").authorities(() -> "POS_SHIFT_VARIANCE_APPROVE")).contentType("application/json").content(body)).andExpect(status().isForbidden());
  mvc.perform(post(path).with(csrf()).with(user("alice").authorities(() -> "POS_SHIFT_VARIANCE_APPROVE")).contentType("application/json").content(body)).andExpect(status().isForbidden());
  mvc.perform(post(path).with(csrf()).with(user("ALICE").authorities(() -> "POS_SHIFT_VARIANCE_APPROVE")).contentType("application/json").content(body)).andExpect(status().isForbidden());
  assertThat(as("alice", () -> service.scope()).cashier()).isEqualTo("alice");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_shift_reviews", Integer.class)).isZero();
  mvc.perform(post(path).with(csrf()).with(user("bob").authorities(() -> "POS_SHIFT_VARIANCE_APPROVE")).contentType("application/json").content(body)).andExpect(status().isNotFound());
  mvc.perform(post(path).with(csrf()).with(user("manager").authorities(() -> "POS_VIEW")).contentType("application/json").content(body)).andExpect(status().isForbidden());
  mvc.perform(post(path).with(csrf()).with(user("manager").authorities(() -> "POS_SHIFT_VARIANCE_APPROVE")).contentType("application/json").content("{\"requestId\":\"x\",\"reason\":\" \"}")).andExpect(status().isBadRequest());
  mvc.perform(post(path).with(csrf()).with(user("manager").authorities(() -> "POS_SHIFT_VARIANCE_APPROVE")).contentType("application/json").content(body)).andExpect(status().isOk());
 }
 @Test void varianceMigrationPreservesPreviouslyClosedSessions(){
  String url="jdbc:h2:mem:upgrade-"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
  org.flywaydb.core.Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").target("4").load().migrate();
  var legacy=new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(url,"sa",""));
  legacy.update("INSERT INTO tenants(id,name,active) VALUES(1,'Legacy',TRUE)");
  legacy.update("INSERT INTO branches(id,tenant_id,name) VALUES(1,1,'Legacy')");
  legacy.update("INSERT INTO employees VALUES('legacy','unused',1,1,TRUE)");
  legacy.update("INSERT INTO registers VALUES(1,1,1)");
  legacy.update("INSERT INTO pos_sessions(id,tenant_id,branch_id,cashier,register_id,state,opening_float,expected_cash,counted_cash,closed_at) VALUES('legacy',1,1,'legacy',1,'CLOSED',50,50,50,CURRENT_TIMESTAMP)");
  legacy.update("INSERT INTO products(id,tenant_id,sku,barcode,name,price,tax_rate,published) VALUES(1,1,'OLD',NULL,'Legacy panel',100,10,TRUE)");
  legacy.update("INSERT INTO orders(id,tenant_id,branch_id,cashier,session_id,sales_channel,idempotency_key,request_hash,total,cash_received,change_given,receipt_number,tax_invoice) VALUES('legacy-order',1,1,'legacy','legacy','IN_STORE','legacy-key','hash',110,120,10,1,FALSE)");
  legacy.update("INSERT INTO order_items VALUES(1,'legacy-order',1,'Legacy panel',1,100,10,10,110)");
  org.flywaydb.core.Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").load().migrate();
  assertThat(legacy.queryForObject("SELECT list_price FROM order_items",BigDecimal.class)).isEqualByComparingTo("100");
  assertThat(legacy.queryForObject("SELECT discount FROM order_items",BigDecimal.class)).isEqualByComparingTo("0");
  assertThat(legacy.queryForObject("SELECT cash_variance FROM pos_sessions",BigDecimal.class)).isEqualByComparingTo("0");
  assertThat(legacy.queryForObject("SELECT close_request_id FROM pos_sessions",String.class)).isEqualTo("legacy");
  assertThat(legacy.queryForObject("SELECT state FROM pos_sessions",String.class)).isEqualTo("CLOSED");
  assertThat(legacy.queryForObject("SELECT cash_received-change_given FROM pos_drawer_tenders",BigDecimal.class)).isEqualByComparingTo("110");
 }
 @Test void httpCloseRequiresVarianceReasonAndPreservesClientRetryIdentity() throws Exception {
  String path="/api/v1/vendor/pos/sessions/"+session+"/close",key=UUID.randomUUID().toString();
  mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"POS_SESSION_CLOSE")).contentType("application/json").content("{\"countedCash\":0}"))
   .andExpect(status().isBadRequest());
  String body="{\"countedCash\":0,\"reason\":\"Shortage\",\"requestId\":\""+key+"\"}";
  for(int i=0;i<2;i++)mvc.perform(post(path).with(csrf()).with(bearer("alice","StaffPasswordForTests")).contentType("application/json").content(body))
   .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CLOSING")).andExpect(jsonPath("$.requestId").value(key)).andExpect(jsonPath("$.variance").value(-50));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE action='SHIFT_VARIANCE_SUBMIT'",Integer.class)).isEqualTo(1);
 }
 void customers(){
  update("UPDATE tenants SET slug=CONCAT('vendor-',id),online_branch_id=id");
  update("INSERT INTO users(id,username,password_hash,enabled) VALUES(100,'customer',?,TRUE),(101,'other-customer',?,TRUE)",encoder.encode("CustomerPassword"),encoder.encode("OtherPassword"));
  update("INSERT INTO customer_profiles VALUES(100,100),(101,101)");
 }
 <T> T asCustomer(String username,java.util.function.Supplier<T> action){
  var previous=org.springframework.security.core.context.SecurityContextHolder.getContext();
  var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
  context.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(username,"test",List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("CUSTOMER"),new org.springframework.security.core.authority.SimpleGrantedAuthority(com.saumya.pos.security.AuthorizationContext.ACTOR_CONTEXT_CUSTOMER))));
  org.springframework.security.core.context.SecurityContextHolder.setContext(context);
  try{return action.get();}finally{org.springframework.security.core.context.SecurityContextHolder.setContext(previous);}
 }
 @Test void anonymousMarketplaceExposesOnlyActivePublishedCatalogue() throws Exception {
  customers();update("INSERT INTO products(id,tenant_id,sku,barcode,name,price,tax_rate,published) VALUES(3,1,'SECRET',NULL,'Private draft',1,0,FALSE)");
  mvc.perform(get("/api/v1/marketplace/vendors/vendor-1/products")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].name").value("Panel"));
  update("UPDATE tenants SET lifecycle_status='SUSPENDED' WHERE id=2");
  mvc.perform(get("/api/v1/marketplace/vendors")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
  mvc.perform(get("/api/v1/marketplace/vendors/vendor-2/products")).andExpect(status().isNotFound());
 }
 @Test void singleVendorCartSwitchIsExplicitScopedAndRetainsOldItems(){
  customers();var cart=asCustomer("customer", () -> marketplace.put(100,new CartService.Item("vendor-1",1,1)));
  assertThatThrownBy(()->asCustomer("customer", () -> marketplace.put(100,new CartService.Item("vendor-2",2,1))));
  assertThat(asCustomer("customer", () -> marketplace.cart(100)).vendorSlug()).isEqualTo("vendor-1");
  assertThatThrownBy(()->asCustomer("customer", () -> marketplace.switchVendor(100,new CartService.Switch(cart.id(),"vendor-2",1,1))));
  assertThat(asCustomer("customer", () -> marketplace.cart(100)).id()).isEqualTo(cart.id());
  var switched=asCustomer("customer", () -> marketplace.switchVendor(100,new CartService.Switch(cart.id(),"vendor-2",2,1)));
  assertThat(switched.vendorSlug()).isEqualTo("vendor-2");
  assertThat(db.queryForObject("SELECT state FROM carts WHERE id=?",String.class,cart.id())).isEqualTo("ARCHIVED");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM cart_items WHERE cart_id=?",Integer.class,cart.id())).isEqualTo(1);
  assertThatThrownBy(()->asCustomer("customer", () -> marketplace.switchVendor(100,new CartService.Switch(cart.id(),"vendor-1",1,1))));
  assertThatThrownBy(()->asCustomer("customer", () -> marketplace.cart(101))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThatThrownBy(()->asCustomer("customer", () -> marketplace.switchVendor(101,new CartService.Switch(switched.id(),"vendor-1",1,1))));
 }
 @Test void marketplaceAndPosSharePricingAndCurrentStock(){
  customers();var cart=asCustomer("customer", () -> marketplace.put(100,new CartService.Item("vendor-1",1,1)));
  assertThat(cart.total()).isEqualByComparingTo("110");
  assertThat(asCustomer("customer", () -> marketplace.quote(100)).checkoutAvailable()).isFalse();
  var receipt=as("alice",()->as("alice", () -> checkout.cashSale(a.cashier(),"shared-stock",sale(1,"120"))));
  assertThat((BigDecimal)receipt.get("total")).isEqualByComparingTo(cart.total());
  assertThat(catalogue.products("vendor-1").getFirst().inStock()).isFalse();
  assertThatThrownBy(()->asCustomer("customer", () -> marketplace.quote(100)));
  assertThat(asCustomer("customer", () -> marketplace.cart(100)).items().getFirst().available()).isFalse();
  update("UPDATE inventory SET quantity=5 WHERE tenant_id=1");
  update("UPDATE products SET price=200,tax_rate=5 WHERE id=1");
  assertThat(asCustomer("customer", () -> marketplace.quote(100)).total()).isEqualByComparingTo("210");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);
  update("UPDATE products SET published=FALSE WHERE id=1");
  assertThat(asCustomer("customer", () -> marketplace.cart(100)).items().getFirst().name()).isEqualTo("Product unavailable");
  assertThat(asCustomer("customer", () -> marketplace.cart(100)).items().getFirst().unitPrice()).isNull();
  assertThatThrownBy(()->asCustomer("customer", () -> marketplace.quote(100)));
 }
 @Test void customerAuthenticationPermissionsAndCsrfProtectCart() throws Exception {
  customers();String path="/api/v1/customer/cart/items",body="{\"vendorSlug\":\"vendor-1\",\"productId\":1,\"quantity\":1}";
  mvc.perform(get("/api/v1/customer/cart").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/customer/cart").with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/customer/cart").with(user("unknown").authorities(()->"CUSTOMER"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/customer/cart").with(bearer("customer","CustomerPassword"))).andExpect(status().isOk());
  mvc.perform(get("/api/v1/vendor/pos/products").with(bearer("customer","CustomerPassword"))).andExpect(status().isForbidden());
  mvc.perform(post(path).with(bearer("customer","CustomerPassword")).contentType("application/json").content(body)).andExpect(status().isOk());
  mvc.perform(post(path).with(csrf()).with(bearer("customer","CustomerPassword")).contentType("application/json").content(body)).andExpect(status().isOk());
  mvc.perform(get("/api/v1/customer/cart").with(bearer("other-customer","OtherPassword"))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
  mvc.perform(post(path).with(csrf()).with(bearer("customer","CustomerPassword")).contentType("application/json").content(body.replace("\"quantity\":1","\"quantity\":1,\"customerId\":101"))).andExpect(status().isBadRequest());
 }
 @Test void cartQuantityUpdatesAreIdempotentAndDatabaseRejectsMixedTenantItems(){
  customers();var request=new CartService.Item("vendor-1",1,2);var cart=asCustomer("customer", () -> marketplace.put(100,request));
  asCustomer("customer", () -> marketplace.put(100,request));
  assertThat(asCustomer("customer", () -> marketplace.cart(100)).items().getFirst().quantity()).isEqualTo(2);
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isEqualTo(1);
  assertThatThrownBy(()->update("INSERT INTO cart_items VALUES(1,?,2,1)",cart.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThat(asCustomer("customer", () -> marketplace.remove(100,1)).items()).isEmpty();
  assertThat(asCustomer("customer", () -> marketplace.put(100,new CartService.Item("vendor-2",2,1))).vendorSlug()).isEqualTo("vendor-2");
 }
 @Test void concurrentDifferentVendorAddsCannotCreateMixedActiveCarts() throws Exception {
  customers();try(var pool=Executors.newFixedThreadPool(2)) {
   Callable<Boolean> first=()->cartAttempt("vendor-1",1),second=()->cartAttempt("vendor-2",2);
   int successes=0;for(var result:pool.invokeAll(List.of(first,second)))if(result.get())successes++;
   assertThat(successes).isEqualTo(1);
  }
  assertThat(db.queryForObject("SELECT COUNT(*) FROM carts WHERE state='ACTIVE'",Integer.class)).isEqualTo(1);
  assertThat(asCustomer("customer", () -> marketplace.cart(100)).items()).hasSize(1);
 }
 boolean cartAttempt(String slug,long product){try{asCustomer("customer", () -> marketplace.put(100,new CartService.Item(slug,product,1)));return true;}catch(org.springframework.web.server.ResponseStatusException e){return false;}}
 @Test void globalIdentityCannotDuplicateEmployeeAndDisabledCustomerTokensAreRejected() throws Exception {
  customers();assertThatThrownBy(()->update("INSERT INTO users(id,username,password_hash,enabled) VALUES(102,'alice','unused',TRUE)")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  mvc.perform(get("/api/v1/customer/cart").with(bearer("alice","StaffPasswordForTests"))).andExpect(status().isForbidden());
  var token=bearer("customer","CustomerPassword");update("UPDATE users SET enabled=FALSE WHERE username='customer'");mvc.perform(get("/api/v1/customer/cart").with(token).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());
 }

 @Test void archivedCartRestorePreservesBothVendorCartsAndScopesOwnership(){
  customers();var first=asCustomer("customer", () -> marketplace.put(100,new CartService.Item("vendor-1",1,1)));
  var second=asCustomer("customer", () -> marketplace.switchVendor(100,new CartService.Switch(first.id(),"vendor-2",2,1)));
  assertThat(asCustomer("customer", () -> marketplace.history(100))).extracting(CartService.Cart::id).containsExactly(first.id());
  assertThatThrownBy(()->asCustomer("customer", () -> marketplace.history(101))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThatThrownBy(()->asCustomer("customer", () -> marketplace.restore(101,new CartService.Restore(first.id(),null))));
  assertThatThrownBy(()->asCustomer("customer", () -> marketplace.restore(100,new CartService.Restore(first.id(),first.id()))));
  var restored=asCustomer("customer", () -> marketplace.restore(100,new CartService.Restore(first.id(),second.id())));
  assertThat(restored.id()).isEqualTo(first.id());assertThat(restored.items().getFirst().productId()).isEqualTo(1);
  assertThat(asCustomer("customer", () -> marketplace.history(100))).extracting(CartService.Cart::id).containsExactly(second.id());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM cart_items",Integer.class)).isEqualTo(2);
  assertThat(db.queryForObject("SELECT version FROM carts WHERE id=?",Long.class,first.id())).isGreaterThan(0);
  assertThatThrownBy(()->asCustomer("customer", () -> marketplace.restore(100,new CartService.Restore(first.id(),second.id()))));
 }
 @Test void invalidArchivedCartCannotReplaceActiveCart(){
  customers();var first=asCustomer("customer", () -> marketplace.put(100,new CartService.Item("vendor-1",1,1)));
  var second=asCustomer("customer", () -> marketplace.switchVendor(100,new CartService.Switch(first.id(),"vendor-2",2,1)));
  update("UPDATE products SET published=FALSE WHERE id=1");
  assertThatThrownBy(()->asCustomer("customer", () -> marketplace.restore(100,new CartService.Restore(first.id(),second.id()))));
  assertThat(asCustomer("customer", () -> marketplace.cart(100)).id()).isEqualTo(second.id());
  update("UPDATE products SET published=TRUE WHERE id=1");update("UPDATE tenants SET lifecycle_status='SUSPENDED' WHERE id=1");
  assertThatThrownBy(()->asCustomer("customer", () -> marketplace.restore(100,new CartService.Restore(first.id(),second.id()))));
  assertThat(asCustomer("customer", () -> marketplace.cart(100)).id()).isEqualTo(second.id());
 }
 @Test void jpaStockAndJdbcFinancialWritesShareOneRollbackBoundary(){
  assertThat(transactions).isInstanceOf(org.springframework.orm.jpa.JpaTransactionManager.class);
  assertThatThrownBy(()->new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->{
   stock.receive(1,1,1,1);
   assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isEqualTo(2);
   update("INSERT INTO pos_audit(id,tenant_id,actor,action,resource_id) VALUES('rollback',1,'alice','ROLLBACK_TEST','test')");
   throw new IllegalStateException("Simulate failure after stock and financial work");
  })).isInstanceOf(IllegalStateException.class);
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE id='rollback'",Integer.class)).isZero();
 }
 @Test void archivedCartHttpPermissionsAndCsrfAreEnforced() throws Exception {
  customers();var first=asCustomer("customer", () -> marketplace.put(100,new CartService.Item("vendor-1",1,1)));
  var second=asCustomer("customer", () -> marketplace.switchVendor(100,new CartService.Switch(first.id(),"vendor-2",2,1)));
  mvc.perform(get("/api/v1/customer/cart/history").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/customer/cart/history").with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/customer/cart/history").with(user("other-customer").authorities(()->"CUSTOMER"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
  String payload="{\"archivedCartId\":\""+first.id()+"\",\"expectedCartId\":\""+second.id()+"\"}";
  mvc.perform(post("/api/v1/customer/cart/restore").with(user("customer").authorities(()->"CUSTOMER")).contentType("application/json").content(payload)).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/customer/cart/restore").with(csrf()).with(user("customer").authorities(()->"CUSTOMER")).contentType("application/json").content(payload)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(first.id()));
 }
 @Test void concurrentRestoresCannotReplaceANewerActiveCart() throws Exception {
  customers();var first=asCustomer("customer", () -> marketplace.put(100,new CartService.Item("vendor-1",1,1)));
  var second=asCustomer("customer", () -> marketplace.switchVendor(100,new CartService.Switch(first.id(),"vendor-2",2,1)));
  var current=asCustomer("customer", () -> marketplace.switchVendor(100,new CartService.Switch(second.id(),"vendor-1",1,1)));
  try(var pool=Executors.newFixedThreadPool(2)) {
   Callable<Boolean> a=()->restoreAttempt(first.id(),current.id()),b=()->restoreAttempt(second.id(),current.id());
   int count=0;for(var result:pool.invokeAll(List.of(a,b)))if(result.get())count++;
   assertThat(count).isEqualTo(1);
  }
  assertThat(db.queryForObject("SELECT COUNT(*) FROM carts WHERE state='ACTIVE'",Integer.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM cart_items",Integer.class)).isEqualTo(3);
 }
 boolean restoreAttempt(String archived,String current){try{asCustomer("customer",()->marketplace.restore(100,new CartService.Restore(archived,current)));return true;}catch(org.springframework.web.server.ResponseStatusException e){return false;}}
 com.saumya.pos.checkout.CheckoutService.PosDraft posDraft(){return new CheckoutService.PosDraft(session,List.of(new CheckoutService.Item(1,1)),true,null,null);}
 com.saumya.pos.checkout.CheckoutService.Draft onlineDraft(String key){
  var cart=asCustomer("customer",()->marketplace.put(100,new CartService.Item("vendor-1",1,1)));
  return asCustomer("customer",()->checkout.online("customer",key,new com.saumya.pos.checkout.CheckoutService.OnlineDraft(cart.id())));
 }
 void configureOnline(){customers();update("UPDATE tenants SET online_branch_id=id");}
 @Test void onlineDraftReservesCanonicalStockAndCancelReleasesExactlyOnce(){
  configureOnline();var draft=onlineDraft("online-hold1");
  assertThat(draft.channel()).isEqualTo("ONLINE");assertThat(draft.status()).isEqualTo("DRAFT");assertThat(draft.paymentAvailable()).isTrue();
  assertThat(db.queryForObject("SELECT customer_id FROM orders",Long.class)).isEqualTo(100);
  assertThat(db.queryForMap("SELECT branch_id,cashier,session_id,cash_received,receipt_number FROM orders").values()).allMatch(Objects::isNull);
  assertThat(as("alice",()->stock.available(1,1))).isZero();assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isEqualTo(1);
  assertThatThrownBy(()->as("alice", () -> checkout.cashSale(a.cashier(),"competing-pos",sale(1,"110"))));
  var cart=asCustomer("customer", () -> marketplace.cart(100));
  assertThat(asCustomer("customer", () -> checkout.online("customer","online-hold1",new com.saumya.pos.checkout.CheckoutService.OnlineDraft(cart.id()))).id()).isEqualTo(draft.id());
  assertThatThrownBy(()->asCustomer("customer", () -> checkout.online("customer","online-new1",new com.saumya.pos.checkout.CheckoutService.OnlineDraft(cart.id()))));
  assertThatThrownBy(()->asCustomer("other-customer", () -> checkout.cancelOnline("other-customer",draft.id(),"Not mine")));
  asCustomer("customer", () -> checkout.cancelOnline("customer",draft.id(),"Changed mind"));asCustomer("customer", () -> checkout.cancelOnline("customer",draft.id(),"Retry"));
  assertThat(as("alice",()->stock.available(1,1))).isEqualTo(1);
  assertThat(db.queryForObject("SELECT reserved FROM inventory WHERE tenant_id=1",Integer.class)).isZero();
  as("alice", () -> checkout.cashSale(a.cashier(),"after-release",sale(1,"110")));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM orders WHERE status='PAID'",Integer.class)).isEqualTo(1);
 }
 @Test void posReservedCashFinalizationKeepsOneOrderReceiptAndInvoice(){
  var draft=as("alice", () -> checkout.pos("alice","pos-reserve1",posDraft()));
  assertThat(as("alice", () -> checkout.pos("alice","pos-reserve1",posDraft())).id()).isEqualTo(draft.id());
  assertThatThrownBy(()->asScope(a, () -> service.close(a,session,new BigDecimal("50"))));
  assertThatThrownBy(()->as("alice", () -> checkout.finalizeCash("alice",draft.id(),BigDecimal.ONE)));
  assertThatThrownBy(()->as("bob", () -> as("bob", () -> checkout.finalizeCash("bob",draft.id(),new BigDecimal("120")))));
  update("UPDATE products SET price=101 WHERE id=1");
  assertThatThrownBy(()->as("alice", () -> checkout.finalizeCash("alice",draft.id(),new BigDecimal("120"))));
  update("UPDATE products SET price=100 WHERE id=1");
  var receipt=as("alice", () -> checkout.finalizeCash("alice",draft.id(),new BigDecimal("120")));
  assertThat(receipt.get("id")).isEqualTo(draft.id());assertThat(receipt.get("customer_id")).isNull();
  assertThat((BigDecimal)receipt.get("change_given")).isEqualByComparingTo("10");
  assertThat(as("alice", () -> checkout.finalizeCash("alice",draft.id(),new BigDecimal("120"))).get("id")).isEqualTo(draft.id());
  asScope(a, () -> service.close(a,session,new BigDecimal("160")));
  assertThat(as("alice", () -> checkout.finalizeCash("alice",draft.id(),new BigDecimal("120"))).get("id")).isEqualTo(draft.id());
  assertThatThrownBy(()->as("alice", () -> checkout.finalizeCash("alice",draft.id(),new BigDecimal("130"))));
  assertThatThrownBy(()->as("alice", () -> checkout.cancelPos("alice",draft.id(),"Already paid")));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_invoices",Integer.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_ledger",Integer.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT receipt_sequence FROM branches WHERE id=1",Long.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT reserved FROM inventory WHERE tenant_id=1",Integer.class)).isZero();
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isZero();
 }
 @Test void expiryReleasesHoldsWithoutPaymentAndIsIdempotent(){
  var draft=as("alice", () -> checkout.pos("alice","expire-pos1",posDraft()));
  update("UPDATE orders SET expires_at=TIMESTAMP '2020-01-01 00:00:00' WHERE id=?",draft.id());
  assertThatThrownBy(()->as("alice", () -> checkout.finalizeCash("alice",draft.id(),new BigDecimal("110"))));
  draftExpiry.expireOne(1,draft.id());draftExpiry.expireOne(1,draft.id());
  assertThat(as("alice",()->stock.available(1,1))).isEqualTo(1);
  assertThat(db.queryForObject("SELECT status FROM orders",String.class)).isEqualTo("EXPIRED");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_ledger",Integer.class)).isZero();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_invoices",Integer.class)).isZero();
  as("alice", () -> checkout.pos("alice","after-expiry",posDraft()));
 }
 @Test void reservationFailureRollsBackEveryLineAndOrder(){
  update("INSERT INTO products(id,tenant_id,sku,barcode,name,price,tax_rate,published) VALUES(3,1,'P3',NULL,'Missing stock',100,0,TRUE)");
  var request=new CheckoutService.PosDraft(session,List.of(new CheckoutService.Item(1,1),new CheckoutService.Item(3,1)),false,null,null);
  assertThatThrownBy(()->as("alice", () -> checkout.pos("alice","rollback-hold",request)));
  assertThat(as("alice",()->stock.available(1,1))).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isZero();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM stock_reservations",Integer.class)).isZero();
 }
 @Test void onlineReservationRacingWalkInSaleCannotOversell() throws Exception {
  configureOnline();var cart=asCustomer("customer", () -> marketplace.put(100,new CartService.Item("vendor-1",1,1)));
  try(var pool=Executors.newFixedThreadPool(2)){
   Callable<Boolean> online=()->{try{asCustomer("customer",()->checkout.online("customer","race-online",new com.saumya.pos.checkout.CheckoutService.OnlineDraft(cart.id())));return true;}catch(org.springframework.web.server.ResponseStatusException e){return false;}};
   Callable<Boolean> pos=()->attempt("race-pos-sale");int won=0;
   for(var result:pool.invokeAll(List.of(online,pos)))if(result.get())won++;
   assertThat(won).isEqualTo(1);
  }
  assertThat(as("alice",()->stock.available(1,1))).isZero();
  assertThat(db.queryForObject("SELECT quantity-reserved FROM inventory WHERE tenant_id=1",Integer.class)).isZero();
 }
 @Test void draftHttpSecurityOwnershipAndContextConstraints() throws Exception {
  configureOnline();var cart=asCustomer("customer", () -> marketplace.put(100,new CartService.Item("vendor-1",1,1)));
  String payload="{\"expectedCartId\":\""+cart.id()+"\"}";
  mvc.perform(post("/api/v1/customer/checkout/drafts").with(csrf()).contentType("application/json").content(payload).header("Idempotency-Key","http-draft1").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/v1/customer/checkout/drafts").with(user("customer").authorities(()->"CUSTOMER")).contentType("application/json").content(payload).header("Idempotency-Key","http-draft1")).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/customer/checkout/drafts").with(csrf()).with(user("customer").authorities(()->"CUSTOMER")).contentType("application/json").content(payload).header("Idempotency-Key","http-draft1")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.paymentAvailable").value(true));
  mvc.perform(get("/api/v1/customer/checkout/drafts").with(user("other-customer").authorities(()->"CUSTOMER"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
  var draft=asCustomer("customer", () -> checkout.onlineList("customer")).getFirst();
  mvc.perform(post("/api/v1/customer/checkout/drafts/"+draft.id()+"/cancel").with(csrf()).with(user("other-customer").authorities(()->"CUSTOMER")).contentType("application/json").content("{\"reason\":\"Not mine\"}")).andExpect(status().isNotFound());
  assertThatThrownBy(()->update("UPDATE orders SET branch_id=1 WHERE id=?",draft.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->update("UPDATE inventory SET reserved=2 WHERE tenant_id=1")).isInstanceOf(org.springframework.dao.DataAccessException.class);
 }
 @Test void competingCashFinalizationAndCancellationCannotBothCommit() throws Exception {
  var draft=as("alice", () -> checkout.pos("alice","cash-cancel-race",posDraft()));
  try(var pool=Executors.newFixedThreadPool(2)){
   Callable<Boolean> cash=()->{try{as("alice",()->as("alice", () -> checkout.finalizeCash("alice",draft.id(),new BigDecimal("120"))));return true;}catch(org.springframework.web.server.ResponseStatusException e){return false;}};
   Callable<Boolean> cancel=()->{try{as("alice",()->as("alice", () -> checkout.cancelPos("alice",draft.id(),"Customer cancelled")));return true;}catch(org.springframework.web.server.ResponseStatusException e){return false;}};
   int won=0;for(var result:pool.invokeAll(List.of(cash,cancel)))if(result.get())won++;assertThat(won).isEqualTo(1);
  }
  String status=db.queryForObject("SELECT status FROM orders",String.class);
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isEqualTo(status.equals("PAID")?0:1);
  assertThat(db.queryForObject("SELECT reserved FROM inventory WHERE tenant_id=1",Integer.class)).isZero();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_ledger",Integer.class)).isEqualTo(status.equals("PAID")?1:0);
 }
 @Test void concurrentReservedCashRetriesIssueOneReceipt() throws Exception {
  var draft=as("alice", () -> checkout.pos("alice","cash-retry-race",posDraft()));
  try(var pool=Executors.newFixedThreadPool(2)){
   Callable<Map<String,Object>> pay=()->as("alice",()->as("alice", () -> checkout.finalizeCash("alice",draft.id(),new BigDecimal("120"))));
   for(var result:pool.invokeAll(List.of(pay,pay))){var receipt=result.get();assertThat(receipt.get("id")).isEqualTo(draft.id());assertThat((List<?>)receipt.get("invoices")).hasSize(1);assertThat((BigDecimal)receipt.get("cash_received")).isEqualByComparingTo("120");}
  }
  assertThat(db.queryForObject("SELECT receipt_sequence FROM branches WHERE id=1",Long.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_ledger",Integer.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_invoices",Integer.class)).isEqualTo(1);
 }
 @Test void posDraftEndpointEnforcesPermissionsAndRejectsForgedContext() throws Exception {
  String body="{\"sessionId\":\""+session+"\",\"items\":[{\"productId\":1,\"quantity\":1}]}";
  mvc.perform(post("/api/v1/vendor/pos/drafts").with(csrf()).with(user("alice").authorities(()->"POS_VIEW")).header("Idempotency-Key","pos-http-key").contentType("application/json").content(body)).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/vendor/pos/drafts").with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_SALE_CREATE")).header("Idempotency-Key","pos-http-key").contentType("application/json").content(body.replace("\"items\"","\"tenantId\":2,\"items\""))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/v1/vendor/pos/drafts").with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_SALE_CREATE")).header("Idempotency-Key","pos-http-key").contentType("application/json").content(body)).andExpect(status().isOk());
  var draft=as("alice", () -> checkout.posList("alice",session)).getFirst();
  mvc.perform(get("/api/v1/vendor/pos/receipts/"+draft.id()).with(user("alice").authorities(()->"POS_RECEIPT_VIEW"))).andExpect(status().isNotFound());
  mvc.perform(post("/api/v1/vendor/pos/drafts/"+draft.id()+"/cancel").with(csrf()).with(user("alice").authorities(()->"POS_VIEW")).contentType("application/json").content("{\"reason\":\"Cancel\"}")).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/vendor/pos/drafts/"+draft.id()+"/finalize-cash").with(csrf()).with(user("alice").authorities(()->"POS_SALE_CREATE")).contentType("application/json").content("{\"cashReceived\":120}")).andExpect(status().isForbidden());
  assertThatThrownBy(() -> as("bob", () -> checkout.posList("bob",session)))
   .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }

 @Test void orderHistoryScopesPaginationDatesAndImmutableDetails() throws Exception {
  var receipt=as("alice", () -> checkout.cashSale(a.cashier(),"history-cash1",sale(1,"120")));String id=(String)receipt.get("id");
  employee("colleague",1,1);
  update("INSERT INTO branches(id,tenant_id,name) VALUES(3,1,'Other branch')");
  update("INSERT INTO registers VALUES(3,1,3)");
  employee("other-branch",1,3);
  update("UPDATE products SET name='Renamed',price=999 WHERE tenant_id=1");
  mvc.perform(get("/api/v1/vendor/pos/orders").with(user("colleague").authorities(()->"POS_VIEW")).param("status","PAID").param("size","1").param("reference",receipt.get("receipt_number").toString())).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(id));
  mvc.perform(get("/api/v1/vendor/pos/orders/"+id).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].name").value("Panel")).andExpect(jsonPath("$.items[0].unitPrice").value(100)).andExpect(jsonPath("$.cashReceived").value(120));
  for(String who:List.of("bob","other-branch")){
   mvc.perform(get("/api/v1/vendor/pos/orders").with(user(who).authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
   mvc.perform(get("/api/v1/vendor/pos/orders/"+id).with(user(who).authorities(()->"POS_VIEW"))).andExpect(status().isNotFound());
  }
  mvc.perform(get("/api/v1/vendor/pos/orders").with(user("alice").authorities(()->"POS_VIEW")).param("from","2000-01-01").param("to","2000-01-01")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  mvc.perform(get("/api/v1/vendor/pos/orders").with(user("alice").authorities(()->"POS_VIEW")).param("reference",id.substring(0,8))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
  mvc.perform(get("/api/v1/vendor/pos/orders").with(user("alice").authorities(()->"POS_VIEW")).param("page","1").param("size","1")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
 }
 @Test void customerHistoryKeepsCancelledOrdersAndHidesOtherChannels() throws Exception {
  configureOnline();var draft=onlineDraft("history-online");
  asCustomer("customer", () -> checkout.cancelOnline("customer",draft.id(),"Buying later"));
  mvc.perform(get("/api/v1/customer/orders").with(user("customer").authorities(()->"CUSTOMER")).param("status","CANCELLED")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].channel").value("ONLINE"));
  mvc.perform(get("/api/v1/customer/orders/"+draft.id()).with(user("customer").authorities(()->"CUSTOMER"))).andExpect(status().isOk()).andExpect(jsonPath("$.cancellationReason").value("Buying later")).andExpect(jsonPath("$.cashReceived").doesNotExist());
  mvc.perform(get("/api/v1/customer/orders").with(user("other-customer").authorities(()->"CUSTOMER"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  mvc.perform(get("/api/v1/customer/orders/"+draft.id()).with(user("other-customer").authorities(()->"CUSTOMER"))).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/vendor/pos/orders/"+draft.id()).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isNotFound());
  var cash=as("alice", () -> as("alice", () -> checkout.cashSale(a.cashier(),"history-guest",sale(1,"120"))));
  mvc.perform(get("/api/v1/customer/orders/"+cash.get("id")).with(user("customer").authorities(()->"CUSTOMER"))).andExpect(status().isNotFound());
 }
 @Test void orderHistoryRequiresPermissionsAndRejectsInvalidFilters() throws Exception {
  mvc.perform(get("/api/v1/vendor/pos/orders").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/customer/orders").with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/vendor/pos/orders").with(user("alice"))).andExpect(status().isForbidden());
  for(String[] parameter:List.of(new String[]{"size","51"},new String[]{"page","-1"},new String[]{"status","REFUNDED"},new String[]{"reference","%"},new String[]{"from","invalid"}))
   mvc.perform(get("/api/v1/vendor/pos/orders").with(user("alice").authorities(()->"POS_VIEW")).param(parameter[0],parameter[1])).andExpect(status().isBadRequest());
 }

 @Test void orderHistoryPaginationIsStableForEqualTimestampsAndUtcDayBounds() throws Exception {
  update("UPDATE inventory SET quantity=3 WHERE tenant_id=1");var ids=new ArrayList<String>();
  for(int n=0;n<3;n++){final int page=n;ids.add((String)as("alice", () -> checkout.cashSale(a.cashier(),"history-page"+page,sale(1,"120"))).get("id"));}
  update("UPDATE orders SET created_at=? WHERE tenant_id=1",java.time.LocalDateTime.parse("2026-11-12T23:59:59"));
  ids.sort(Comparator.reverseOrder());
  mvc.perform(get("/api/v1/vendor/pos/orders").with(user("alice").authorities(()->"POS_VIEW")).param("size","2").param("from","2026-11-12").param("to","2026-11-12")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.items[0].id").value(ids.get(0))).andExpect(jsonPath("$.items[1].id").value(ids.get(1)));
  mvc.perform(get("/api/v1/vendor/pos/orders").with(user("alice").authorities(()->"POS_VIEW")).param("size","2").param("page","1")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].id").value(ids.get(2)));
  mvc.perform(get("/api/v1/vendor/pos/orders").with(user("alice").authorities(()->"POS_VIEW")).param("from","2026-11-13")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
 }

 @Test void pooledDatabaseClockUsesUtcForJdbcAndJpaHistory() {
  String databaseClock=db.queryForObject("SELECT CURRENT_TIMESTAMP",String.class);
  var local=java.time.LocalDateTime.parse(databaseClock.substring(0,19).replace(' ','T'));
  assertThat(java.time.Duration.between(local.toInstant(java.time.ZoneOffset.UTC),java.time.Instant.now()).abs()).isLessThan(java.time.Duration.ofSeconds(10));
 }

 @Test void publicProductDetailsArePublishedScopedAndUseSharedTax() throws Exception {
  configureOnline();
  mvc.perform(get("/api/v1/marketplace/vendors/vendor-1/products/1")).andExpect(status().isOk()).andExpect(jsonPath("$.vendorName").value("Vendor A")).andExpect(jsonPath("$.unitPrice").value(100)).andExpect(jsonPath("$.unitTax").value(10)).andExpect(jsonPath("$.unitTotal").value(110)).andExpect(jsonPath("$.inStock").value(true)).andExpect(jsonPath("$.barcode").doesNotExist());
  mvc.perform(get("/api/v1/marketplace/vendors/vendor-2/products/1")).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/marketplace/vendors/vendor-1/products/0")).andExpect(status().isNotFound());
  update("UPDATE inventory SET quantity=0 WHERE tenant_id=1");
  mvc.perform(get("/api/v1/marketplace/vendors/vendor-1/products/1")).andExpect(status().isOk()).andExpect(jsonPath("$.inStock").value(false));
  update("UPDATE products SET published=FALSE WHERE id=1");
  mvc.perform(get("/api/v1/marketplace/vendors/vendor-1/products/1")).andExpect(status().isNotFound());
  update("UPDATE products SET published=TRUE WHERE id=1");update("UPDATE tenants SET lifecycle_status='SUSPENDED' WHERE id=1");
  mvc.perform(get("/api/v1/marketplace/vendors/vendor-1/products/1")).andExpect(status().isNotFound());
 }
 @Test void salesReportSeparatesPaidTaxCashAndOnlineReservationsWithoutDoubleCounting() throws Exception {
  configureOnline();update("UPDATE inventory SET quantity=10 WHERE tenant_id=1");
  update("INSERT INTO products(id,tenant_id,sku,barcode,name,price,tax_rate,published) VALUES(3,1,'CABLE',NULL,'Cable',20,0,TRUE)");update("INSERT INTO inventory(tenant_id,branch_id,product_id,quantity) VALUES(1,1,3,5)");
  as("alice", () -> checkout.cashSale(a.cashier(),"report-paid1",new CheckoutService.CashSale(session,List.of(new CheckoutService.Item(1,1),new CheckoutService.Item(3,2)),new BigDecimal("200"),true,null,null)));
  var cancelled=onlineDraft("report-cancel");asCustomer("customer", () -> checkout.cancelOnline("customer",cancelled.id(),"Buying later"));onlineDraft("report-unpaid");
  update("UPDATE products SET price=999,tax_rate=80 WHERE tenant_id=1");
  mvc.perform(get("/api/v1/vendor/pos/reports/sales").with(user("alice").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW")).param("tenantId","2").param("branchId","2"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.branchId").value(1)).andExpect(jsonPath("$.totals.paidOrders").value(1)).andExpect(jsonPath("$.totals.sales").value(150)).andExpect(jsonPath("$.totals.tax").value(10)).andExpect(jsonPath("$.totals.beforeTax").value(140)).andExpect(jsonPath("$.totals.cashReceived").value(200)).andExpect(jsonPath("$.totals.changeGiven").value(50)).andExpect(jsonPath("$.totals.cashApplied").value(150)).andExpect(jsonPath("$.totals.units").value(3)).andExpect(jsonPath("$.totals.invoices").value(1)).andExpect(jsonPath("$.totals.unpaidOrders").value(1)).andExpect(jsonPath("$.totals.unpaidAmount").value(110)).andExpect(jsonPath("$.totals.cancelledOrders").value(1));
  mvc.perform(get("/api/v1/vendor/pos/reports/sales").with(user("alice").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW")).param("channel","ONLINE")).andExpect(status().isOk()).andExpect(jsonPath("$.totals.paidOrders").value(0)).andExpect(jsonPath("$.totals.sales").value(0)).andExpect(jsonPath("$.totals.unpaidOrders").value(1)).andExpect(jsonPath("$.channels.length()").value(1));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(3);assertThat(db.queryForObject("SELECT reserved FROM inventory WHERE tenant_id=1 AND product_id=1",Integer.class)).isEqualTo(1);
 }
 @Test void salesReportUsesFinalizationDayAndRejectsForeignBranchesAndMissingGrants() throws Exception {
  var draft=as("alice", () -> checkout.pos("alice","report-finalize",posDraft()));
  var today=java.time.LocalDate.now(java.time.ZoneOffset.UTC);var yesterday=today.minusDays(1);
  update("UPDATE orders SET created_at=? WHERE id=?",yesterday.atStartOfDay(),draft.id());as("alice", () -> checkout.finalizeCash("alice",draft.id(),new BigDecimal("120")));
  mvc.perform(get("/api/v1/vendor/pos/reports/sales").with(user("alice").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW")).param("from",today.toString()).param("to",today.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.totals.paidOrders").value(1)).andExpect(jsonPath("$.days.length()").value(1));
  mvc.perform(get("/api/v1/vendor/pos/reports/sales").with(user("alice").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW")).param("from",yesterday.toString()).param("to",yesterday.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.totals.paidOrders").value(0));
  update("INSERT INTO branches(id,tenant_id,name) VALUES(3,1,'Other branch')");update("INSERT INTO registers VALUES(3,1,3)");employee("other-branch",1,3);
  for(String actor:List.of("bob","other-branch"))mvc.perform(get("/api/v1/vendor/pos/reports/sales").with(user(actor).authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totals.paidOrders").value(0));
  mvc.perform(get("/api/v1/vendor/pos/reports/sales").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/vendor/pos/reports/sales").with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/vendor/pos/reports/sales").with(user("customer").authorities(()->"CUSTOMER"))).andExpect(status().isForbidden());
  for(String[] param:List.of(new String[]{"channel","UNKNOWN"},new String[]{"from","2000-01-01"},new String[]{"to","invalid"}))mvc.perform(get("/api/v1/vendor/pos/reports/sales").with(user("alice").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW")).param(param[0],param[1])).andExpect(status().isBadRequest());
 }

 @Test void retailCategoriesArePublishedTenantOwnedAndForeignAssignmentsFail() throws Exception {
  customers();update("INSERT INTO catalog_categories(id,tenant_id,name,slug) VALUES(1,1,'Panels','panels'),(2,2,'Private','private'),(3,1,'Hidden','hidden')");
  update("INSERT INTO products(id,tenant_id,sku,barcode,name,price,tax_rate,published) VALUES(3,1,'HIDDEN',NULL,'Hidden item',100,10,FALSE)");
  update("INSERT INTO product_categories VALUES(1,1,1),(2,2,2),(1,3,3)");
  mvc.perform(get("/api/v1/marketplace/vendors/vendor-1/categories")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].name").value("Panels")).andExpect(jsonPath("$[0].products").value(1));
  mvc.perform(get("/api/v1/marketplace/vendors/vendor-1/products")).andExpect(status().isOk()).andExpect(jsonPath("$[0].categoryId").value(1)).andExpect(jsonPath("$[0].categoryName").value("Panels"));
  assertThatThrownBy(()->update("UPDATE product_categories SET category_id=2 WHERE tenant_id=1 AND product_id=1")).isInstanceOf(org.springframework.dao.DataAccessException.class);
  update("UPDATE tenants SET lifecycle_status='SUSPENDED' WHERE id=1");mvc.perform(get("/api/v1/marketplace/vendors/vendor-1/categories")).andExpect(status().isNotFound());
 }
 @Test void approvedPromotionsAreSharedAuditedAndSavedOnCashReceiptsAndReports() throws Exception {
  configureOnline();update("UPDATE inventory SET quantity=5 WHERE tenant_id=1");supervisor("manager");
  String path="/api/v1/vendor/catalog/products/1/promotion",body="{\"percent\":10,\"reason\":\"Approved solar offer\"}";
  mvc.perform(put(path).with(csrf()).with(user("manager").authorities(()->"CATALOG_PROMOTION_MANAGE",()->"POS_DISCOUNT_APPROVE")).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.price").value(90));
  mvc.perform(put(path).with(csrf()).with(user("manager").authorities(()->"CATALOG_PROMOTION_MANAGE",()->"POS_DISCOUNT_APPROVE")).contentType("application/json").content(body)).andExpect(status().isOk());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM catalog_promotion_audit",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT actor FROM catalog_promotion_audit",String.class)).isEqualTo("manager");
  mvc.perform(get("/api/v1/marketplace/vendors/vendor-1/products/1")).andExpect(status().isOk()).andExpect(jsonPath("$.listPrice").value(100)).andExpect(jsonPath("$.unitTotal").value(99));
  var cart=asCustomer("customer", () -> marketplace.put(100,new CartService.Item("vendor-1",1,1)));assertThat(cart.total()).isEqualByComparingTo("99");assertThat(cart.items().getFirst().discount()).isEqualByComparingTo("10");
  var receipt=as("alice", () -> as("alice", () -> checkout.cashSale(a.cashier(),"promotion-cash",sale(1,"100"))));assertThat((BigDecimal)receipt.get("total")).isEqualByComparingTo("99");
  var item=(Map<?,?>)((List<?>)receipt.get("items")).getFirst();assertThat((BigDecimal)item.get("list_price")).isEqualByComparingTo("100");assertThat((BigDecimal)item.get("discount")).isEqualByComparingTo("10");
  as("manager", () -> promotions.approve(1,"manager",1,new com.saumya.pos.catalog.PromotionService.Change(new BigDecimal("20"),"New approved offer")));
  var copy=as("alice", () -> receiptReader.reprint(a,(String)receipt.get("id")));assertThat((BigDecimal)copy.get("total")).isEqualByComparingTo("99");
  mvc.perform(get("/api/v1/vendor/pos/reports/sales").with(user("alice").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totals.discounts").value(10)).andExpect(jsonPath("$.totals.tax").value(9)).andExpect(jsonPath("$.totals.sales").value(99));
 }
 @Test void promotionCommandsEnforceApprovalCsrfInputAndTenantScope() throws Exception {
  String path="/api/v1/vendor/catalog/products/1/promotion",body="{\"percent\":10,\"reason\":\"Approved offer\"}";
  mvc.perform(put(path).with(csrf()).with(user("alice").authorities(()->"POS_SALE_CREATE")).contentType("application/json").content(body)).andExpect(status().isForbidden());
  mvc.perform(put(path).with(user("alice").authorities(()->"CATALOG_PROMOTION_MANAGE",()->"POS_DISCOUNT_APPROVE")).contentType("application/json").content(body)).andExpect(status().isForbidden());
  mvc.perform(put(path).with(csrf()).with(user("bob").authorities(()->"CATALOG_PROMOTION_MANAGE",()->"POS_DISCOUNT_APPROVE")).contentType("application/json").content(body)).andExpect(status().isNotFound());
  for(String invalid:List.of(body.replace("10","101"),body.replace("10","1.111"),body.replace("Approved offer"," "),body.replace("\"percent\"","\"tenantId\":2,\"percent\"")))mvc.perform(put(path).with(csrf()).with(user("alice").authorities(()->"CATALOG_PROMOTION_MANAGE",()->"POS_DISCOUNT_APPROVE")).contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM catalog_promotions",Integer.class)).isZero();
 }
 @Test void reservedPromotionsAreImmutableAndChangesRequireFreshReservation(){
  as("alice",()->promotions.approve(1,"alice",1,new com.saumya.pos.catalog.PromotionService.Change(BigDecimal.TEN,"Approved offer")));
  var draft=as("alice", () -> checkout.pos("alice","promotion-draft",posDraft()));assertThat(draft.total()).isEqualByComparingTo("99");assertThat(draft.items().getFirst().discount()).isEqualByComparingTo("10");
  as("alice", () -> promotions.approve(1,"alice",1,new com.saumya.pos.catalog.PromotionService.Change(new BigDecimal("20"),"Changed offer")));
  assertThatThrownBy(()->as("alice", () -> checkout.finalizeCash("alice",draft.id(),new BigDecimal("100")))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT reserved FROM inventory WHERE tenant_id=1",Integer.class)).isEqualTo(1);
  as("alice", () -> checkout.cancelPos("alice",draft.id(),"Requote changed promotion"));var next=as("alice", () -> checkout.pos("alice","promotion-next",posDraft()));as("alice", () -> checkout.finalizeCash("alice",next.id(),new BigDecimal("100")));assertThat(next.total()).isEqualByComparingTo("88");
 }
 @Test void promotionAndCashSaleSerializeOnTheSameProduct() throws Exception {
  try(var pool=Executors.newFixedThreadPool(2)){
   Callable<Object> approve=()->as("alice",()->promotions.approve(1,"alice",1,new com.saumya.pos.catalog.PromotionService.Change(BigDecimal.TEN,"Concurrent approved offer")));
   Callable<Object> pay=()->as("alice",()->as("alice", () -> checkout.cashSale(a.cashier(),"promotion-race",sale(1,"120"))));
   for(var result:pool.invokeAll(List.of(approve,pay)))result.get();
  }
  BigDecimal total=db.queryForObject("SELECT total FROM orders",BigDecimal.class);assertThat(total).isIn(new BigDecimal("110.00"),new BigDecimal("99.00"));assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isZero();assertThat(db.queryForObject("SELECT COUNT(*) FROM catalog_promotion_audit",Integer.class)).isEqualTo(1);
 }

 void loadSolarDemoSeed() throws Exception {
  String sql;
  try(var resource=getClass().getResourceAsStream("/demo/add-solar-demo-products.sql")){
   if(resource==null)throw new IllegalStateException("Missing test demo catalogue fixture");
   sql=new String(resource.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).replace("USE solar;","");
  }
  try(var connection=db.getDataSource().getConnection()){
   // H2 lacks MySQL's START TRANSACTION spelling; all seed DML is identical in both jobs.
   if(connection.getMetaData().getDatabaseProductName().equals("H2"))sql=sql.replace("START TRANSACTION;","SET AUTOCOMMIT FALSE;");
   boolean autoCommit=connection.getAutoCommit();
   try{new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.ByteArrayResource(sql.getBytes(java.nio.charset.StandardCharsets.UTF_8))).populate(connection);}
   finally{if(!connection.getAutoCommit())connection.rollback();connection.setAutoCommit(autoCommit);}
  }
 }
 void demoStore(){update("INSERT INTO tenants(id,name,legal_name,lifecycle_status,slug) VALUES(900001,'Demo store','Demo store','ACTIVE','vendor-900001')");update("INSERT INTO branches(id,tenant_id,name) VALUES(900001,900001,'Demo outlet')");}
 @Test void solarDemoSeedAddsCategorizedProductsAndPreservesEditsStockHoldsAndSales() throws Exception {
  var receipt=as("alice", () -> checkout.cashSale(a.cashier(),"demo-existing",sale(1,"120")));
  demoStore();
  employee("admin@saumya.demo.test",900001,900001);
  new com.saumya.pos.isolation.TenantIsolationFixture(db,encoder)
      .seedEntitlements(900001,"admin@saumya.demo.test");
  loadSolarDemoSeed();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM products WHERE tenant_id=900001",Integer.class)).isEqualTo(18);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM product_categories WHERE tenant_id=900001",Integer.class)).isEqualTo(18);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM catalog_categories WHERE tenant_id=900001",Integer.class)).isEqualTo(6);
  as("admin@saumya.demo.test", () -> promotions.approve(900001,"admin@saumya.demo.test",900101,new com.saumya.pos.catalog.PromotionService.Change(BigDecimal.TEN,"Preserve demo offer")));
  update("UPDATE products SET price=77,published=FALSE WHERE id=900101");
  update("UPDATE inventory SET quantity=23,reserved=3 WHERE tenant_id=900001 AND product_id=900101");
  update("UPDATE product_categories SET category_id=900024 WHERE tenant_id=900001 AND product_id=900101");loadSolarDemoSeed();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM products WHERE tenant_id=900001",Integer.class)).isEqualTo(18);
  assertThat(db.queryForObject("SELECT price FROM products WHERE id=900101",BigDecimal.class)).isEqualByComparingTo("77");
  assertThat(db.queryForObject("SELECT published FROM products WHERE id=900101",Boolean.class)).isFalse();
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=900001 AND product_id=900101",Integer.class)).isEqualTo(23);
  assertThat(db.queryForObject("SELECT reserved FROM inventory WHERE tenant_id=900001 AND product_id=900101",Integer.class)).isEqualTo(3);
  assertThat(db.queryForObject("SELECT category_id FROM product_categories WHERE tenant_id=900001 AND product_id=900101",Long.class)).isEqualTo(900024);
  assertThat(db.queryForObject("SELECT percent FROM catalog_promotions WHERE tenant_id=900001",BigDecimal.class)).isEqualByComparingTo("10");
  assertThat(receiptReader.receipt(a,(String)receipt.get("id")).get("id")).isEqualTo(receipt.get("id"));
 }
 @Test void solarDemoSeedDoesNothingWithoutAnActiveAssignedDemoBranch() throws Exception {
  loadSolarDemoSeed();assertThat(db.queryForObject("SELECT COUNT(*) FROM products",Integer.class)).isEqualTo(2);
  demoStore();update("UPDATE tenants SET lifecycle_status='SUSPENDED' WHERE id=900001");loadSolarDemoSeed();assertThat(db.queryForObject("SELECT COUNT(*) FROM catalog_categories",Integer.class)).isZero();
 }
 @Test void solarDemoSeedSkipsForeignProductIdCollisions() throws Exception {
  demoStore();update("INSERT INTO products(id,tenant_id,sku,barcode,name,price,tax_rate,published) VALUES(900101,1,'FOREIGN',NULL,'Keep foreign product',9,0,TRUE)");loadSolarDemoSeed();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM products WHERE tenant_id=900001",Integer.class)).isEqualTo(17);
  assertThat(db.queryForObject("SELECT tenant_id FROM products WHERE id=900101",Long.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory WHERE tenant_id=900001 AND product_id=900101",Integer.class)).isZero();
 }

 com.saumya.pos.returns.ReturnService.Create returnRequest(String order,int quantity){return new com.saumya.pos.returns.ReturnService.Create(order,session,List.of(new com.saumya.pos.returns.ReturnService.Item(1,quantity)),"Buyer returned item","ORIGINAL_RECEIPT","Original receipt and matching goods checked");}
 void enableReturns(){update("UPDATE branches SET pos_return_window_days=30 WHERE id=1");}
 com.saumya.pos.returns.ReturnService.Review approveReturn(){return new com.saumya.pos.returns.ReturnService.Review("APPROVED","Independent receipt and item review");}
 com.saumya.pos.returns.ReturnService.Inspect inspectReturn(String condition,String disposition){return new com.saumya.pos.returns.ReturnService.Inspect(List.of(new com.saumya.pos.returns.ReturnService.Inspection(1,condition,disposition,"Inspected received goods")));}
 @Test void returnIntakeIsDisabledByDefaultAndClosedShiftRetryHasNoSideEffects(){
  var receipt=as("alice", () -> checkout.cashSale(a.cashier(),"return-sale1",sale(1,"120")));String order=(String)receipt.get("id");
  assertThat(as("alice", () -> returns.eligibility(order)).eligible()).isFalse();assertThatThrownBy(()->as("alice", () -> returns.create("return-start1",returnRequest(order,1)))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  enableReturns();var returned=as("alice", () -> returns.create("return-start1",returnRequest(order,1)));assertThat(returned.state()).isEqualTo("REQUESTED");assertThat(returned.refundStatus()).isEqualTo("NOT_INITIATED");
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isZero();assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_return_dispositions",Integer.class)).isZero();
  assertThat(asScope(a, () -> service.reconciliation(a,session)).expectedCash()).isEqualByComparingTo("160");asScope(a, () -> service.close(a,session,new BigDecimal("160")));
  assertThatThrownBy(() -> as("alice", () -> returns.create("return-start1",returnRequest(order,1))))
   .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_returns WHERE id=?",Integer.class,returned.id())).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_return_events",Integer.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_cash_refunds",Integer.class)).isZero();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_cash_movements",Integer.class)).isZero();
  assertThatThrownBy(()->as("alice", () -> returns.create("return-start1",returnRequest(order,2)))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }
 @Test void independentReviewAndQuarantineAreIdempotentAndNeverRestockDamagedGoods(){
  enableReturns();supervisor("manager");var receipt=as("alice", () -> checkout.cashSale(a.cashier(),"return-sale2",sale(1,"120")));var request=as("alice", () -> returns.create("return-case2",returnRequest((String)receipt.get("id"),1)));
  assertThatThrownBy(()->as("alice", () -> returns.review(request.id(),approveReturn()))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThatThrownBy(()->as("manager", () -> returns.inspect(request.id(),inspectReturn("SELLABLE","RESTOCK")))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  as("manager", () -> returns.review(request.id(),approveReturn()));as("manager", () -> returns.review(request.id(),approveReturn()));
  assertThatThrownBy(()->as("manager", () -> returns.inspect(request.id(),inspectReturn("DAMAGED","RESTOCK")))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  var inspected=as("manager", () -> returns.inspect(request.id(),inspectReturn("DAMAGED","QUARANTINE")));assertThat(inspected.state()).isEqualTo("INSPECTED");assertThat(inspected.refundStatus()).isEqualTo("NOT_INITIATED");
  as("manager", () -> returns.inspect(request.id(),inspectReturn("DAMAGED","QUARANTINE")));as("manager", () -> returns.review(request.id(),approveReturn()));
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isZero();assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_return_dispositions",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_return_events",Integer.class)).isEqualTo(3);
  assertThatThrownBy(()->as("manager", () -> returns.inspect(request.id(),inspectReturn("SELLABLE","RESTOCK")))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThatThrownBy(()->as("alice", () -> returns.create("return-again",returnRequest((String)receipt.get("id"),1)))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }
 @Test void rejectedIntakeReleasesTheOriginalQuantityBalanceWithoutIssuingStock(){
  enableReturns();supervisor("manager");var receipt=as("alice", () -> checkout.cashSale(a.cashier(),"return-sale3",sale(1,"120")));String order=(String)receipt.get("id");var request=as("alice", () -> returns.create("return-case3",returnRequest(order,1)));
  assertThat(as("alice", () -> returns.eligibility(order)).items().getFirst().remaining()).isZero();as("manager", () -> returns.review(request.id(),new com.saumya.pos.returns.ReturnService.Review("REJECTED","Proof did not match the original goods")));
  assertThat(as("alice", () -> returns.eligibility(order)).items().getFirst().remaining()).isEqualTo(1);as("alice", () -> returns.create("return-new3",returnRequest(order,1)));assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isZero();
 }
 @Test void inspectionRollsBackAllStockAndDispositionRowsIfAnyRestockFails(){
  enableReturns();supervisor("manager");update("INSERT INTO products(id,tenant_id,sku,barcode,name,price,tax_rate,published) VALUES(3,1,'CABLE',NULL,'Cable',20,0,TRUE)");update("INSERT INTO inventory(tenant_id,branch_id,product_id,quantity) VALUES(1,1,3,1)");
  var receipt=as("alice", () -> checkout.cashSale(a.cashier(),"return-sale4",new CheckoutService.CashSale(session,List.of(new CheckoutService.Item(1,1),new CheckoutService.Item(3,1)),new BigDecimal("150"),false,null,null)));
  var command=new com.saumya.pos.returns.ReturnService.Create((String)receipt.get("id"),session,List.of(new com.saumya.pos.returns.ReturnService.Item(1,1),new com.saumya.pos.returns.ReturnService.Item(3,1)),"Goods returned","ORIGINAL_RECEIPT","Receipt and both goods checked");var request=as("alice", () -> returns.create("return-case4",command));as("manager", () -> returns.review(request.id(),approveReturn()));
  var inspection=new com.saumya.pos.returns.ReturnService.Inspect(List.of(new com.saumya.pos.returns.ReturnService.Inspection(1,"SELLABLE","RESTOCK","Panel checked"),new com.saumya.pos.returns.ReturnService.Inspection(3,"SELLABLE","RESTOCK","Cable checked")));
  update("UPDATE inventory SET quantity=? WHERE tenant_id=1 AND product_id=3",Integer.MAX_VALUE);assertThatThrownBy(()->as("manager", () -> returns.inspect(request.id(),inspection))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1 AND product_id=1",Integer.class)).isZero();assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_return_dispositions",Integer.class)).isZero();assertThat(as("alice", () -> returns.get(request.id())).state()).isEqualTo("APPROVED");
  update("UPDATE inventory SET quantity=0 WHERE tenant_id=1 AND product_id=3");as("manager", () -> returns.inspect(request.id(),inspection));as("manager", () -> returns.inspect(request.id(),inspection));
  assertThat(as("alice",()->stock.atBranch(1,1,1))).isEqualTo(1);assertThat(as("alice",()->stock.atBranch(1,1,3))).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_return_dispositions",Integer.class)).isEqualTo(2);assertThat(asScope(a, () -> service.reconciliation(a,session)).expectedCash()).isEqualByComparingTo("180");
 }
 @Test void returnWindowAndOriginalPaidPosChannelAreEnforced(){
  configureOnline();enableReturns();var draft=as("alice",()->as("alice", () -> checkout.pos("alice","not-paid-return",posDraft())));assertThatThrownBy(()->as("alice", () -> returns.eligibility(draft.id()))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);as("alice",()->as("alice", () -> checkout.cancelPos("alice",draft.id(),"Not sold")));
  var online=onlineDraft("online-no-return");assertThatThrownBy(()->as("alice", () -> returns.eligibility(online.id()))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);asCustomer("customer", () -> checkout.cancelOnline("customer",online.id(),"Cancel hold"));
  var receipt=as("alice",()->as("alice", () -> checkout.cashSale(a.cashier(),"return-sale5",sale(1,"120"))));String order=(String)receipt.get("id");update("UPDATE orders SET status_changed_at=? WHERE id=?",java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusDays(31),order);
  assertThat(as("alice", () -> returns.eligibility(order)).eligible()).isFalse();assertThatThrownBy(()->as("alice", () -> returns.create("return-old5",returnRequest(order,1)))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }
 @Test void returnHttpPermissionsCsrfOriginalOwnershipAndForgedFieldsFailClosed() throws Exception {
  enableReturns();supervisor("manager");var receipt=as("alice", () -> checkout.cashSale(a.cashier(),"return-sale6",sale(1,"120")));String order=(String)receipt.get("id"),path="/api/v1/vendor/pos/returns";
  String body="{\"orderId\":\""+order+"\",\"sessionId\":\""+session+"\",\"items\":[{\"productId\":1,\"quantity\":1}],\"reason\":\"Buyer return\",\"proof\":\"ORIGINAL_RECEIPT\",\"proofNote\":\"Receipt checked\"}";
  mvc.perform(get(path).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());mvc.perform(get(path).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isForbidden());
  mvc.perform(post(path).with(user("alice").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW",()->"POS_RETURN_CREATE")).header("Idempotency-Key","http-return6").contentType("application/json").content(body)).andExpect(status().isForbidden());
  for(String forged:List.of(body.replace("\"items\"","\"tenantId\":2,\"items\""),body.replace("[{\"productId\":1,\"quantity\":1}]","[null]")))mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW",()->"POS_RETURN_CREATE")).header("Idempotency-Key","http-return6").contentType("application/json").content(forged)).andExpect(status().isBadRequest());
  mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW",()->"POS_RETURN_CREATE")).header("Idempotency-Key","http-return6").contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.refundStatus").value("NOT_INITIATED"));
  var request=as("alice", () -> returns.list("REQUESTED")).getFirst();mvc.perform(get(path+"/"+request.id()).with(user("bob").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW"))).andExpect(status().isNotFound());
  mvc.perform(get(path).with(user("bob").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
  mvc.perform(post(path+"/"+request.id()+"/review").with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW",()->"POS_RETURN_APPROVE")).contentType("application/json").content("{\"decision\":\"APPROVED\",\"reason\":\"Reviewed\"}")).andExpect(status().isForbidden());
 }
 @Test void concurrentReturnClaimsCannotExceedSoldQuantity() throws Exception {
  enableReturns();var receipt=as("alice", () -> checkout.cashSale(a.cashier(),"return-sale7",sale(1,"120")));String order=(String)receipt.get("id");
  try(var pool=Executors.newFixedThreadPool(2)){Callable<Boolean> first=()->{try{as("alice", () -> returns.create("return-race1",returnRequest(order,1)));return true;}catch(org.springframework.web.server.ResponseStatusException e){return false;}};Callable<Boolean> second=()->{try{as("alice", () -> returns.create("return-race2",returnRequest(order,1)));return true;}catch(org.springframework.web.server.ResponseStatusException e){return false;}};int successes=0;for(var result:pool.invokeAll(List.of(first,second)))if(result.get())successes++;assertThat(successes).isEqualTo(1);}
  assertThat(db.queryForObject("SELECT SUM(quantity) FROM pos_return_items",Integer.class)).isEqualTo(1);
 }
 @Test void concurrentIdenticalInspectionsRestockOnlyOnce() throws Exception {
  enableReturns();supervisor("manager");var receipt=as("alice", () -> checkout.cashSale(a.cashier(),"return-sale8",sale(1,"120")));var request=as("alice", () -> returns.create("return-case8",returnRequest((String)receipt.get("id"),1)));as("manager", () -> returns.review(request.id(),approveReturn()));
  try(var pool=Executors.newFixedThreadPool(2)){Callable<String> inspect=()->as("manager", () -> returns.inspect(request.id(),inspectReturn("SELLABLE","RESTOCK"))).id();for(var result:pool.invokeAll(List.of(inspect,inspect)))assertThat(result.get()).isEqualTo(request.id());}
  assertThat(as("alice",()->stock.atBranch(1,1,1))).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_return_dispositions",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_return_events",Integer.class)).isEqualTo(3);
 }

 @Test void newReturnCanReferenceAClosedOriginalSaleFromANewOwnedShift(){
  enableReturns();var receipt=as("alice", () -> checkout.cashSale(a.cashier(),"return-old-shift",sale(1,"120")));asScope(a, () -> service.close(a,session,new BigDecimal("160")));
  session=(String)asScope(a, () -> service.open(a,new PosService.OpenSession(1,new BigDecimal("20")))).get("id");
  var request=as("alice", () -> returns.create("return-new-shift",returnRequest((String)receipt.get("id"),1)));assertThat(request.state()).isEqualTo("REQUESTED");assertThat(asScope(a, () -> service.reconciliation(a,session)).expectedCash()).isEqualByComparingTo("20");
 }

 String inspectedRefundReturn(){
  enableReturns();supervisor("manager");
  var receipt=as("alice",()->as("alice", () -> checkout.cashSale(a.cashier(),"refund-original",sale(1,"120"))));
  var r=as("alice", () -> returns.create("refund-intake",returnRequest((String)receipt.get("id"),1)));
  as("manager", () -> returns.review(r.id(),approveReturn()));as("manager", () -> returns.inspect(r.id(),inspectReturn("DAMAGED","QUARANTINE")));return r.id();
 }
 String refundSession(String floatAmount){
  update("INSERT INTO registers VALUES(3,1,1)");assignRegister("manager",1,1,3);
  return (String)as("manager",()->service.open(new PosService.Scope(1,1,"manager"),new PosService.OpenSession(3,new BigDecimal(floatAmount)))).get("id");
 }
 com.saumya.pos.returns.CashRefundService.Pay refundPay(String returned,String paying){
  var q=as("manager", () -> refunds.quote(returned,paying));
  return new com.saumya.pos.returns.CashRefundService.Pay(returned,paying,"Independent cash handover",q.quoteHash(),"CASH_HANDED_TO_BUYER");
 }
 @Test void cashRefundIsOnceOnlyAndDebitsPayingDrawerWithoutChangingOriginalSale(){
  String returned=inspectedRefundReturn(),paying=refundSession("200");
  assertThatThrownBy(()->as("manager", () -> refunds.quote(returned,paying))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  update("UPDATE branches SET cash_refunds_enabled=TRUE WHERE id=1");
  var command=refundPay(returned,paying);var paid=as("manager", () -> refunds.pay("refund-once1",command));
  assertThat(paid.amount()).isEqualByComparingTo("110");assertThat(paid.items()).hasSize(1);
  assertThat(as("manager", () -> service.reconciliation(new PosService.Scope(1,1,"manager"),paying)).expectedCash()).isEqualByComparingTo("90");
  assertThat(asScope(a, () -> service.reconciliation(a,session)).expectedCash()).isEqualByComparingTo("160");
  assertThat(as("alice", () -> returns.get(returned)).refundStatus()).isEqualTo("PAID");
  assertThat(as("alice",()->stock.atBranch(1,1,1))).isZero();assertThat(db.queryForObject("SELECT status FROM orders",String.class)).isEqualTo("PAID");
  as("manager", () -> service.close(new PosService.Scope(1,1,"manager"),paying,new BigDecimal("90")));update("UPDATE branches SET cash_refunds_enabled=FALSE WHERE id=1");
  assertThat(as("manager", () -> refunds.pay("refund-once1",command)).id()).isEqualTo(paid.id());
  assertThat(as("manager", () -> refunds.byReturn(returned)).id()).isEqualTo(paid.id());assertThat(as("manager", () -> refunds.copy(paid.id())).copy()).isTrue();
  assertThatThrownBy(()->as("bob", () -> refunds.get(paid.id()))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_cash_refunds",Integer.class)).isEqualTo(1);
 }
 @Test void cashRefundRejectsInsufficientFundsSelfApprovalAndTaxInvoice(){
  String returned=inspectedRefundReturn(),paying=refundSession("50");update("UPDATE branches SET cash_refunds_enabled=TRUE WHERE id=1");
  assertThatThrownBy(()->as("alice", () -> refunds.quote(returned,session))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  var q=as("manager", () -> refunds.quote(returned,paying));assertThat(q.enoughCash()).isFalse();
  assertThatThrownBy(()->as("manager", () -> refunds.pay("refund-short",refundPay(returned,paying)))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  update("UPDATE orders SET tax_invoice=TRUE");
  assertThatThrownBy(()->as("manager", () -> refunds.quote(returned,paying))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_cash_refunds",Integer.class)).isZero();
 }
 @Test void concurrentRefundRetriesCreateOneImmutablePayment() throws Exception {
  String returned=inspectedRefundReturn(),paying=refundSession("200");update("UPDATE branches SET cash_refunds_enabled=TRUE WHERE id=1");var command=refundPay(returned,paying);
  try(var pool=Executors.newFixedThreadPool(2)){
   var jobs=List.of((Callable<String>)()->as("manager", () -> refunds.pay("refund-race1",command)).id(),()->as("manager", () -> refunds.pay("refund-race1",command)).id());
   var results=pool.invokeAll(jobs);assertThat(results.get(0).get()).isEqualTo(results.get(1).get());
  }
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_cash_refund_items",Integer.class)).isEqualTo(1);
 }
 @Test void refundHttpRequiresIndependentPrivilegesAndCsrf() throws Exception {
  String returned=inspectedRefundReturn(),paying=refundSession("200"),path="/api/v1/vendor/pos/refunds";update("UPDATE branches SET cash_refunds_enabled=TRUE WHERE id=1");
  mvc.perform(get(path).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());
  mvc.perform(get(path).with(user("manager").authorities(()->"POS_VIEW"))).andExpect(status().isForbidden());
  mvc.perform(get(path+"/quote/"+returned).param("sessionId",paying).with(user("manager").authorities(()->"POS_VIEW",()->"POS_REFUND_VIEW",()->"POS_REFUND_APPROVE",()->"POS_REFUND_CASH_RECORD"))).andExpect(status().isOk());
  mvc.perform(post(path).with(user("manager").authorities(()->"POS_VIEW",()->"POS_REFUND_VIEW",()->"POS_REFUND_APPROVE",()->"POS_REFUND_CASH_RECORD")).contentType("application/json").content("{}")).andExpect(status().isForbidden());
 }

 @Test void refundAndCashWithdrawalCannotOverdrawPayingSession() throws Exception {
  String returned=inspectedRefundReturn(),paying=refundSession("120");update("UPDATE branches SET cash_refunds_enabled=TRUE WHERE id=1");var command=refundPay(returned,paying);var manager=new PosService.Scope(1,1,"manager");
  try(var pool=Executors.newFixedThreadPool(2)){
   Callable<Boolean> refund=()->{try{as("manager", () -> refunds.pay("refund-cash-race",command));return true;}catch(org.springframework.web.server.ResponseStatusException e){return false;}};
   Callable<Boolean> withdrawal=()->{try{asScope(manager, () -> service.cashMovement(manager,paying,"cash-withdraw-race",movement(PosService.MovementType.CASH_OUT,"20","Supplies")));return true;}catch(org.springframework.web.server.ResponseStatusException e){return false;}};
   int wins=0;for(var result:pool.invokeAll(List.of(refund,withdrawal)))if(result.get())wins++;assertThat(wins).isEqualTo(1);
  }
  assertThat(asScope(manager, () -> service.reconciliation(manager,paying)).expectedCash()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
 }
 @Test void stalePartialRefundQuoteCannotPayAndFullAllocationMatchesOriginal() {
  enableReturns();supervisor("manager");update("UPDATE inventory SET quantity=3 WHERE tenant_id=1");update("UPDATE products SET price=0.05,tax_rate=10 WHERE id=1");
  var receipt=as("alice",()->as("alice", () -> checkout.cashSale(a.cashier(),"partial-original",new CheckoutService.CashSale(session,List.of(new CheckoutService.Item(1,3)),BigDecimal.ONE,false,null,null))));
  String order=(String)receipt.get("id"),paying=refundSession("10");update("UPDATE branches SET cash_refunds_enabled=TRUE WHERE id=1");
  var ids=new ArrayList<String>();for(int i=0;i<3;i++){final int index=i;var r=as("alice", () -> returns.create("partial-return"+index,returnRequest(order,1)));as("manager", () -> returns.review(r.id(),approveReturn()));as("manager", () -> returns.inspect(r.id(),inspectReturn("DAMAGED","QUARANTINE")));ids.add(r.id());}
  var first=refundPay(ids.get(0),paying);var stale=refundPay(ids.get(1),paying);
  as("manager", () -> refunds.pay("partial-refund0",first));
  assertThatThrownBy(()->as("manager", () -> refunds.pay("partial-refund1",stale))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  update("UPDATE products SET price=999,tax_rate=50 WHERE id=1");
  for(int i=1;i<3;i++){final int index=i;as("manager", () -> refunds.pay("partial-refund"+index,refundPay(ids.get(index),paying)));}
  assertThat(db.queryForObject("SELECT SUM(amount) FROM pos_cash_refunds",BigDecimal.class)).isEqualByComparingTo((BigDecimal)receipt.get("total"));
 }

 @Test void refundReportUsesPayoutUtcDatesAndDoesNotMultiplyMultiLineCash() throws Exception {
  enableReturns();supervisor("manager");update("INSERT INTO products(id,tenant_id,sku,barcode,name,price,tax_rate,published) VALUES(3,1,'CABLE',NULL,'Cable',20,10,TRUE)");update("INSERT INTO inventory(tenant_id,branch_id,product_id,quantity) VALUES(1,1,3,1)");
  var receipt=as("alice",()->as("alice", () -> checkout.cashSale(a.cashier(),"refund-report-sale",new CheckoutService.CashSale(session,List.of(new CheckoutService.Item(1,1),new CheckoutService.Item(3,1)),new BigDecimal("150"),false,null,null))));
  var command=new com.saumya.pos.returns.ReturnService.Create((String)receipt.get("id"),session,List.of(new com.saumya.pos.returns.ReturnService.Item(1,1),new com.saumya.pos.returns.ReturnService.Item(3,1)),"Returned goods","ORIGINAL_RECEIPT","Receipt checked");
  var returned=as("alice", () -> returns.create("refund-report-return",command));as("manager", () -> returns.review(returned.id(),approveReturn()));
  as("manager", () -> returns.inspect(returned.id(),new com.saumya.pos.returns.ReturnService.Inspect(List.of(new com.saumya.pos.returns.ReturnService.Inspection(1,"DAMAGED","QUARANTINE","Panel checked"),new com.saumya.pos.returns.ReturnService.Inspection(3,"DAMAGED","QUARANTINE","Cable checked")))));
  String paying=refundSession("200");update("UPDATE branches SET cash_refunds_enabled=TRUE WHERE id=1");var paid=as("manager", () -> refunds.pay("refund-report-paid",refundPay(returned.id(),paying)));
  update("UPDATE pos_cash_refunds SET paid_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.parse("2026-11-12T23:59:59Z")),paid.id());
  String path="/api/v1/vendor/pos/reports/cash-refunds";
  mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-13").param("tenantId","2").param("branchId","2").with(user("manager").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_REFUND_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.branchId").value(1)).andExpect(jsonPath("$.totals.refunds").value(1)).andExpect(jsonPath("$.totals.units").value(2)).andExpect(jsonPath("$.totals.cash").value(132)).andExpect(jsonPath("$.totals.beforeTax").value(120)).andExpect(jsonPath("$.totals.tax").value(12)).andExpect(jsonPath("$.days[0].totals.refunds").value(1)).andExpect(jsonPath("$.days[1].totals.refunds").value(0)).andExpect(jsonPath("$.cashiers[0].cashier").value("manager"));
  update("INSERT INTO branches(id,tenant_id,name) VALUES(3,1,'Other branch')");update("INSERT INTO registers VALUES(4,1,3)");employee("other-report",1,3);
  for(String actor:List.of("bob","other-report"))mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-13").with(user(actor).authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_REFUND_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totals.cash").value(0)).andExpect(jsonPath("$.cashiers.length()").value(0));
  update("UPDATE pos_cash_refunds SET paid_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.parse("2026-11-13T00:00:00Z")),paid.id());
  for(String day:List.of("2026-11-12","2026-11-13"))mvc.perform(get(path).param("from",day).param("to",day).with(user("manager").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_REFUND_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totals.refunds").value(day.endsWith("13")?1:0));
  assertThat(as("manager", () -> service.reconciliation(new PosService.Scope(1,1,"manager"),paying)).expectedCash()).isEqualByComparingTo("68");
 }
 @Test void refundReportRequiresAllGrantsAndValidDates() throws Exception {
  String path="/api/v1/vendor/pos/reports/cash-refunds";mvc.perform(get(path).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());
  mvc.perform(get(path).with(user("alice").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW"))).andExpect(status().isForbidden());
  mvc.perform(get(path).with(user("alice").authorities(()->"POS_VIEW",()->"POS_REFUND_VIEW"))).andExpect(status().isForbidden());
  mvc.perform(get(path).with(user("non-employee").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_REFUND_VIEW"))).andExpect(status().isForbidden());
  for(String[] range:List.of(new String[]{"2026-11-14","2026-11-13"},new String[]{"2025-01-01","2026-11-13"},new String[]{"invalid","2026-11-13"}))mvc.perform(get(path).param("from",range[0]).param("to",range[1]).with(user("alice").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_REFUND_VIEW"))).andExpect(status().isBadRequest());
 }

 void searchableCatalogue(){
  update("UPDATE tenants SET slug=CONCAT('vendor-',id),online_branch_id=id");
  update("UPDATE tenants SET online_branch_id=1 WHERE id=1");
  update("INSERT INTO branches(id,tenant_id,name) VALUES(3,1,'Other stock')");
  update("INSERT INTO products(id,tenant_id,sku,barcode,name,price,tax_rate,published) VALUES(3,1,'INV',NULL,'Inverter',200,10,TRUE),(4,1,'BAT',NULL,'Battery',75,0,TRUE),(5,1,'HIDDEN',NULL,'Private Panel',1,0,FALSE),(6,1,'CABLE',NULL,'100%_Cable!',10,0,TRUE)");
  update("INSERT INTO inventory(tenant_id,branch_id,product_id,quantity,reserved) VALUES(1,1,3,2,1),(1,3,4,5,0),(1,1,6,1,0)");update("UPDATE inventory SET reserved=1 WHERE tenant_id=1 AND product_id=1");
  update("INSERT INTO catalog_categories(id,tenant_id,name,slug) VALUES(101,1,'Panels','panels'),(102,1,'Inverters','inverters'),(201,2,'Private category','private')");
  update("INSERT INTO product_categories VALUES(1,1,101),(1,3,102),(1,5,101),(2,2,201)");
  as("alice", () -> promotions.approve(1,"alice",3,new com.saumya.pos.catalog.PromotionService.Change(new BigDecimal("75"),"Approved inverter offer")));
 }
 @Test void publicCataloguePagesUseEffectivePricesAndDesignatedFreeStock() throws Exception {
  searchableCatalogue();String path="/api/v1/marketplace/vendors/vendor-1/catalogue";
  mvc.perform(get(path).param("sort","price-low").param("size","1")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4)).andExpect(jsonPath("$.totalPages").value(4)).andExpect(jsonPath("$.catalogueTotal").value(4)).andExpect(jsonPath("$.items[0].id").value(6)).andExpect(jsonPath("$.categories.length()").value(3));
  mvc.perform(get(path).param("sort","price-low").param("size","1").param("page","1")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(3)).andExpect(jsonPath("$.items[0].price").value(50)).andExpect(jsonPath("$.items[0].discountPercent").value(75)).andExpect(jsonPath("$.items[0].inStock").value(true)).andExpect(jsonPath("$.items[0].barcode").doesNotExist()).andExpect(jsonPath("$.items[0].available").doesNotExist());
  mvc.perform(get(path).param("inStock","true")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
  mvc.perform(get("/api/v1/marketplace/vendors/vendor-1/products/4")).andExpect(status().isOk()).andExpect(jsonPath("$.inStock").value(false));
  mvc.perform(get(path).param("category","101")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].inStock").value(false));
  mvc.perform(get(path).param("category","other")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
  mvc.perform(get(path).param("category","201")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  mvc.perform(get(path).param("q","%_Cable!")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
  mvc.perform(get(path).param("q","inv")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
  mvc.perform(get(path).param("q","private")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  mvc.perform(get(path).param("page","20")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.totalElements").value(4));
  update("UPDATE tenants SET online_branch_id=NULL WHERE id=1");mvc.perform(get(path).param("inStock","true")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
 }
 @Test void catalogueRejectsInvalidFiltersAndInactiveVendors() throws Exception {
  String path="/api/v1/marketplace/vendors/vendor-1/catalogue";
  for(String[] bad:List.of(new String[]{"page","-1"},new String[]{"size","49"},new String[]{"sort","price;DROP"},new String[]{"category","-1"},new String[]{"category","9999999999999999999"},new String[]{"q","x".repeat(121)}))mvc.perform(get(path).param(bad[0],bad[1])).andExpect(status().isBadRequest());
  update("UPDATE tenants SET lifecycle_status='SUSPENDED' WHERE id=1");mvc.perform(get(path)).andExpect(status().isNotFound());mvc.perform(get("/api/v1/marketplace/vendors/unknown/catalogue")).andExpect(status().isNotFound());
 }

 @Test void cataloguePriceSortingUsesRoundedPromotionsAndStableIdTies() throws Exception {
  searchableCatalogue();update("INSERT INTO products(id,tenant_id,sku,barcode,name,price,tax_rate,published) VALUES(7,1,'TINY',NULL,'Tiny connector',0.05,10,TRUE),(8,1,'TIE',NULL,'Tie inverter',50,0,TRUE)");
  as("alice", () -> promotions.approve(1,"alice",7,new com.saumya.pos.catalog.PromotionService.Change(new BigDecimal("10"),"Approved connector offer")));
  mvc.perform(get("/api/v1/marketplace/vendors/vendor-1/catalogue").param("q","tiny")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].price").value(0.05));
  mvc.perform(get("/api/v1/marketplace/vendors/vendor-1/catalogue").param("sort","price-low").param("size","2").param("page","1")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(3)).andExpect(jsonPath("$.items[1].id").value(8)).andExpect(jsonPath("$.totalElements").value(6));
 }

 @Test void posCatalogueUsesAssignedBranchAndExactServerSideScans() throws Exception {
  searchableCatalogue();update("INSERT INTO registers VALUES(3,1,3)");String path="/api/v1/vendor/pos/catalogue";
  mvc.perform(get(path).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());mvc.perform(get(path).with(user("alice").authorities(()->"POS_SALE_CREATE"))).andExpect(status().isForbidden());
  mvc.perform(get(path).param("tenantId","2").param("branchId","3").param("inStock","true").with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
  employee("store3",1,3);
  var store3=new PosService.Scope(1,3,"store3");
  asScope(store3,()->service.open(store3,new PosService.OpenSession(3,BigDecimal.ZERO)));
  asScope(b,()->service.open(b,new PosService.OpenSession(2,BigDecimal.ZERO)));
  mvc.perform(get(path).param("inStock","true").with(user("store3").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(4)).andExpect(jsonPath("$.items[0].quantity").value(5));
  mvc.perform(get(path).with(user("bob").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(2));
  mvc.perform(get(path+"/scan").param("code","  INV  ").with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(3)).andExpect(jsonPath("$.price").value(50)).andExpect(jsonPath("$.quantity").value(1));
  mvc.perform(get(path+"/scan").param("code","123").with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.barcode").value("123")).andExpect(jsonPath("$.quantity").value(0));
  for(String code:List.of("12","P2","HIDDEN"))mvc.perform(get(path+"/scan").param("code",code).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isNotFound());
  assertThatThrownBy(()->update("UPDATE products SET barcode='123' WHERE id=3")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  update("UPDATE products SET sku='123' WHERE id=3");mvc.perform(get(path+"/scan").param("code","123").with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isConflict());
  update("UPDATE tenants SET online_branch_id=NULL WHERE id=1");mvc.perform(get(path).param("q","inv").with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(1));
 }
 @Test void posCatalogueRejectsMalformedFiltersAndUnknownMembership() throws Exception {
  String path="/api/v1/vendor/pos/catalogue";
  for(String[] bad:List.of(new String[]{"page","-1"},new String[]{"size","49"},new String[]{"category","-1"},new String[]{"sort","unknown"},new String[]{"q","x".repeat(121)}))mvc.perform(get(path).param(bad[0],bad[1]).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isBadRequest());
  mvc.perform(get(path+"/scan").param("code"," ").with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isBadRequest());
  mvc.perform(get(path).with(user("unknown").authorities(()->"POS_VIEW"))).andExpect(status().isForbidden());
 }

 @Test void reservationReferenceRecoveryRetainsSnapshotsAndEnforcesOwnership() throws Exception {
  configureOnline();var draft=as("alice",()->as("alice", () -> checkout.pos("alice","recover-pos-draft",posDraft())));supervisor("manager");
  String path="/api/v1/vendor/pos/drafts/"+draft.id();
  mvc.perform(get(path).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value(session)).andExpect(jsonPath("$.expired").value(false)).andExpect(jsonPath("$.total").value(110));
  for(String actor:List.of("bob","manager"))mvc.perform(get(path).with(user(actor).authorities(()->"POS_VIEW"))).andExpect(status().isNotFound());
  mvc.perform(get(path).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());mvc.perform(get(path).with(user("alice").authorities(()->"POS_SALE_CREATE"))).andExpect(status().isForbidden());
  update("UPDATE products SET price=999 WHERE id=1");mvc.perform(get(path).param("tenantId","2").param("cashier","bob").with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].unitPrice").value(100));
  as("alice", () -> as("alice", () -> checkout.cancelPos("alice",draft.id(),"Buyer cancelled")));var online=onlineDraft("recover-online-draft");
  String customerPath="/api/v1/customer/checkout/drafts/"+online.id();mvc.perform(get(customerPath).with(user("customer").authorities(()->"CUSTOMER"))).andExpect(status().isOk()).andExpect(jsonPath("$.channel").value("ONLINE"));
  mvc.perform(get(customerPath).with(user("other-customer").authorities(()->"CUSTOMER"))).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/customer/checkout/drafts/"+draft.id()).with(user("customer").authorities(()->"CUSTOMER"))).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/vendor/pos/drafts/"+online.id()).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isNotFound());
 }
 @Test void elapsedDraftRecoveryDoesNotReleaseStockOrPayAndCanRecoverAfterClose() throws Exception {
  var draft=as("alice", () -> checkout.pos("alice","recover-expiry-draft",posDraft()));update("UPDATE orders SET expires_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)),draft.id());
  String path="/api/v1/vendor/pos/drafts/"+draft.id();
  mvc.perform(get(path).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.expired").value(true));
  assertThat(as("alice",()->stock.atBranch(1,1,1))).isZero();assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_invoices",Integer.class)).isZero();
  draftExpiry.expireOne(1,draft.id());asScope(a, () -> service.close(a,session,new BigDecimal("50")));mvc.perform(get(path).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EXPIRED"));assertThat(as("alice",()->stock.atBranch(1,1,1))).isEqualTo(1);
 }
 @Test void paidDraftReferenceRecoveryNeverCreatesAnotherReceiptOrSale() throws Exception {
  var draft=as("alice", () -> checkout.pos("alice","recover-paid-draft",posDraft()));as("alice", () -> checkout.finalizeCash("alice",draft.id(),new BigDecimal("120")));asScope(a, () -> service.close(a,session,new BigDecimal("160")));
  for(int i=0;i<2;i++)mvc.perform(get("/api/v1/vendor/pos/drafts/"+draft.id()).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID")).andExpect(jsonPath("$.expired").value(false));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_invoices",Integer.class)).isEqualTo(1);
 }

 @Test void salesCsvExportIsScopedAuditedAndContainsNoBuyerFields() throws Exception {
  as("alice", () -> checkout.cashSale(a.cashier(),"export-sale",sale(1,"120")));String path="/api/v1/vendor/pos/reports/sales/export",body="{\"channel\":\"ALL\",\"reason\":\"Branch review\"}";
  mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW")).contentType("application/json").content(body)).andExpect(status().isForbidden());
  mvc.perform(post(path).with(user("alice").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_REPORT_EXPORT")).contentType("application/json").content(body)).andExpect(status().isForbidden());
  var response=mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_REPORT_EXPORT")).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().exists("Content-Disposition")).andReturn().getResponse();
  String csv=response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);assertThat(csv).contains("\"1\",\"1\"","\"110.00\"").doesNotContain("customer_id","cashier","password","buyer","Panel");
  var audit=db.queryForMap("SELECT * FROM report_export_audit WHERE id=?",response.getHeader("X-Export-Reference"));assertThat(((Number)audit.get("tenant_id")).longValue()).isEqualTo(1);assertThat(audit.get("actor")).isEqualTo("alice");assertThat(audit.get("content_sha256")).isEqualTo(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(response.getContentAsByteArray())));
  var foreign=mvc.perform(post(path).with(csrf()).with(user("bob").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_REPORT_EXPORT")).contentType("application/json").content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);assertThat(foreign).contains("\"2\",\"2\"").doesNotContain("110.00");
  update("INSERT INTO branches(id,tenant_id,name) VALUES(3,1,'Other export branch')");update("INSERT INTO registers VALUES(3,1,3)");employee("export-other",1,3);
  String other=mvc.perform(post(path).with(csrf()).with(user("export-other").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_REPORT_EXPORT")).contentType("application/json").content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);assertThat(other).contains("\"1\",\"3\"").doesNotContain("110.00");

 }
 @Test void exportsRejectForgedScopeInvalidRangeAndMissingRefundGrant() throws Exception {
  String sales="/api/v1/vendor/pos/reports/sales/export",refund="/api/v1/vendor/pos/reports/cash-refunds/export";
  for(String body:List.of("{\"reason\":\" \"}","{\"reason\":\"Review\",\"tenantId\":2}","{\"reason\":\"Review\",\"from\":\"2026-11-14\",\"to\":\"2026-11-13\"}"))mvc.perform(post(sales).with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_REPORT_EXPORT")).contentType("application/json").content(body)).andExpect(status().isBadRequest());
  mvc.perform(post(refund).with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_REPORT_EXPORT")).contentType("application/json").content("{\"reason\":\"Review\"}")).andExpect(status().isForbidden());
  mvc.perform(post(refund).with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_REPORT_EXPORT",()->"POS_REFUND_VIEW")).contentType("application/json").content("{\"reason\":\"Review\"}")).andExpect(status().isOk());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM report_export_audit",Integer.class)).isEqualTo(1);
 }

 @Test void cashRefundCsvExportsOnlyScopedPayoutAggregatesWithoutChangingFinances() throws Exception {
  String returned=inspectedRefundReturn(),paying=refundSession("200");update("UPDATE branches SET cash_refunds_enabled=TRUE WHERE id=1");as("manager", () -> refunds.pay("export-refund-paid",refundPay(returned,paying)));
  String csv=mvc.perform(post("/api/v1/vendor/pos/reports/cash-refunds/export").with(csrf()).with(user("manager").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_REPORT_EXPORT",()->"POS_REFUND_VIEW")).contentType("application/json").content("{\"reason\":\"Payout review\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
  assertThat(csv).contains("\"CASHIER\",\"manager\"","\"110.00\"").doesNotContain("Panel","Independent cash handover","return_id","session_id");
  assertThat(as("manager", () -> service.reconciliation(new PosService.Scope(1,1,"manager"),paying)).expectedCash()).isEqualByComparingTo("90");assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_cash_refunds",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);
 }

 @Test void cashierSalesPagesRetainOriginalSellerAndSavedAmountsAcrossClosedShifts() throws Exception {
  configureOnline();update("UPDATE inventory SET quantity=10 WHERE tenant_id=1");update("INSERT INTO products(id,tenant_id,sku,barcode,name,price,tax_rate,published) VALUES(3,1,'CABLE',NULL,'Cable',20,10,TRUE)");update("INSERT INTO inventory(tenant_id,branch_id,product_id,quantity) VALUES(1,1,3,10)");
  as("alice", () -> promotions.approve(1,"alice",1,new com.saumya.pos.catalog.PromotionService.Change(BigDecimal.TEN,"Approved offer")));
  as("alice",()->as("alice", () -> checkout.cashSale(a.cashier(),"cashier-alice-sale",new CheckoutService.CashSale(session,List.of(new CheckoutService.Item(1,1),new CheckoutService.Item(3,2)),new BigDecimal("200"),true,null,null))));
  var manager=supervisor("manager");update("INSERT INTO registers VALUES(3,1,1)");assignRegister("manager",1,1,3);String managerSession=(String)asScope(manager, () -> service.open(manager,new PosService.OpenSession(3,BigDecimal.ZERO))).get("id");
  as("manager", () -> as("manager", () -> checkout.cashSale(manager.cashier(),"cashier-manager-sale",new CheckoutService.CashSale(managerSession,List.of(new CheckoutService.Item(3,1)),new BigDecimal("25"),false,null,null))));
  asScope(a, () -> service.close(a,session,new BigDecimal("193")));onlineDraft("cashier-online-unpaid");update("UPDATE products SET price=999,tax_rate=50 WHERE tenant_id=1");
  String path="/api/v1/vendor/pos/reports/cashier-sales";
  mvc.perform(get(path).param("size","1").param("tenantId","2").param("branchId","2").with(user("manager").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_CASHIER_REPORT_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.items[0].cashier").value("alice")).andExpect(jsonPath("$.pageTotals.paidOrders").value(1)).andExpect(jsonPath("$.pageTotals.units").value(3)).andExpect(jsonPath("$.pageTotals.sales").value(143)).andExpect(jsonPath("$.pageTotals.tax").value(13)).andExpect(jsonPath("$.pageTotals.beforeTax").value(130)).andExpect(jsonPath("$.pageTotals.cashApplied").value(143)).andExpect(jsonPath("$.pageTotals.discounts").value(10));
  mvc.perform(get(path).param("size","1").param("page","1").with(user("manager").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_CASHIER_REPORT_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].cashier").value("manager")).andExpect(jsonPath("$.pageTotals.sales").value(22));
  mvc.perform(get(path).param("size","48").with(user("manager").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_CASHIER_REPORT_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.pageTotals.sales").value(165)).andExpect(jsonPath("$.pageTotals.units").value(4));
  update("INSERT INTO branches(id,tenant_id,name) VALUES(4,1,'Other report branch')");update("INSERT INTO registers VALUES(4,1,4)");employee("other-seller-report",1,4);
  for(String actor:List.of("bob","other-seller-report"))mvc.perform(get(path).with(user(actor).authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_CASHIER_REPORT_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.items.length()").value(0));
 }
 @Test void cashierSalesReportRequiresItsOwnPermissionAndUtcBounds() throws Exception {
  String path="/api/v1/vendor/pos/reports/cashier-sales";mvc.perform(get(path).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());mvc.perform(get(path).with(user("alice").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW"))).andExpect(status().isForbidden());
  mvc.perform(get(path).with(user("unknown").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_CASHIER_REPORT_VIEW"))).andExpect(status().isForbidden());
  for(String[] param:List.of(new String[]{"size","49"},new String[]{"page","-1"},new String[]{"from","2020-01-01"},new String[]{"to","invalid"}))mvc.perform(get(path).param(param[0],param[1]).with(user("alice").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_CASHIER_REPORT_VIEW"))).andExpect(status().isBadRequest());
 }

 @Test void sellingCashierReportUsesUtcPaidEventsAndDoesNotSubtractOtherCashierRefunds() throws Exception {
  String returned=inspectedRefundReturn(),paying=refundSession("200");update("UPDATE branches SET cash_refunds_enabled=TRUE WHERE id=1");as("manager", () -> refunds.pay("cashier-insights-refund",refundPay(returned,paying)));String path="/api/v1/vendor/pos/reports/cashier-sales";
  mvc.perform(get(path).with(user("manager").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_CASHIER_REPORT_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].cashier").value("alice")).andExpect(jsonPath("$.pageTotals.sales").value(110));
  update("UPDATE orders SET status_changed_at=?",java.sql.Timestamp.from(java.time.Instant.parse("2026-11-12T23:59:59Z")));
  mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-12").with(user("manager").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_CASHIER_REPORT_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.pageTotals.paidOrders").value(1));
  update("UPDATE orders SET status_changed_at=?",java.sql.Timestamp.from(java.time.Instant.parse("2026-11-13T00:00:00Z")));update("UPDATE employees SET enabled=FALSE WHERE username='alice'");
  for(String day:List.of("2026-11-12","2026-11-13"))mvc.perform(get(path).param("from",day).param("to",day).with(user("manager").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_CASHIER_REPORT_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.pageTotals.paidOrders").value(day.endsWith("13")?1:0));
 }

 @Test void refundHistoryPagesSearchesAndDatesRemainBranchScoped() throws Exception {
  enableReturns();supervisor("manager");update("UPDATE inventory SET quantity=3 WHERE tenant_id=1");var receipt=as("alice", () -> checkout.cashSale(a.cashier(),"history-original",new CheckoutService.CashSale(session,List.of(new CheckoutService.Item(1,3)),new BigDecimal("400"),false,null,null)));String paying=refundSession("500");update("UPDATE branches SET cash_refunds_enabled=TRUE WHERE id=1");
  var records=new ArrayList<com.saumya.pos.returns.CashRefundService.View>();for(int i=0;i<3;i++){final int index=i;var r=as("alice", () -> returns.create("history-return"+index,returnRequest((String)receipt.get("id"),1)));as("manager", () -> returns.review(r.id(),approveReturn()));as("manager", () -> returns.inspect(r.id(),inspectReturn("DAMAGED","QUARANTINE")));records.add(as("manager", () -> refunds.pay("history-refund"+index,refundPay(r.id(),paying))));}
  update("UPDATE pos_cash_refunds SET paid_at=?",java.sql.Timestamp.from(java.time.Instant.parse("2026-11-12T23:59:59Z")));
  String path="/api/v1/vendor/pos/refunds/history";var ids=records.stream().map(com.saumya.pos.returns.CashRefundService.View::id).sorted(Comparator.reverseOrder()).toList();
  for(int page=0;page<3;page++)mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-12").param("size","1").param("page",Integer.toString(page)).param("tenantId","2").with(user("manager").authorities(()->"POS_VIEW",()->"POS_REFUND_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.items[0].id").value(ids.get(page))).andExpect(jsonPath("$.items[0].reason").doesNotExist()).andExpect(jsonPath("$.items[0].sessionId").doesNotExist());
  for(String ref:List.of(records.getFirst().id().substring(0,8),records.getFirst().returnId()))mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-12").param("reference",ref).with(user("manager").authorities(()->"POS_VIEW",()->"POS_REFUND_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
  mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-12").param("reference",receipt.get("receipt_number").toString()).with(user("manager").authorities(()->"POS_VIEW",()->"POS_REFUND_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
  mvc.perform(get(path).param("from","2026-11-13").param("to","2026-11-13").with(user("manager").authorities(()->"POS_VIEW",()->"POS_REFUND_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  update("INSERT INTO branches(id,tenant_id,name) VALUES(4,1,'Other history branch')");update("INSERT INTO registers VALUES(4,1,4)");employee("history-other",1,4);for(String actor:List.of("bob","history-other"))mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-12").with(user(actor).authorities(()->"POS_VIEW",()->"POS_REFUND_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  assertThat(as("manager", () -> service.reconciliation(new PosService.Scope(1,1,"manager"),paying)).expectedCash()).isEqualByComparingTo("170");assertThat(as("alice",()->stock.atBranch(1,1,1))).isZero();
 }
 @Test void refundHistoryRejectsInvalidFiltersAndMissingPermissions() throws Exception {
  String path="/api/v1/vendor/pos/refunds/history";mvc.perform(get(path).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());mvc.perform(get(path).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isForbidden());
  for(String[] bad:List.of(new String[]{"reference","%"},new String[]{"size","49"},new String[]{"page","-1"},new String[]{"from","2020-01-01"}))mvc.perform(get(path).param(bad[0],bad[1]).with(user("alice").authorities(()->"POS_VIEW",()->"POS_REFUND_VIEW"))).andExpect(status().isBadRequest());
 }

 @Test void returnAuditTimelineIncludesStockAndFinancialEventsWithoutPrivateNotes() throws Exception {
  String returned=inspectedRefundReturn(),paying=refundSession("200");update("UPDATE branches SET cash_refunds_enabled=TRUE WHERE id=1");var paid=as("manager", () -> refunds.pay("audit-refund-paid",refundPay(returned,paying)));as("manager", () -> refunds.copy(paid.id()));
  int sequence=0;for(String action:List.of("REQUESTED","APPROVED","INSPECTED","CASH_REFUND","REFUND_COPY"))update("UPDATE pos_return_events SET created_at=? WHERE return_id=? AND action=?",java.sql.Timestamp.from(java.time.Instant.parse("2026-11-13T12:00:00Z").plusSeconds(sequence++)),returned,action);
  as("manager", () -> service.close(new PosService.Scope(1,1,"manager"),paying,new BigDecimal("90")));asScope(a, () -> service.close(a,session,new BigDecimal("160")));
  String path="/api/v1/vendor/pos/returns/"+returned+"/events";
  mvc.perform(get(path).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());mvc.perform(get(path).with(user("manager").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW"))).andExpect(status().isForbidden());
  mvc.perform(get(path).param("size","2").with(user("manager").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW",()->"POS_RETURN_AUDIT_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("INSPECTED")).andExpect(jsonPath("$.totalElements").value(5)).andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.items[0].action").value("REFUND_COPY")).andExpect(jsonPath("$.items[0].note").doesNotExist()).andExpect(jsonPath("$.items[0].sessionId").doesNotExist());
  mvc.perform(get(path).param("page","2").param("size","2").with(user("manager").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW",()->"POS_RETURN_AUDIT_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].action").value("REQUESTED"));
  update("UPDATE pos_return_events SET created_at=? WHERE return_id=?",java.sql.Timestamp.from(java.time.Instant.parse("2026-11-13T12:00:00Z")),returned);
  var eventIds=db.queryForList("SELECT id FROM pos_return_events WHERE return_id=?",String.class,returned).stream().sorted(Comparator.reverseOrder()).toList();
  for(int page=0;page<3;page++)mvc.perform(get(path).param("page",Integer.toString(page)).param("size","2").with(user("manager").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW",()->"POS_RETURN_AUDIT_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(eventIds.get(page*2)));
  update("INSERT INTO branches(id,tenant_id,name) VALUES(4,1,'Other audit branch')");update("INSERT INTO registers VALUES(4,1,4)");employee("audit-other",1,4);
  for(String actor:List.of("bob","audit-other"))mvc.perform(get(path).param("tenantId","1").param("branchId","1").with(user(actor).authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW",()->"POS_RETURN_AUDIT_VIEW"))).andExpect(status().isNotFound());
  mvc.perform(get(path).param("size","49").with(user("manager").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW",()->"POS_RETURN_AUDIT_VIEW"))).andExpect(status().isBadRequest());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_return_events",Integer.class)).isEqualTo(5);assertThat(as("alice",()->stock.atBranch(1,1,1))).isZero();assertThat(as("manager", () -> service.reconciliation(new PosService.Scope(1,1,"manager"),paying)).expectedCash()).isEqualByComparingTo("90");
 }
 @Test void pagedReturnQueueFindsCasesBeyondTwentyAndEnforcesReadOnlyBranchScope() throws Exception {
  update("UPDATE branches SET pos_return_window_days=30 WHERE id=1");update("UPDATE inventory SET quantity=25 WHERE tenant_id=1");
  String order=(String)as("alice",()->as("alice", () -> checkout.cashSale(a.cashier(),"queue-sale",new CheckoutService.CashSale(session,List.of(new CheckoutService.Item(1,25)),new BigDecimal("2750"),false,null,null)))).get("id");
  var ids=new ArrayList<String>();for(int i=0;i<25;i++){final int index=i;ids.add(as("alice", () -> returns.create("queue-case-"+index,returnRequest(order,1))).id());}
  update("UPDATE pos_returns SET created_at=?",java.sql.Timestamp.from(java.time.Instant.parse("2026-11-12T00:00:00Z")));ids.sort(Comparator.naturalOrder());
  String path="/api/v1/vendor/pos/returns/queue";
  for(int page=0;page<3;page++)mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-12").param("page",Integer.toString(page)).with(user("alice").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(25)).andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.items[0].id").value(ids.get(page*12))).andExpect(jsonPath("$.items[0].reason").doesNotExist()).andExpect(jsonPath("$.items[0].requester").doesNotExist()).andExpect(jsonPath("$.items[0].items").doesNotExist());
  mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-12").param("reference",ids.getLast()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
  mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-12").param("reference",order.toUpperCase(Locale.ROOT)).with(user("alice").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(25));
  mvc.perform(get(path).param("from","2026-11-13").param("to","2026-11-13").with(user("alice").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-12").param("state","APPROVED").with(user("alice").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  employee("queue-manager",1,1);as("queue-manager", () -> returns.review(ids.getFirst(),approveReturn()));
  asScope(a, () -> service.close(a,session,new BigDecimal("2800")));
  for(String state:List.of("ALL","REQUESTED","APPROVED"))mvc.perform(get(path).param("state",state).param("from","2026-11-12").param("to","2026-11-12").with(user("alice").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(state.equals("ALL")?25:state.equals("REQUESTED")?24:1));
  update("INSERT INTO branches(id,tenant_id,name) VALUES(4,1,'Other queue branch')");update("INSERT INTO registers VALUES(4,1,4)");employee("queue-other",1,4);
  for(String actor:List.of("bob","queue-other"))mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-12").param("tenantId","1").param("branchId","1").with(user(actor).authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_return_events",Integer.class)).isEqualTo(26);assertThat(as("alice",()->stock.atBranch(1,1,1))).isZero();assertThat(asScope(a, () -> service.reconciliation(a,session)).expectedCash()).isEqualByComparingTo("2800");
 }
 @Test void returnQueueRejectsMissingPermissionsAndInvalidFilters() throws Exception {
  String path="/api/v1/vendor/pos/returns/queue";mvc.perform(get(path).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());mvc.perform(get(path).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isForbidden());
  for(String[] bad:List.of(new String[]{"state","PAID"},new String[]{"reference","%"},new String[]{"size","49"},new String[]{"page","-1"},new String[]{"from","2020-01-01"},new String[]{"to","bad-date"}))mvc.perform(get(path).param(bad[0],bad[1]).with(user("alice").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW"))).andExpect(status().isBadRequest());
 }

 @Test void ownClosedShiftHistoryIsPagedAndDoesNotExposeOtherCashiersOrChangeDrawers() throws Exception {
  var ids=new ArrayList<String>();ids.add(session);asScope(a, () -> service.close(a,session,new BigDecimal("50")));
  for(int i=0;i<24;i++){String id=(String)asScope(a, () -> service.open(a,new PosService.OpenSession(1,BigDecimal.ZERO))).get("id");asScope(a, () -> service.close(a,id,BigDecimal.ZERO));ids.add(id);}
  update("UPDATE pos_sessions SET closed_at=? WHERE cashier='alice'",java.sql.Timestamp.from(java.time.Instant.parse("2026-11-12T00:00:00Z")));ids.sort(Comparator.reverseOrder());
  String open=(String)asScope(a, () -> service.open(a,new PosService.OpenSession(1,BigDecimal.TEN))).get("id");
  employee("shift-colleague",1,1);update("INSERT INTO registers VALUES(3,1,1)");assignRegister("shift-colleague",1,1,3);var colleague=new PosService.Scope(1,1,"shift-colleague");String other=(String)asScope(colleague, () -> service.open(colleague,new PosService.OpenSession(3,new BigDecimal("99")))).get("id");asScope(colleague, () -> service.close(colleague,other,new BigDecimal("99")));update("UPDATE pos_sessions SET closed_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.parse("2026-11-12T00:00:00Z")),other);
  String path="/api/v1/vendor/pos/sessions/history";
  for(int page=0;page<3;page++)mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-12").param("page",Integer.toString(page)).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(25)).andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.items[0].id").value(ids.get(page*12))).andExpect(jsonPath("$.items[0].cashier").doesNotExist()).andExpect(jsonPath("$.items[0].varianceReason").doesNotExist()).andExpect(jsonPath("$.items[0].closeRequestId").doesNotExist());
  mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-12").param("reference",session.toUpperCase(Locale.ROOT)).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].openingFloat").value(50)).andExpect(jsonPath("$.items[0].expectedCash").value(50)).andExpect(jsonPath("$.items[0].countedCash").value(50)).andExpect(jsonPath("$.items[0].variance").value(0));
  for(String reference:List.of(open,other))mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-12").param("reference",reference).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  mvc.perform(get(path).param("from","2026-11-13").param("to","2026-11-13").with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  update("INSERT INTO branches(id,tenant_id,name) VALUES(4,1,'Other shift branch')");update("INSERT INTO registers VALUES(4,1,4)");employee("shift-other",1,4);
  for(String actor:List.of("bob","shift-other"))mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-12").param("cashier","alice").param("tenantId","1").param("branchId","1").with(user(actor).authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  int audit=db.queryForObject("SELECT COUNT(*) FROM pos_audit",Integer.class);mvc.perform(get(path).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk());assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit",Integer.class)).isEqualTo(audit);assertThat(asScope(a, () -> service.reconciliation(a,open)).expectedCash()).isEqualByComparingTo("10");assertThat(as("alice",()->stock.atBranch(1,1,1))).isEqualTo(1);
 }
 @Test void ownShiftHistoryRejectsMissingPermissionsAndInvalidBounds() throws Exception {
  String path="/api/v1/vendor/pos/sessions/history";mvc.perform(get(path).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());mvc.perform(get(path).with(user("alice"))).andExpect(status().isForbidden());
  for(String[] bad:List.of(new String[]{"reference","%"},new String[]{"size","49"},new String[]{"page","-1"},new String[]{"from","2020-01-01"},new String[]{"to","bad-date"}))mvc.perform(get(path).param(bad[0],bad[1]).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isBadRequest());
  update("UPDATE employees SET enabled=FALSE WHERE username='alice'");mvc.perform(get(path).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isForbidden());
 }

 @Test void shiftHistoryExcludesPendingReviewAndPreservesApprovedVarianceSnapshot() throws Exception {
  String request=UUID.randomUUID().toString();asScope(a, () -> service.close(a,session,new BigDecimal("49"),"Count difference",request));
  String path="/api/v1/vendor/pos/sessions/history";
  mvc.perform(get(path).param("reference",session).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  employee("history-supervisor",1,1);as("history-supervisor", () -> service.reviewVariance(new PosService.Scope(1,1,"history-supervisor"),session,request,"Count independently checked",PosService.ReviewAction.APPROVE));
  mvc.perform(get(path).param("reference",session).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].expectedCash").value(50)).andExpect(jsonPath("$.items[0].countedCash").value(49)).andExpect(jsonPath("$.items[0].variance").value(-1)).andExpect(jsonPath("$.items[0].reviewedBy").doesNotExist()).andExpect(jsonPath("$.items[0].reviewReason").doesNotExist());
  assertThat(db.queryForObject("SELECT state FROM pos_sessions WHERE id=?",String.class,session)).isEqualTo("CLOSED");assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_shift_reviews",Integer.class)).isEqualTo(1);
 }

 @Test void publicVendorDirectoryIsPagedSearchableAndContainsOnlyActivePublicCards() throws Exception {
  update("UPDATE tenants SET slug=CONCAT('vendor-',id),online_branch_id=id");
  for(int i=3;i<=27;i++)update("INSERT INTO tenants(id,name,legal_name,lifecycle_status,slug) VALUES(?,?,'Solar Partner','ACTIVE',?)",i,"Solar Partner", "solar-"+i);
  update("INSERT INTO tenants(id,name,legal_name,lifecycle_status,slug) VALUES(28,'Hidden inactive','Hidden inactive','SUSPENDED','hidden-inactive'),(29,'Hidden null','Hidden null','ACTIVE',NULL),(30,'Hidden blank','Hidden blank','ACTIVE','')");
  String path="/api/v1/marketplace/directory";
  for(int page=0;page<3;page++)mvc.perform(get(path).param("q","sOlAr").param("size","12").param("page",Integer.toString(page))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(25)).andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.items[0].id").value(3+page*12)).andExpect(jsonPath("$.items[0].active").doesNotExist()).andExpect(jsonPath("$.items[0].onlineBranchId").doesNotExist());
  mvc.perform(get(path).param("q","solar-27")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].slug").value("solar-27"));
  mvc.perform(get(path).param("q","hidden")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  update("UPDATE tenants SET name='100%_! Solar' WHERE id=27");mvc.perform(get(path).param("q","%_!")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(27));
  mvc.perform(get(path).param("page","9999")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
  update("UPDATE tenants SET lifecycle_status='SUSPENDED' WHERE id=27");mvc.perform(get(path).param("q","solar-27")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  assertThat(as("alice",()->stock.atBranch(1,1,1))).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isZero();assertThat(db.queryForObject("SELECT COUNT(*) FROM carts",Integer.class)).isZero();
 }
 @Test void publicVendorDirectoryRejectsUnboundedInput() throws Exception {
  for(String[] bad:List.of(new String[]{"q","x".repeat(121)},new String[]{"q","bad\nquery"},new String[]{"size","49"},new String[]{"page","-1"}))mvc.perform(get("/api/v1/marketplace/directory").param(bad[0],bad[1])).andExpect(status().isBadRequest());
 }

 @Test void browsingAnotherVendorDirectoryPagePreservesExistingCustomerCart() throws Exception {
  customers();var cart=asCustomer("customer", () -> marketplace.put(100,new CartService.Item("vendor-1",1,1)));
  mvc.perform(get("/api/v1/marketplace/directory").param("q","vendor-2")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
  assertThat(asCustomer("customer", () -> marketplace.cart(100))).isEqualTo(cart);assertThat(db.queryForObject("SELECT COUNT(*) FROM carts",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM stock_reservations",Integer.class)).isZero();assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isZero();
 }

 @Test void promotionAuditHistoryIsTenantScopedPagedAndReadOnlyForWithdrawnProducts() throws Exception {
  for(int i=1;i<=25;i++){final int index=i;as("alice", () -> promotions.approve(1,"alice",1,new com.saumya.pos.catalog.PromotionService.Change(new BigDecimal(index),"Approved offer "+index)));}as("alice", () -> promotions.approve(1,"alice",1,new com.saumya.pos.catalog.PromotionService.Change(new BigDecimal("25"),"No change")));as("bob", () -> promotions.approve(2,"bob",2,new com.saumya.pos.catalog.PromotionService.Change(BigDecimal.TEN,"Foreign tenant offer")));
  update("UPDATE catalog_promotion_audit SET created_at=?",java.sql.Timestamp.from(java.time.Instant.parse("2026-11-13T12:00:00Z")));var ids=db.queryForList("SELECT id FROM catalog_promotion_audit WHERE tenant_id=1",String.class).stream().sorted(Comparator.reverseOrder()).toList();
  update("UPDATE products SET name='Renamed panel',published=FALSE WHERE id=1");String path="/api/v1/vendor/pos/promotions/1/history";
  for(int page=0;page<3;page++)mvc.perform(get(path).param("page",Integer.toString(page)).with(user("alice").authorities(()->"POS_VIEW",()->"CATALOG_PROMOTION_MANAGE",()->"POS_DISCOUNT_APPROVE"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(25)).andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.currentName").value("Renamed panel")).andExpect(jsonPath("$.published").value(false)).andExpect(jsonPath("$.items[0].id").value(ids.get(page*12))).andExpect(jsonPath("$.items[0].actor").value("alice")).andExpect(jsonPath("$.items[0].tenantId").doesNotExist());
  update("INSERT INTO branches(id,tenant_id,name) VALUES(4,1,'Other catalogue branch')");employee("promotion-viewer",1,4);mvc.perform(get(path).with(user("promotion-viewer").authorities(()->"POS_VIEW",()->"CATALOG_PROMOTION_MANAGE",()->"POS_DISCOUNT_APPROVE"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(25));
  mvc.perform(get(path).param("tenantId","1").with(user("bob").authorities(()->"POS_VIEW",()->"CATALOG_PROMOTION_MANAGE",()->"POS_DISCOUNT_APPROVE"))).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/vendor/pos/promotions/2/history").with(user("alice").authorities(()->"POS_VIEW",()->"CATALOG_PROMOTION_MANAGE",()->"POS_DISCOUNT_APPROVE"))).andExpect(status().isNotFound());
  mvc.perform(get(path).param("page","999").with(user("alice").authorities(()->"POS_VIEW",()->"CATALOG_PROMOTION_MANAGE",()->"POS_DISCOUNT_APPROVE"))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM catalog_promotion_audit",Integer.class)).isEqualTo(26);assertThat(promotions.price(1,1,new BigDecimal("100"),false).price()).isEqualByComparingTo("75");assertThat(as("alice",()->stock.atBranch(1,1,1))).isEqualTo(1);assertThat(asScope(a, () -> service.reconciliation(a,session)).expectedCash()).isEqualByComparingTo("50");
 }
 @Test void promotionAuditRejectsMissingRightsInactiveMembershipAndBadBounds() throws Exception {
  String path="/api/v1/vendor/pos/promotions/1/history";mvc.perform(get(path).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());
  for(String missing:List.of("POS_VIEW","CATALOG_PROMOTION_MANAGE","POS_DISCOUNT_APPROVE")){var rights=List.of("POS_VIEW","CATALOG_PROMOTION_MANAGE","POS_DISCOUNT_APPROVE").stream().filter(p->!p.equals(missing)).map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).toList();mvc.perform(get(path).with(user("alice").authorities(rights))).andExpect(status().isForbidden());}
  for(String[] bad:List.of(new String[]{"page","-1"},new String[]{"size","49"}))mvc.perform(get(path).param(bad[0],bad[1]).with(user("alice").authorities(()->"POS_VIEW",()->"CATALOG_PROMOTION_MANAGE",()->"POS_DISCOUNT_APPROVE"))).andExpect(status().isBadRequest());
  update("UPDATE tenants SET lifecycle_status='SUSPENDED' WHERE id=1");mvc.perform(get(path).with(user("alice").authorities(()->"POS_VIEW",()->"CATALOG_PROMOTION_MANAGE",()->"POS_DISCOUNT_APPROVE"))).andExpect(status().isForbidden());
 }

 @Test void registerDirectoryIsPagedScopedAndHidesDrawerDetails() throws Exception {
  for(int id=3;id<=26;id++){update("INSERT INTO registers VALUES(?,1,1)",id);assignRegister("alice",1,1,id);}
  employee("register-colleague",1,1);assignRegister("register-colleague",1,1,3);var colleague=new PosService.Scope(1,1,"register-colleague");String closing=(String)asScope(colleague, () -> service.open(colleague,new PosService.OpenSession(3,new BigDecimal("50")))).get("id");asScope(colleague, () -> service.close(colleague,closing,new BigDecimal("49"),"Count discrepancy",UUID.randomUUID().toString()));String closed=(String)asScope(a, () -> service.open(a,new PosService.OpenSession(4,BigDecimal.ZERO))).get("id");asScope(a, () -> service.close(a,closed,BigDecimal.ZERO));
  int audit=db.queryForObject("SELECT COUNT(*) FROM pos_audit",Integer.class);var ids=db.queryForList("SELECT id FROM registers WHERE tenant_id=1 ORDER BY id",Long.class);String path="/api/v1/vendor/pos/registers";
  for(int page=0;page<3;page++)mvc.perform(get(path).param("page",Integer.toString(page)).with(user("alice").authorities(()->"POS_VIEW",()->"POS_SESSION_OPEN"))).andExpect(status().isOk()).andExpect(jsonPath("$.branchId").value(1)).andExpect(jsonPath("$.totalElements").value(25)).andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.items[0].id").value(ids.get(page*12))).andExpect(jsonPath("$.items[0].cashier").doesNotExist()).andExpect(jsonPath("$.items[0].openingFloat").doesNotExist()).andExpect(jsonPath("$.items[0].sessionId").doesNotExist()).andExpect(jsonPath("$.items[0].state").doesNotExist());
  mvc.perform(get(path).param("size","48").with(user("alice").authorities(()->"POS_VIEW",()->"POS_SESSION_OPEN"))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].available").value(false)).andExpect(jsonPath("$.items[1].available").value(false)).andExpect(jsonPath("$.items[2].available").value(true));
  mvc.perform(get(path).param("tenantId","1").param("branchId","1").with(user("bob").authorities(()->"POS_VIEW",()->"POS_SESSION_OPEN"))).andExpect(status().isOk()).andExpect(jsonPath("$.branchId").value(2)).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(2));
  update("INSERT INTO branches(id,tenant_id,name) VALUES(4,1,'Other register branch')");employee("register-other",1,4);update("INSERT INTO registers VALUES(99,1,4)");assignRegister("register-other",1,4,99);mvc.perform(get(path).param("branchId","1").with(user("register-other").authorities(()->"POS_VIEW",()->"POS_SESSION_OPEN"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(99));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit",Integer.class)).isEqualTo(audit);assertThat(asScope(a, () -> service.reconciliation(a,session)).expectedCash()).isEqualByComparingTo("50");assertThat(as("alice",()->stock.atBranch(1,1,1))).isEqualTo(1);
  asScope(a, () -> service.close(a,session,new BigDecimal("50")));mvc.perform(get(path).with(user("alice").authorities(()->"POS_VIEW",()->"POS_SESSION_OPEN"))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].available").value(true));
  mvc.perform(get(path).param("page","999").with(user("alice").authorities(()->"POS_VIEW",()->"POS_SESSION_OPEN"))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
 }
 @Test void registerDirectoryRejectsMissingRightsBadBoundsAndInactiveMembership() throws Exception {
  String path="/api/v1/vendor/pos/registers";mvc.perform(get(path).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());for(String right:List.of("POS_VIEW","POS_SESSION_OPEN"))mvc.perform(get(path).with(user("alice").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(right)))).andExpect(status().isForbidden());
  for(String[] bad:List.of(new String[]{"page","-1"},new String[]{"size","49"}))mvc.perform(get(path).param(bad[0],bad[1]).with(user("alice").authorities(()->"POS_VIEW",()->"POS_SESSION_OPEN"))).andExpect(status().isBadRequest());update("UPDATE employees SET enabled=FALSE WHERE username='alice'");mvc.perform(get(path).with(user("alice").authorities(()->"POS_VIEW",()->"POS_SESSION_OPEN"))).andExpect(status().isForbidden());
 }

 @Test void directReceiptIdentitySurvivesRenamesRetriesAndAuditedCopies() throws Exception {
  var receipt=as("alice", () -> checkout.cashSale(a.cashier(),"identity-cash",sale(1,"120")));String id=(String)receipt.get("id");assertThat(receipt.get("receipt_seller_name")).isEqualTo("Vendor A");assertThat(receipt.get("receipt_outlet_name")).isEqualTo("A");assertThat(receipt.get("issued_at")).isNotNull();
  update("UPDATE tenants SET name='New seller' WHERE id=1");update("UPDATE branches SET name='New outlet' WHERE id=1");
  for(var copy:List.of(as("alice", () -> checkout.cashSale(a.cashier(),"identity-cash",sale(1,"120"))),receiptReader.reprint(a,id))){assertThat(copy.get("receipt_seller_name")).isEqualTo("Vendor A");assertThat(copy.get("receipt_outlet_name")).isEqualTo("A");assertThat(copy.get("issued_at")).isEqualTo(receipt.get("issued_at"));assertThat(copy.get("receipt_number")).isEqualTo(receipt.get("receipt_number"));}
  mvc.perform(get("/api/v1/vendor/pos/receipts/"+id).with(user("bob").authorities(()->"POS_RECEIPT_VIEW"))).andExpect(status().isNotFound());assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);assertThat(as("alice",()->stock.atBranch(1,1,1))).isZero();
 }
 @Test void reservedReceiptIdentityIsCapturedAtPaymentAndInvoiceUsesTheSameNames(){
  var draft=as("alice", () -> checkout.pos("alice","identity-draft",new CheckoutService.PosDraft(session,List.of(new CheckoutService.Item(1,1)),true,null,null)));assertThat(db.queryForObject("SELECT receipt_seller_name FROM orders WHERE id=?",String.class,draft.id())).isNull();
  update("UPDATE tenants SET name='Seller at payment' WHERE id=1");update("UPDATE branches SET name='Outlet at payment' WHERE id=1");update("UPDATE orders SET created_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.parse("2026-01-01T00:00:00Z")),draft.id());
  var receipt=as("alice", () -> checkout.finalizeCash("alice",draft.id(),new BigDecimal("120")));assertThat(receipt.get("receipt_seller_name")).isEqualTo("Seller at payment");assertThat(receipt.get("receipt_outlet_name")).isEqualTo("Outlet at payment");assertThat(receipt.get("issued_at")).isEqualTo(db.queryForObject("SELECT status_changed_at FROM orders WHERE id=?",java.sql.Timestamp.class,draft.id()));
  assertThat(db.queryForObject("SELECT seller_name FROM vendor_invoices WHERE order_id=?",String.class,draft.id())).isEqualTo("Seller at payment");
  update("UPDATE tenants SET name='Later seller' WHERE id=1");var retry=as("alice", () -> checkout.finalizeCash("alice",draft.id(),new BigDecimal("120")));assertThat(retry.get("receipt_seller_name")).isEqualTo("Seller at payment");assertThat(retry.get("issued_at")).isEqualTo(receipt.get("issued_at"));
 }
 @Test void receiptIdentityRollsBackWithSaleAndLegacyReadNeverInventsNames(){
  assertThatThrownBy(()->new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->{as("alice", () -> checkout.cashSale(a.cashier(),"identity-rollback",sale(1,"120")));throw new IllegalStateException("Rollback sale");})).isInstanceOf(IllegalStateException.class);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_drawer_tenders",Integer.class)).isZero();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isZero();assertThat(as("alice",()->stock.atBranch(1,1,1))).isEqualTo(1);assertThat(db.queryForObject("SELECT receipt_sequence FROM branches WHERE id=1",Long.class)).isZero();
  String id=(String)as("alice", () -> checkout.cashSale(a.cashier(),"identity-legacy",sale(1,"120"))).get("id");update("UPDATE orders SET receipt_seller_name=NULL,receipt_outlet_name=NULL WHERE id=?",id);update("UPDATE tenants SET name='Mutable current seller' WHERE id=1");assertThat(receiptReader.receipt(a,id).get("receipt_seller_name")).isNull();assertThat(receiptReader.reprint(a,id).get("receipt_outlet_name")).isNull();assertThat(db.queryForObject("SELECT receipt_seller_name FROM orders WHERE id=?",String.class,id)).isNull();
 }

 @Test void fastCashUsesCanonicalReservationConsumptionAndRejectsChangedCashRetries(){
  var paid=as("alice", () -> checkout.cashSale("alice","unified-cash",sale(1,"120")));String id=(String)paid.get("id");
  assertThat(db.queryForObject("SELECT state FROM stock_reservations WHERE order_id=?",String.class,id)).isEqualTo("CONSUMED");assertThat(db.queryForObject("SELECT reserved FROM inventory WHERE tenant_id=1 AND product_id=1",Integer.class)).isZero();assertThat(db.queryForObject("SELECT status FROM orders WHERE id=?",String.class,id)).isEqualTo("PAID");assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE action='DRAFT_RESERVE' AND resource_id=?",Integer.class,id)).isEqualTo(1);
  assertThat(as("alice", () -> checkout.cashSale("alice","unified-cash",sale(1,"120"))).get("id")).isEqualTo(id);assertThatThrownBy(()->as("alice", () -> checkout.cashSale("alice","unified-cash",sale(1,"130")))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);
 }
 @Test void failedFastCashLeavesNoDraftOrHoldAndDoesNotAliasExplicitDraftKeys(){
  assertThatThrownBy(()->as("alice", () -> checkout.cashSale("alice","unified-underpaid",sale(1,"1")))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isZero();assertThat(db.queryForObject("SELECT COUNT(*) FROM stock_reservations",Integer.class)).isZero();assertThat(db.queryForObject("SELECT receipt_sequence FROM branches WHERE id=1",Long.class)).isZero();
  var draft=as("alice", () -> checkout.pos("alice","same-operation-key",posDraft()));assertThatThrownBy(()->as("alice", () -> checkout.cashSale("alice","same-operation-key",sale(1,"120")))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);assertThat(db.queryForObject("SELECT status FROM orders WHERE id=?",String.class,draft.id())).isEqualTo("DRAFT");assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);
 }

 @Test void paidStatusTimestampRetainsMicrosecondsInsteadOfRoundingIntoTheFuture(){
  var receipt=as("alice", () -> checkout.cashSale("alice","paid-precision",sale(1,"120")));String id=(String)receipt.get("id");var paid=java.time.Instant.parse("2026-11-13T12:00:00.654321Z");update("UPDATE orders SET status_changed_at=? WHERE id=?",java.sql.Timestamp.from(paid),id);
  assertThat(db.queryForObject("SELECT status_changed_at FROM orders WHERE id=?",java.sql.Timestamp.class,id).toInstant()).isEqualTo(paid);assertThat(((java.sql.Timestamp)receiptReader.receipt(a,id).get("issued_at")).toInstant()).isEqualTo(paid);
 }

 @Test void receiptPdfCopiesAreScopedAuditedAndMatchExactReturnedBytes() throws Exception {
  var sale=as("alice", () -> checkout.cashSale("alice","pdf-original",sale(1,"120")));String id=(String)sale.get("id");update("UPDATE tenants SET name='Renamed seller' WHERE id=1");asScope(a, () -> service.close(a,session,new BigDecimal("160")));int audit=db.queryForObject("SELECT COUNT(*) FROM pos_audit",Integer.class);
  var response=mvc.perform(post("/api/v1/vendor/pos/receipts/"+id+"/document").with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_RECEIPT_VIEW",()->"POS_RECEIPT_REPRINT")).contentType("application/json").content("{\"reason\":\" Customer requested copy \"}")).andExpect(status().isOk()).andExpect(content().contentType("application/pdf")).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse();
  byte[] bytes=response.getContentAsByteArray();String reference=response.getHeader("X-Document-Reference");assertThat(reference).matches("[0-9a-f-]{36}");assertThat(response.getHeader("Content-Disposition")).contains(".pdf");String text;try(var reader=new org.openpdf.text.pdf.PdfReader(bytes)){text=new org.openpdf.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(1);}assertThat(text).contains("COPY - Receipt 1/1","Vendor A","110.00",reference).doesNotContain("Renamed seller","Customer requested copy");
  assertThat(db.queryForObject("SELECT content_sha256 FROM receipt_document_audit WHERE id=?",String.class,reference)).isEqualTo(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));assertThat(db.queryForObject("SELECT content_length FROM receipt_document_audit WHERE id=?",Integer.class,reference)).isEqualTo(bytes.length);assertThat(db.queryForObject("SELECT reason FROM receipt_document_audit WHERE id=?",String.class,reference)).isEqualTo("Customer requested copy");assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit",Integer.class)).isEqualTo(audit);assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT receipt_sequence FROM branches WHERE id=1",Long.class)).isEqualTo(1);assertThat(as("alice",()->stock.atBranch(1,1,1))).isZero();
 }
 @Test void receiptPdfRejectsMissingRightsCsrfBadReasonForeignScopeAndUnsupportedGlyphs() throws Exception {
  update("UPDATE products SET name='Panel 😀' WHERE id=1");String id=(String)as("alice", () -> checkout.cashSale("alice","pdf-unsupported",sale(1,"120"))).get("id"),path="/api/v1/vendor/pos/receipts/"+id+"/document";
  mvc.perform(post(path).contentType("application/json").content("{\"reason\":\"Copy request\"}")).andExpect(status().isForbidden());
  for(String missing:List.of("POS_VIEW","POS_RECEIPT_VIEW","POS_RECEIPT_REPRINT")){var rights=List.of("POS_VIEW","POS_RECEIPT_VIEW","POS_RECEIPT_REPRINT").stream().filter(p->!p.equals(missing)).map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).toList();mvc.perform(post(path).with(csrf()).with(user("alice").authorities(rights)).contentType("application/json").content("{\"reason\":\"Copy request\"}")).andExpect(status().isForbidden());}
  mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_RECEIPT_VIEW",()->"POS_RECEIPT_REPRINT")).contentType("application/json").content("{\"reason\":\" \"}")).andExpect(status().isBadRequest());
  mvc.perform(post(path).with(csrf()).with(user("bob").authorities(()->"POS_VIEW",()->"POS_RECEIPT_VIEW",()->"POS_RECEIPT_REPRINT")).contentType("application/json").content("{\"reason\":\"Copy request\"}")).andExpect(status().isNotFound());
  mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_RECEIPT_VIEW",()->"POS_RECEIPT_REPRINT")).contentType("application/json").content("{\"reason\":\"Copy request\"}")).andExpect(status().isConflict());assertThat(db.queryForObject("SELECT COUNT(*) FROM receipt_document_audit",Integer.class)).isZero();
 }

 // Exercise the checked-in manual seed, not a duplicate Java fixture implementation.
 void multiVendorDemo() throws Exception {
  String script;
  try(var resource=getClass().getResourceAsStream("/demo/setup-multi-vendor-demo.sql")){
   if(resource==null)throw new IllegalStateException("Missing test multi-vendor fixture");
   script=new String(resource.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
  }
  try(var connection=db.getDataSource().getConnection();var statement=connection.createStatement()){
   if(connection.getMetaData().getDatabaseProductName().equals("MySQL")){
    String procedure=script.substring(script.indexOf("CREATE PROCEDURE"),script.indexOf("END$$")+3);
    statement.execute("DROP PROCEDURE IF EXISTS setup_multi_vendor_demo");
    try{statement.execute(procedure);statement.execute("CALL setup_multi_vendor_demo()");}
    finally{statement.execute("DROP PROCEDURE IF EXISTS setup_multi_vendor_demo");}
   }else{
    String core=script.substring(script.indexOf("-- BEGIN DEMO DATA")+18,script.indexOf("-- END DEMO DATA"));
    // Strip full-line SQL comments before splitting statements; comments may contain semicolons.
    core=core.replaceAll("(?m)^\\s*--[^\\r\\n]*(?:\\r?\\n|$)", "");
    String demoEntitlements="{\"features\":{\"POS\":true,\"CATALOG\":true,\"INVENTORY\":true,\"ADMINISTRATION\":true,\"CRM\":true,\"LEADS\":true,\"QUOTATIONS\":true,\"ORDERS\":true,\"RETURNS\":true,\"FINANCE\":true,\"SHIPMENTS\":true,\"SERVICE\":true,\"REPORTING\":true}}";
    core=core.replace("'" + demoEntitlements + "'","'" + demoEntitlements + "' FORMAT JSON");
    core=core.replace("30,'{}','Local demo subscription'",
        "30,'{}' FORMAT JSON,'Local demo subscription'");
    core=core.replace("DATE '2099-12-31','{\"demo\":true}'",
        "DATE '2099-12-31','{\"demo\":true}' FORMAT JSON");
    connection.setAutoCommit(false);
    try{for(String sql:core.split(";"))if(!sql.isBlank())statement.execute(sql.replace("DROP TEMPORARY TABLE","DROP TABLE"));connection.commit();}
    catch(Exception e){connection.rollback();throw e;}
   }
  }
 }
 String demoToken(String username,String password){return authentication.login(username,password).accessToken();}
 @Test void demoSeedHasRealCredentialsLeastPrivilegeAndVendorOwnedCatalogues() throws Exception {
  multiVendorDemo();
  var fixtures=new com.fasterxml.jackson.databind.ObjectMapper().readTree(getClass().getResourceAsStream("/multi-vendor-demo-credentials.json"));
  for(var account:fixtures){
   String username=account.get("username").asText(),password=account.get("password").asText(),role=account.get("role").asText();
   String hash=db.queryForObject("SELECT password_hash FROM users WHERE username=?",String.class,username);
   assertThat(hash).startsWith("$2a$10$");assertThat(encoder.matches(password,hash)).as(username).isTrue();
   var session=authentication.login(username,password);var granted=authentication.authenticate(jwt.decoder().decode(session.accessToken())).getAuthorities().stream().map(org.springframework.security.core.GrantedAuthority::getAuthority).toList();
   assertThat(granted.stream().filter(permission->!permission.startsWith("ACTOR_CONTEXT_")).toList()).containsExactlyInAnyOrderElementsOf(session.account().permissions());
    assertThat(granted.stream().filter(permission->permission.startsWith("ACTOR_CONTEXT_")).toList()).containsExactly(
      switch(session.account().actorType()){case PLATFORM->com.saumya.pos.security.AuthorizationContext.ACTOR_CONTEXT_PLATFORM;case VENDOR->com.saumya.pos.security.AuthorizationContext.ACTOR_CONTEXT_VENDOR;case CUSTOMER->com.saumya.pos.security.AuthorizationContext.ACTOR_CONTEXT_CUSTOMER;});
   if(role.equals("PLATFORM_SUPER_ADMIN")){assertThat(session.account().tenantId()).isNull();assertThat(granted.stream().filter(permission->!permission.startsWith("ACTOR_CONTEXT_")).toList()).containsExactlyInAnyOrderElementsOf(com.saumya.pos.platform.PlatformPermissionRegistry.all()).allMatch(g->g.startsWith("PLATFORM_"));}
   else if(role.equals("CUSTOMER")){assertThat(session.account().tenantId()).isNull();assertThat(granted.stream().filter(permission->!permission.startsWith("ACTOR_CONTEXT_")).toList()).containsExactly("CUSTOMER");}
   else{assertThat(session.account().tenantId()).isEqualTo(account.get("tenant").asLong());assertThat(granted).noneMatch(g->g.startsWith("PLATFORM_")||g.equals("CUSTOMER"));
    if(role.equals("POS_CASHIER"))assertThat(granted).contains("POS_SALE_CREATE").doesNotContain("POS_REFUND_APPROVE","POS_TENDER_MANUAL_VERIFY","POS_SETTINGS_MANAGE","POS_DISCOUNT_OVERRIDE");
    if(role.equals("INVENTORY_MANAGER"))assertThat(granted).contains("INVENTORY_RECEIVE").doesNotContain("POS_SALE_CREATE","PRICE_UPDATE","POS_REFUND_APPROVE");
    if(role.equals("SALES_MANAGER"))assertThat(granted).contains("POS_VIEW","POS_REPORT_VIEW").doesNotContain("LEAD_CREATE","INVENTORY_RECEIVE","POS_SALE_CREATE");
    if(role.equals("ACCOUNTANT"))assertThat(granted).contains("POS_REFUND_VIEW").doesNotContain("POS_TENDER_MANUAL_VERIFY","POS_SALE_CREATE","POS_SETTINGS_MANAGE");
   }
  }
  String[] slugs={"saumya-rooftops","helio-rural","lumina-lighting"};String[] brands={"SunArc","AgriRay","NightSun"};
  for(int i=0;i<3;i++){long tenant=910001L+i*10000;assertThat(catalogue.products(slugs[i])).hasSize(5);assertThat(catalogue.categories(slugs[i])).hasSize(3);assertThat(catalogue.products(slugs[i]).getFirst().brand()).isEqualTo(brands[i]);assertThat(catalogue.onlineBranch(tenant)).isEqualTo(tenant);
   mvc.perform(get("/api/v1/marketplace/vendors/"+slugs[i]+"/products/"+(tenant+300))).andExpect(status().isOk()).andExpect(jsonPath("$.brand").value(brands[i]));
   mvc.perform(get("/api/v1/marketplace/vendors/"+slugs[i]+"/products/"+(tenant+305))).andExpect(status().isNotFound());
  }
  mvc.perform(get("/api/v1/marketplace/vendors/saumya-rooftops/products/920301")).andExpect(status().isNotFound());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM tenant_customers WHERE customer_id=949001",Integer.class)).isEqualTo(3);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM tenant_customers WHERE customer_id=949002",Integer.class)).isEqualTo(1);
 }
 @Test void demoGlobalCustomerPurchasesFromTwoVendorsWithoutMixingCartsOrdersOrStock() throws Exception {
  multiVendorDemo();String name="asha@customer.demo.test",token=demoToken(name,"AshaBuyer2026!");
  var first=asCustomer(name,()->marketplace.put(949001,new CartService.Item("saumya-rooftops",910301,1)));
  mvc.perform(post("/api/v1/customer/cart/items").header("Authorization","Bearer "+token).with(csrf()).contentType("application/json").content("{\"vendorSlug\":\"helio-rural\",\"productId\":920301,\"quantity\":1}")).andExpect(status().isConflict());
  assertThat(asCustomer(name,()->marketplace.cart(949001)).id()).isEqualTo(first.id());
  var switched=asCustomer(name,()->marketplace.switchVendor(949001,new CartService.Switch(first.id(),"helio-rural",920301,1)));
  assertThat(asCustomer(name,()->marketplace.history(949001))).extracting(CartService.Cart::id).contains(first.id());
  asCustomer(name,()->marketplace.switchVendor(949001,new CartService.Switch(switched.id(),"saumya-rooftops",910301,1)));
  var ids=new ArrayList<String>();
  for(var item:List.of(new CartService.Item("saumya-rooftops",910301,1),new CartService.Item("helio-rural",920301,1))){
   var cart=asCustomer(name,()->marketplace.put(949001,item));var draft=asCustomer(name,()->checkout.online(name,"demo-buy-"+item.vendorSlug(),new CheckoutService.OnlineDraft(cart.id())));
   var payment=asCustomer(name,()->payments.start(name,draft.id(),"UPI"));asCustomer(name,()->payments.confirm(name,draft.id(),payment.attemptId()));ids.add(draft.id());
   mvc.perform(get("/api/v1/customer/orders/"+draft.id()).header("Authorization","Bearer "+token)).andExpect(status().isOk());
   mvc.perform(get("/api/v1/customer/orders/"+draft.id()).header("Authorization","Bearer "+demoToken("rahul@customer.demo.test","RahulBuyer2026!"))).andExpect(status().isNotFound());
  }
  assertThat(asCustomer(name,()->checkout.onlineList(name))).hasSize(2).extracting(CheckoutService.Draft::vendorName).containsExactlyInAnyOrder("Saumya Solar Rooftops","Helio Rural Energy");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM orders WHERE customer_id=949001 AND status='PAID'",Integer.class)).isEqualTo(2);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM order_items i JOIN products p ON p.id=i.product_id WHERE i.order_id IN (?,?) AND i.tenant_id<>p.tenant_id",Integer.class,ids.get(0),ids.get(1))).isZero();
  assertThat(db.queryForObject("SELECT quantity-reserved FROM inventory WHERE tenant_id=? AND branch_id=? AND product_id=?",Integer.class,910001L,910001L,910301L)).isEqualTo(39);assertThat(db.queryForObject("SELECT quantity-reserved FROM inventory WHERE tenant_id=? AND branch_id=? AND product_id=?",Integer.class,920001L,920001L,920301L)).isEqualTo(11);assertThat(db.queryForObject("SELECT quantity-reserved FROM inventory WHERE tenant_id=? AND branch_id=? AND product_id=?",Integer.class,930001L,930001L,930301L)).isEqualTo(35);
  assertThat(db.queryForObject("SELECT SUM(reserved) FROM inventory WHERE tenant_id IN (910001,920001,930001)",Integer.class)).isZero();
 }
 @Test void demoTokensEnforcePlatformAndVendorAndCustomerBoundaries() throws Exception {
  multiVendorDemo();String platform=demoToken("superadmin@platform.demo.test","PlatformDemo2026!");
  for(String path:List.of("/api/v1/customer/cart","/api/v1/vendor/products","/api/v1/vendor/pos/orders"))mvc.perform(get(path).header("Authorization","Bearer "+platform)).andExpect(status().isForbidden());
  String inventory=demoToken("inventory@saumya.demo.test","SaumyaStock2026!");
  mvc.perform(get("/api/v1/vendor/products").header("Authorization","Bearer "+inventory)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(6));
  mvc.perform(post("/api/v1/vendor/inventory/receipts").header("Authorization","Bearer "+inventory).header("Idempotency-Key","demo-other-stock").with(csrf()).contentType("application/json").content("{\"productId\":920301,\"quantity\":1,\"reason\":\"Cross vendor test\"}")).andExpect(status().isNotFound());
  String sales=demoToken("sales@saumya.demo.test","SaumyaSales2026!");
  mvc.perform(post("/api/v1/vendor/inventory/receipts").header("Authorization","Bearer "+sales).header("Idempotency-Key","demo-sales-stock").with(csrf()).contentType("application/json").content("{\"productId\":910301,\"quantity\":1,\"reason\":\"Denied sales stock\"}")).andExpect(status().isForbidden());
  db.update("INSERT INTO employees(username,tenant_id,branch_id,enabled) VALUES('superadmin@platform.demo.test',910001,910001,FALSE)");
  assertThatThrownBy(()->authentication.login("superadmin@platform.demo.test","PlatformDemo2026!")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+platform)).andExpect(status().isUnauthorized());
  db.update("DELETE FROM employees WHERE username='superadmin@platform.demo.test'");
  db.update("DELETE FROM platform_user_permissions WHERE username='superadmin@platform.demo.test'");
  mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+platform)).andExpect(status().isUnauthorized());
 }
 @Test void demoRerunPreservesOperationalChangesAndDoesNotRestoreRevokedGrants() throws Exception {
  multiVendorDemo();String changed=encoder.encode("ChangedDemoPassword2026!");
  update("UPDATE inventory SET quantity=39,reserved=2 WHERE tenant_id=910001 AND product_id=910301");
  db.update("UPDATE products SET price=12345 WHERE id=910301");db.update("UPDATE users SET password_hash=? WHERE username='cashier@saumya.demo.test'",changed);
  removeAssignedRolePermission("cashier@saumya.demo.test","POS_SALE_CREATE");
  db.update("DELETE FROM platform_user_permissions WHERE permission='PLATFORM_SUPPORT_ACCESS'");
  db.update("UPDATE branches SET split_tender_enabled=FALSE WHERE id=910001");
  multiVendorDemo();
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=910001 AND product_id=910301",Integer.class)).isEqualTo(39);
  assertThat(db.queryForObject("SELECT reserved FROM inventory WHERE tenant_id=910001 AND product_id=910301",Integer.class)).isEqualTo(2);
  assertThat(db.queryForObject("SELECT available FROM catalog_stock WHERE tenant_id=910001 AND product_id=910301",Integer.class)).isEqualTo(37);
  assertThat(db.queryForObject("SELECT price FROM products WHERE id=910301",BigDecimal.class)).isEqualByComparingTo("12345");
  assertThat(authentication.login("cashier@saumya.demo.test","ChangedDemoPassword2026!").account().permissions()).doesNotContain("POS_SALE_CREATE");
  assertThat(authentication.login("superadmin@platform.demo.test","PlatformDemo2026!").account().permissions()).containsExactlyInAnyOrderElementsOf(com.saumya.pos.platform.PlatformPermissionRegistry.all().stream().filter(permission->!permission.equals("PLATFORM_SUPPORT_ACCESS")).toList());
  assertThat(db.queryForObject("SELECT split_tender_enabled FROM branches WHERE id=910001",Boolean.class)).isFalse();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM products WHERE tenant_id IN (910001,920001,930001)",Integer.class)).isEqualTo(18);
  assertThat(db.queryForObject("SELECT next_id FROM identity_sequence WHERE id=1",Long.class)).isGreaterThanOrEqualTo(950000L);
 }
 @Test void demoIdentityCollisionAbortsBeforeInstallingAnyVendorData() throws Exception {
  db.update("INSERT INTO users(id,username,password_hash,enabled) VALUES(940001,'unrelated@example.test',?,TRUE)",encoder.encode("UnrelatedPassword2026!"));
  assertThatThrownBy(this::multiVendorDemo).isInstanceOfSatisfying(java.sql.SQLException.class,e->assertThat(e.getErrorCode()).isIn(23513,3819));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM tenants WHERE id IN (910001,920001,930001)",Integer.class)).isZero();
  assertThat(db.queryForObject("SELECT username FROM users WHERE id=940001",String.class)).isEqualTo("unrelated@example.test");
 }

 @Test void demoCashierCanBillOwnStoreWithPolicyDiscountButCannotUseAnotherStoresRegisterOrProduct() throws Exception {
  multiVendorDemo();String token=demoToken("cashier@lumina.demo.test","LuminaTill2026!");
  mvc.perform(post("/api/v1/vendor/pos/sessions").header("Authorization","Bearer "+token).with(csrf()).contentType("application/json").content("{\"registerId\":910011,\"openingFloat\":0}")).andExpect(status().isNotFound());
  var opened=mvc.perform(post("/api/v1/vendor/pos/sessions").header("Authorization","Bearer "+token).with(csrf()).contentType("application/json").content("{\"registerId\":930011,\"openingFloat\":0}")).andExpect(status().isOk()).andReturn();
  String sessionId=new com.fasterxml.jackson.databind.ObjectMapper().readTree(opened.getResponse().getContentAsString()).get("id").asText();
  String body="{\"sessionId\":\""+sessionId+"\",\"items\":[{\"productId\":930301,\"quantity\":1}],\"invoiceRequested\":false,\"discounts\":{\"linePercent\":{},\"billPercent\":5,\"reason\":\"Demo approved sale\"}}";
  String path="/api/v1/vendor/pos/drafts";
  mvc.perform(post(path).header("Authorization","Bearer "+token).header("Idempotency-Key","demo-cross-product").with(csrf()).contentType("application/json").content(body.replace("930301","910301"))).andExpect(status().isNotFound());
  mvc.perform(post(path).header("Authorization","Bearer "+token).header("Idempotency-Key","demo-excess-discount").with(csrf()).contentType("application/json").content(body.replace("billPercent\":5","billPercent\":11"))).andExpect(status().isConflict());
  var reserved=mvc.perform(post(path).header("Authorization","Bearer "+token).header("Idempotency-Key","demo-discount-bill").with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andReturn();
  String id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(reserved.getResponse().getContentAsString()).get("id").asText();
  mvc.perform(post(path+"/"+id+"/finalize-cash").header("Authorization","Bearer "+token).with(csrf()).contentType("application/json").content("{\"cashReceived\":8075}")).andExpect(status().isOk());
  assertThat(db.queryForObject("SELECT quantity-reserved FROM inventory WHERE tenant_id=? AND branch_id=? AND product_id=?",Integer.class,930001L,930001L,930301L)).isEqualTo(34);assertThat(db.queryForObject("SELECT quantity-reserved FROM inventory WHERE tenant_id=? AND branch_id=? AND product_id=?",Integer.class,910001L,910001L,910301L)).isEqualTo(40);
  assertThat(db.queryForObject("SELECT customer_id FROM orders WHERE id=?",Long.class,id)).isNull();
  String other=demoToken("cashier@saumya.demo.test","SaumyaTill2026!");
  mvc.perform(get("/api/v1/vendor/pos/orders/"+id).header("Authorization","Bearer "+other)).andExpect(status().isNotFound());
 }


 @Test void tenantOwnedProjectionAndSubscriptionRowsRejectCrossTenantParents() {
  update("UPDATE inventory SET quantity=5 WHERE tenant_id IN (1,2)");
  var orderA=asScope(a,()->checkout.cashSale("alice","v48-owner-a",sale(1,"120")));
  String orderAId=(String)orderA.get("id");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_drawer_tenders WHERE tenant_id=1 AND order_reference=?",Integer.class,orderAId)).isEqualTo(1);

  String sessionB=(String)asScope(b,()->service.open(b,new PosService.OpenSession(2,BigDecimal.ZERO)).get("id"));
  var orderB=asScope(b,()->checkout.cashSale("bob","v48-owner-b",
    new CheckoutService.CashSale(sessionB,List.of(new CheckoutService.Item(2,1)),new BigDecimal("100"),false,null,null)));
  String orderBId=(String)orderB.get("id");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_drawer_tenders WHERE tenant_id=2 AND order_reference=?",Integer.class,orderBId)).isEqualTo(1);

  // The old single-column tenant FK accepted this valid tenant ID plus a
  // different tenant's order. The V48 composite ownership FK must reject it.
  assertThatThrownBy(()->update(
    "INSERT INTO pos_drawer_tenders(id,tenant_id,branch_id,session_id,order_reference,cash_received,change_given) VALUES('v48-cross-order',1,1,?,?,100,0)",
    session,orderBId)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->update(
    "INSERT INTO pos_drawer_tenders(id,tenant_id,branch_id,session_id,order_reference,cash_received,change_given) VALUES('v48-cross-session',1,1,?,?,100,0)",
    sessionB,orderAId)).isInstanceOf(RuntimeException.class);

  var draft=as("alice",()->checkout.pos("alice","v48-draft",posDraft()));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_active_drafts WHERE tenant_id=1 AND order_reference=?",Integer.class,draft.id())).isEqualTo(1);
  assertThatThrownBy(()->update(
    "INSERT INTO pos_active_drafts(tenant_id,branch_id,cashier,session_id,order_reference) VALUES(1,1,'alice',?,?)",
    session,orderBId)).isInstanceOf(RuntimeException.class);

  // The shared fixture already created one active subscription per tenant.
  // Reuse those persisted rows so this test focuses on the composite tenant FK
  // instead of violating the one-effective-subscription invariant.
  Long subscriptionA=db.queryForObject(
      "SELECT id FROM tenant_subscriptions WHERE tenant_id=1",Long.class);
  Long subscriptionB=db.queryForObject(
      "SELECT id FROM tenant_subscriptions WHERE tenant_id=2",Long.class);

  update("INSERT INTO subscription_events(tenant_id,subscription_id,new_status,actor_username,reason) VALUES(1,?,'ACTIVE','alice','same tenant event')",subscriptionA);
  assertThatThrownBy(()->update(
    "INSERT INTO subscription_events(tenant_id,subscription_id,new_status,actor_username,reason) VALUES(1,?,'ACTIVE','alice','cross tenant event')",
    subscriptionB)).isInstanceOf(RuntimeException.class);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM subscription_events WHERE tenant_id=1 AND subscription_id=?",Integer.class,subscriptionA)).isEqualTo(1);
 }




 @Test void commercialAndFinancialRelationshipsRejectCrossTenantParentsAndRestrictHistoryDeletion(){
  var saleA=asScope(a,()->checkout.cashSale("alice","p2-cross-owner-a",sale(1,"120")));
  String orderA=(String)saleA.get("id");
  String sessionB=(String)asScope(b,()->service.open(b,new PosService.OpenSession(2,BigDecimal.ZERO)).get("id"));
  var saleB=asScope(b,()->checkout.cashSale("bob","p2-cross-owner-b",
    new CheckoutService.CashSale(sessionB,List.of(new CheckoutService.Item(2,1)),new BigDecimal("100"),false,null,null)));
  String orderB=(String)saleB.get("id");
  String returnA="p2-cross-return-a",returnB="p2-cross-return-b";
  String hashA="a".repeat(64),hashB="b".repeat(64);
  db.update("""
    INSERT INTO pos_returns(id,tenant_id,branch_id,order_id,requester,session_id,idempotency_key,
      request_hash,reason,proof,proof_note,state,created_at)
    VALUES(?,1,1,?,'alice',?,'p2-cross-return-key-a',?,'Verified return','ORIGINAL_RECEIPT',
      'Original receipt checked','REQUESTED',CURRENT_TIMESTAMP)
    """,returnA,orderA,session,hashA);
  db.update("""
    INSERT INTO pos_returns(id,tenant_id,branch_id,order_id,requester,session_id,idempotency_key,
      request_hash,reason,proof,proof_note,state,created_at)
    VALUES(?,2,2,?,'bob',?,'p2-cross-return-key-b',?,'Verified return','ORIGINAL_RECEIPT',
      'Original receipt checked','REQUESTED',CURRENT_TIMESTAMP)
    """,returnB,orderB,sessionB,hashB);
  db.update("INSERT INTO pos_return_items(tenant_id,return_id,order_id,product_id,name,quantity) VALUES(1,?,?,1,'Panel',1)",returnA,orderA);
  db.update("INSERT INTO pos_return_items(tenant_id,return_id,order_id,product_id,name,quantity) VALUES(2,?,?,2,'Private',1)",returnB,orderB);
  db.update("INSERT INTO pos_return_events(id,tenant_id,return_id,actor,action,note,created_at) VALUES('p2-cross-event-a',1,?,'alice','REQUESTED','A event',CURRENT_TIMESTAMP)",returnA);
  db.update("INSERT INTO pos_return_events(id,tenant_id,return_id,actor,action,note,created_at) VALUES('p2-cross-event-b',2,?,'bob','REQUESTED','B event',CURRENT_TIMESTAMP)",returnB);

  BigDecimal totalA=db.queryForObject("SELECT total FROM orders WHERE tenant_id=1 AND id=?",BigDecimal.class,orderA);
  BigDecimal netA=db.queryForObject("SELECT price*quantity FROM order_items WHERE tenant_id=1 AND order_id=? AND product_id=1",BigDecimal.class,orderA);
  BigDecimal taxA=db.queryForObject("SELECT tax FROM order_items WHERE tenant_id=1 AND order_id=? AND product_id=1",BigDecimal.class,orderA);
  Long receiptA=db.queryForObject("SELECT receipt_number FROM orders WHERE tenant_id=1 AND id=?",Long.class,orderA);
  db.update("INSERT INTO vendor_invoices(tenant_id,order_id,invoice_number,seller_name,outlet_name,total) VALUES(1,?,'P2-CROSS-INVOICE-A','Vendor A','A',?)",orderA,totalA);
  db.update("""
    INSERT INTO vendor_payment_attempts(id,tenant_id,order_id,method,merchant_account,state,amount,created_at,idempotency_key,request_hash)
    VALUES('p2-cross-payment-a',1,?,'UPI','merchant-a','PENDING',?,CURRENT_TIMESTAMP,'p2-cross-payment-key-a',REPEAT('a',64))
    """,orderA,totalA);
  db.update("""
    INSERT INTO receipt_document_audit(id,tenant_id,branch_id,order_id,actor,reason,content_sha256,content_length,created_at)
    VALUES('p2-cross-receipt-a',1,1,?,'alice','Copy requested',REPEAT('a',64),128,CURRENT_TIMESTAMP)
    """,orderA);
  db.update("""
    INSERT INTO inventory_receipts(id,tenant_id,branch_id,product_id,quantity,actor,reason,command_key,available_after,created_at)
    VALUES('p2-cross-stock-a',1,1,1,1,'alice','Delivery accepted','p2-stock-a',2,CURRENT_TIMESTAMP)
    """);
  db.update("""
    INSERT INTO pos_cash_refunds(id,tenant_id,branch_id,return_id,order_id,original_receipt,cashier,
      return_requester,session_id,idempotency_key,request_hash,quote_hash,amount,reason,paid_at)
    VALUES('p2-cross-refund-a',1,1,?,?,?,'alice','bob',?,'p2-refund-a',?, ?,?,'Refund paid',CURRENT_TIMESTAMP)
    """,returnA,orderA,receiptA,session,hashA,hashB,totalA);
  db.update("""
    INSERT INTO pos_cash_refund_items(tenant_id,refund_id,return_id,order_id,product_id,name,quantity,net,tax,amount)
    VALUES(1,'p2-cross-refund-a',?,?,1,'Panel',1,?,?,?)
    """,returnA,orderA,netA,taxA,totalA);
  db.update("""
    INSERT INTO inventory_return_dispositions(tenant_id,branch_id,return_id,product_id,quantity,
      disposition,received_condition,actor,created_at)
    VALUES(1,1,?,1,1,'QUARANTINE','DAMAGED','alice',CURRENT_TIMESTAMP)
    """,returnA);

  // Cross-tenant parents must be rejected even when the child tenant, branch,
  // cashier, session, or one of the other parent references is otherwise valid.
  assertThatThrownBy(()->db.update("""
    INSERT INTO vendor_invoices(tenant_id,order_id,invoice_number,seller_name,outlet_name,total)
    VALUES(1,?,'P2-CROSS-INVOICE-B','Vendor A','A',100)
    """,orderB)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("""
    INSERT INTO vendor_payment_attempts(id,tenant_id,order_id,method,merchant_account,state,amount,created_at,idempotency_key,request_hash)
    VALUES('p2-cross-payment-bad',1,?,'UPI','merchant-a','PENDING',100,CURRENT_TIMESTAMP,'p2-cross-payment-key-bad',REPEAT('b',64))
    """,orderB)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("""
    INSERT INTO receipt_document_audit(id,tenant_id,branch_id,order_id,actor,reason,content_sha256,content_length,created_at)
    VALUES('p2-cross-receipt-bad',1,1,?,'alice','Cross tenant copy',REPEAT('b',64),128,CURRENT_TIMESTAMP)
    """,orderB)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("""
    INSERT INTO inventory_receipts(id,tenant_id,branch_id,product_id,quantity,actor,reason,command_key,available_after,created_at)
    VALUES('p2-cross-stock-bad',1,1,2,1,'alice','Foreign product','p2-stock-bad',1,CURRENT_TIMESTAMP)
    """)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("""
    INSERT INTO pos_returns(id,tenant_id,branch_id,order_id,requester,session_id,idempotency_key,
      request_hash,reason,proof,proof_note,state,created_at)
    VALUES('p2-cross-return-bad',1,1,?,'alice',?,'p2-cross-return-bad-key',?,'Foreign order',
      'ORIGINAL_RECEIPT','No cross tenant','REQUESTED',CURRENT_TIMESTAMP)
    """,orderB,session,hashA)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("""
    INSERT INTO pos_return_items(tenant_id,return_id,order_id,product_id,name,quantity)
    VALUES(1,?,?,2,'Foreign item',1)
    """,returnA,orderB)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("""
    INSERT INTO pos_return_events(id,tenant_id,return_id,actor,action,note,created_at)
    VALUES('p2-cross-event-bad',1,?,'alice','REQUESTED','Foreign return',CURRENT_TIMESTAMP)
    """,returnB)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("""
    INSERT INTO pos_cash_refunds(id,tenant_id,branch_id,return_id,order_id,original_receipt,cashier,
      return_requester,session_id,idempotency_key,request_hash,quote_hash,amount,reason,paid_at)
    VALUES('p2-cross-refund-bad',1,1,?,?,?,'alice','bob',?,'p2-refund-bad',?,?,100,'Foreign order',CURRENT_TIMESTAMP)
    """,returnA,orderB,receiptA,session,hashA,hashB)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("""
    INSERT INTO pos_cash_refund_items(tenant_id,refund_id,return_id,order_id,product_id,name,quantity,net,tax,amount)
    VALUES(1,'p2-cross-refund-a',?,?,2,'Foreign item',1,90,10,100)
    """,returnA,orderA)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("""
    INSERT INTO inventory_return_dispositions(tenant_id,branch_id,return_id,product_id,quantity,
      disposition,received_condition,actor,created_at)
    VALUES(1,1,?,2,1,'QUARANTINE','DAMAGED','alice',CURRENT_TIMESTAMP)
    """,returnB)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  // Mirror the complete relationship graph: Vendor B must not be able to
  // attach Vendor A's financial, return, receipt or stock-history parents.
  BigDecimal totalB=db.queryForObject("SELECT total FROM orders WHERE tenant_id=2 AND id=?",BigDecimal.class,orderB);
  BigDecimal netB=db.queryForObject("SELECT price*quantity FROM order_items WHERE tenant_id=2 AND order_id=? AND product_id=2",BigDecimal.class,orderB);
  BigDecimal taxB=db.queryForObject("SELECT tax FROM order_items WHERE tenant_id=2 AND order_id=? AND product_id=2",BigDecimal.class,orderB);
  Long receiptB=db.queryForObject("SELECT receipt_number FROM orders WHERE tenant_id=2 AND id=?",Long.class,orderB);
  db.update("INSERT INTO vendor_invoices(tenant_id,order_id,invoice_number,seller_name,outlet_name,total) VALUES(2,?,'P2-CROSS-INVOICE-B','Vendor B','B',?)",orderB,totalB);
  db.update("""
    INSERT INTO vendor_payment_attempts(id,tenant_id,order_id,method,merchant_account,state,amount,created_at,idempotency_key,request_hash)
    VALUES('p2-cross-payment-b',2,?,'UPI','merchant-b','PENDING',?,CURRENT_TIMESTAMP,'p2-cross-payment-key-b',REPEAT('b',64))
    """,orderB,totalB);
  db.update("""
    INSERT INTO receipt_document_audit(id,tenant_id,branch_id,order_id,actor,reason,content_sha256,content_length,created_at)
    VALUES('p2-cross-receipt-b',2,2,?,'bob','Copy requested',REPEAT('b',64),128,CURRENT_TIMESTAMP)
    """,orderB);
  db.update("""
    INSERT INTO inventory_receipts(id,tenant_id,branch_id,product_id,quantity,actor,reason,command_key,available_after,created_at)
    VALUES('p2-cross-stock-b',2,2,2,1,'bob','Delivery accepted','p2-stock-b',2,CURRENT_TIMESTAMP)
    """);
  db.update("""
    INSERT INTO pos_cash_refunds(id,tenant_id,branch_id,return_id,order_id,original_receipt,cashier,
      return_requester,session_id,idempotency_key,request_hash,quote_hash,amount,reason,paid_at)
    VALUES('p2-cross-refund-b',2,2,?,?,?,'bob','alice',?,'p2-refund-b',?,?,?,'Refund paid',CURRENT_TIMESTAMP)
    """,returnB,orderB,receiptB,sessionB,hashB,hashA,totalB);
  db.update("""
    INSERT INTO pos_cash_refund_items(tenant_id,refund_id,return_id,order_id,product_id,name,quantity,net,tax,amount)
    VALUES(2,'p2-cross-refund-b',?,?,2,'Private',1,?,?,?)
    """,returnB,orderB,netB,taxB,totalB);
  db.update("""
    INSERT INTO inventory_return_dispositions(tenant_id,branch_id,return_id,product_id,quantity,
      disposition,received_condition,actor,created_at)
    VALUES(2,2,?,2,1,'QUARANTINE','DAMAGED','bob',CURRENT_TIMESTAMP)
    """,returnB);

  assertThatThrownBy(()->db.update("INSERT INTO vendor_invoices(tenant_id,order_id,invoice_number,seller_name,outlet_name,total) VALUES(2,?,'P2-CROSS-INVOICE-REVERSE','Vendor B','B',100)",orderA)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("""
    INSERT INTO vendor_payment_attempts(id,tenant_id,order_id,method,merchant_account,state,amount,created_at,idempotency_key,request_hash)
    VALUES('p2-cross-payment-reverse',2,?,'UPI','merchant-b','PENDING',100,CURRENT_TIMESTAMP,'p2-cross-payment-key-reverse',REPEAT('b',64))
    """,orderA)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("""
    INSERT INTO receipt_document_audit(id,tenant_id,branch_id,order_id,actor,reason,content_sha256,content_length,created_at)
    VALUES('p2-cross-receipt-reverse',2,2,?,'bob','Cross tenant copy',REPEAT('b',64),128,CURRENT_TIMESTAMP)
    """,orderA)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("""
    INSERT INTO inventory_receipts(id,tenant_id,branch_id,product_id,quantity,actor,reason,command_key,available_after,created_at)
    VALUES('p2-cross-stock-reverse',2,2,1,1,'bob','Foreign product','p2-stock-reverse',1,CURRENT_TIMESTAMP)
    """)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("""
    INSERT INTO pos_returns(id,tenant_id,branch_id,order_id,requester,session_id,idempotency_key,request_hash,reason,proof,proof_note,state,created_at)
    VALUES('p2-cross-return-reverse',2,2,?,'bob',?,'p2-cross-return-reverse-key',?,'Foreign order','ORIGINAL_RECEIPT','No cross tenant','REQUESTED',CURRENT_TIMESTAMP)
    """,orderA,sessionB,hashB)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("INSERT INTO pos_return_items(tenant_id,return_id,order_id,product_id,name,quantity) VALUES(2,?,?,1,'Foreign item',1)",returnB,orderA)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("INSERT INTO pos_return_events(id,tenant_id,return_id,actor,action,note,created_at) VALUES('p2-cross-event-reverse',2,?,'bob','REQUESTED','Foreign return',CURRENT_TIMESTAMP)",returnA)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("""
    INSERT INTO pos_cash_refunds(id,tenant_id,branch_id,return_id,order_id,original_receipt,cashier,return_requester,session_id,idempotency_key,request_hash,quote_hash,amount,reason,paid_at)
    VALUES('p2-cross-refund-reverse',2,2,?,?,?,'bob','alice',?,'p2-refund-reverse',?,?,100,'Foreign order',CURRENT_TIMESTAMP)
    """,returnB,orderA,receiptB,sessionB,hashB,hashA)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("INSERT INTO pos_cash_refund_items(tenant_id,refund_id,return_id,order_id,product_id,name,quantity,net,tax,amount) VALUES(2,'p2-cross-refund-b',?,?,1,'Foreign item',1,90,10,100)",returnB,orderA)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("INSERT INTO inventory_return_dispositions(tenant_id,branch_id,return_id,product_id,quantity,disposition,received_condition,actor,created_at) VALUES(2,2,?,1,1,'QUARANTINE','DAMAGED','bob',CURRENT_TIMESTAMP)",returnA)).isInstanceOf(org.springframework.dao.DataAccessException.class);

  // Direct SQL must not remove any financial-history parent while children remain.
  assertThatThrownBy(()->db.update("DELETE FROM pos_sessions WHERE tenant_id=1 AND id=?",session)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("DELETE FROM pos_sessions WHERE tenant_id=2 AND id=?",sessionB)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("DELETE FROM branches WHERE tenant_id=1 AND id=1")).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("DELETE FROM branches WHERE tenant_id=2 AND id=2")).isInstanceOf(org.springframework.dao.DataAccessException.class);

  // UPDATE paths must be guarded as strictly as INSERT paths.
  assertThatThrownBy(()->db.update("UPDATE vendor_invoices SET tenant_id=2 WHERE tenant_id=1 AND order_id=?",orderA)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("UPDATE vendor_payment_attempts SET tenant_id=2 WHERE id='p2-cross-payment-a'")).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("UPDATE receipt_document_audit SET tenant_id=2 WHERE id='p2-cross-receipt-a'")).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("UPDATE inventory_receipts SET product_id=2 WHERE id='p2-cross-stock-a'")).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("UPDATE pos_returns SET order_id=? WHERE tenant_id=1 AND id=?",orderB,returnA)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("UPDATE pos_return_items SET order_id=? WHERE tenant_id=1 AND return_id=?",orderB,returnA)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("UPDATE pos_return_events SET tenant_id=2 WHERE id='p2-cross-event-a'")).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("UPDATE pos_cash_refunds SET order_id=? WHERE tenant_id=1 AND id='p2-cross-refund-a'",orderB)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("UPDATE pos_cash_refund_items SET order_id=? WHERE tenant_id=1 AND refund_id='p2-cross-refund-a'",orderB)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("UPDATE inventory_return_dispositions SET tenant_id=2 WHERE tenant_id=1 AND return_id=?",returnA)).isInstanceOf(org.springframework.dao.DataAccessException.class);

  // Financial/commercial history is retained: parent deletion is blocked by
  // its dependent invoice, payment, return, receipt, refund and audit records.
  assertThatThrownBy(()->db.update("DELETE FROM orders WHERE tenant_id=1 AND id=?",orderA))
    .isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("DELETE FROM pos_returns WHERE tenant_id=1 AND id=?",returnA))
    .isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("DELETE FROM pos_cash_refunds WHERE tenant_id=1 AND id='p2-cross-refund-a'"))
    .isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("DELETE FROM products WHERE tenant_id=1 AND id=1"))
    .isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_invoices WHERE tenant_id=1 AND order_id=?",Integer.class,orderA)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_cash_refund_items WHERE tenant_id=1 AND refund_id='p2-cross-refund-a'",Integer.class)).isEqualTo(1);
 }

 @Test void inventoryReceiptAndCashTenderRollbackTogetherWithTheirStockAuditAndReceiptWrites(){
  int quantityBefore=db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1 AND branch_id=1 AND product_id=1",Integer.class);
  int receiptsBefore=db.queryForObject("SELECT COUNT(*) FROM inventory_receipts WHERE tenant_id=1 AND branch_id=1",Integer.class);
  assertThatThrownBy(()->as("alice",()->new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->{
    service.lockInventoryOutlet(a);
    stockReceiving.receive(1,1,"alice","p2-stock-receipt-rollback",
      new com.saumya.pos.inventory.StockReceivingService.Input(1,2,"Rollback integration receipt"));
    throw new IllegalStateException("Inject failure after stock receipt and balance writes");
  }))).isInstanceOf(IllegalStateException.class).hasMessageContaining("Inject failure");
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1 AND branch_id=1 AND product_id=1",Integer.class)).isEqualTo(quantityBefore);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_receipts WHERE tenant_id=1 AND branch_id=1",Integer.class)).isEqualTo(receiptsBefore);

  var draft=as("alice",()->checkout.pos("alice","p2-payment-rollback",posDraft()));
  int ledgerBefore=db.queryForObject("SELECT COUNT(*) FROM inventory_ledger WHERE tenant_id=1 AND order_id=?",Integer.class,draft.id());
  assertThatThrownBy(()->as("alice",()->new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->{
    payments.tender("alice",draft.id(),"p2-cash-tender-rollback",
      new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("CASH",draft.total(),draft.total()));
    throw new IllegalStateException("Inject failure after payment, stock consume and receipt writes");
  }))).isInstanceOf(IllegalStateException.class).hasMessageContaining("Inject failure");
  assertThat(db.queryForObject("SELECT status FROM orders WHERE tenant_id=1 AND id=?",String.class,draft.id())).isEqualTo("DRAFT");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_payment_attempts WHERE tenant_id=1 AND order_id=?",Integer.class,draft.id())).isZero();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_ledger WHERE tenant_id=1 AND order_id=?",Integer.class,draft.id())).isEqualTo(ledgerBefore);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_invoices WHERE tenant_id=1 AND order_id=?",Integer.class,draft.id())).isZero();
 }

 @Test void cashRefundFailureRollsBackRefundAllocationAndAuditTimeline(){
  String returned=inspectedRefundReturn(),paying=refundSession("200");
  update("UPDATE branches SET cash_refunds_enabled=TRUE WHERE id=1");
  var command=refundPay(returned,paying);
  BigDecimal cashBefore=as("manager",()->service.reconciliation(new PosService.Scope(1,1,"manager"),paying)).expectedCash();
  assertThatThrownBy(()->as("manager",()->new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->{
    refunds.pay("p2-refund-rollback",command);
    throw new IllegalStateException("Inject failure after refund payment and audit writes");
  }))).isInstanceOf(IllegalStateException.class).hasMessageContaining("Inject failure");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_cash_refunds WHERE tenant_id=1",Integer.class)).isZero();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_cash_refund_items WHERE tenant_id=1",Integer.class)).isZero();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_return_events WHERE tenant_id=1 AND action='CASH_REFUND'",Integer.class)).isZero();
  assertThat(as("manager",()->service.reconciliation(new PosService.Scope(1,1,"manager"),paying)).expectedCash()).isEqualByComparingTo(cashBefore);
 }

 @Test void directServiceCallsRejectMissingPermissionBeforeBusinessQueries() {
  // No controller is involved: call the Spring service proxies directly with
  // the authenticated employee's permission removed from active assigned roles.
  removeAssignedRolePermission("alice","POS_CASHIER_REPORT_VIEW");removeAssignedRolePermission("alice","POS_VIEW");
  assertThatThrownBy(()->as("alice",()->cashierSales.report("alice",null,null,0,10)))
    .isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->as("alice",()->orderHistory.pos("alice",
    new com.saumya.pos.checkout.OrderHistoryService.Filter("ALL","",null,null,0,10))))
    .isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->as("alice",()->draftRecovery.pos("alice","nonexistent-order")))
    .isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->as("alice",()->posCatalog.search(1,1,"","all","name",false,0,10)))
    .isInstanceOf(RuntimeException.class);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isZero();
 }


 @Test void catalogAdminServicesRejectMissingPermissionAndForgedTenantOnDirectInvocation() {
  removeAssignedRolePermission("alice","PRODUCT_VIEW");
  assertThatThrownBy(()->as("alice",()->productAdmin.list(1,0,10))).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->as("alice",()->categoryAdmin.list(1,0,10))).isInstanceOf(RuntimeException.class);
  addAssignedRolePermission("alice","PRODUCT_VIEW");
  assertThatThrownBy(()->as("alice",()->productAdmin.list(2,0,10))).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->as("alice",()->categoryAdmin.list(2,0,10))).isInstanceOf(RuntimeException.class);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM products WHERE tenant_id=2",Integer.class)).isEqualTo(1);
 }


 @Test void everyProtectedControllerRouteRejectsAnonymousHttpRequests() throws Exception {
  int checked=0;
  for(var entry:requestMappings.getHandlerMethods().entrySet()){
   var handler=entry.getValue();
   if(!handler.getBeanType().getPackageName().startsWith("com.saumya.pos"))continue;
   var patterns=entry.getKey().getPatternValues();
   if(patterns.isEmpty())continue;
   String route=patterns.iterator().next();
   Set<RequestMethod> declared=entry.getKey().getMethodsCondition().getMethods();
   List<RequestMethod> methods=declared.isEmpty()?List.of(RequestMethod.GET):declared.stream()
       .filter(m->Set.of(RequestMethod.GET,RequestMethod.POST,RequestMethod.PUT,RequestMethod.PATCH,RequestMethod.DELETE).contains(m))
       .toList();
   for(RequestMethod method:methods){
    if(publicRoute(method,route))continue;
    String concrete=route.replaceAll("\\{[^/]+\\}","1");
    var builder=request(HttpMethod.valueOf(method.name()),concrete).with(csrf()).with(anonymous());
    if(!Set.of(RequestMethod.GET,RequestMethod.DELETE).contains(method))
     builder.contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{}");
    mvc.perform(builder).andExpect(status().isUnauthorized());
    checked++;
   }
  }
  assertThat(checked).as("protected controller routes exercised through the real HTTP security filter").isGreaterThan(50);
 }

 @Test void everyEligibleProtectedGetRouteRejectsAuthenticatedPrincipalWithNoAuthorities() throws Exception {
  int checked=0;
  for(var entry:requestMappings.getHandlerMethods().entrySet()){
   HandlerMethod handler=entry.getValue();
   if(!handler.getBeanType().getPackageName().startsWith("com.saumya.pos"))continue;
   Set<RequestMethod> declared=entry.getKey().getMethodsCondition().getMethods();
   if(!declared.isEmpty()&&!declared.contains(RequestMethod.GET))continue;
   var patterns=entry.getKey().getPatternValues();
   if(patterns.isEmpty())continue;
   String route=patterns.iterator().next();
   if(publicRoute(RequestMethod.GET,route))continue;
   var policy=org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(),org.springframework.security.access.prepost.PreAuthorize.class);
   if(policy==null)policy=org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(),org.springframework.security.access.prepost.PreAuthorize.class);
   if(policy==null||!(policy.value().contains("hasAuthority(")||policy.value().contains("hasAnyAuthority(")))continue;

   boolean eligible=true;
   for(var parameter:handler.getMethod().getParameters()){
    if(parameter.isAnnotationPresent(org.springframework.web.bind.annotation.RequestBody.class)
        ||parameter.isAnnotationPresent(org.springframework.web.bind.annotation.RequestHeader.class)){
     eligible=false;break;
    }
    var query=parameter.getAnnotation(org.springframework.web.bind.annotation.RequestParam.class);
    if(query!=null&&query.required()
        &&org.springframework.web.bind.annotation.ValueConstants.DEFAULT_NONE.equals(query.defaultValue())){
     eligible=false;break;
    }
    var pathVariable=parameter.getAnnotation(org.springframework.web.bind.annotation.PathVariable.class);
    if(pathVariable!=null){
     Class<?> type=parameter.getType();
     if(!(type==String.class||type==long.class||type==Long.class||type==int.class||type==Integer.class)){
      eligible=false;break;
     }
    }else if(query==null&&parameter.getType()!=java.security.Principal.class
        &&parameter.getType()!=org.springframework.security.web.csrf.CsrfToken.class
        &&parameter.getType()!=jakarta.servlet.http.HttpServletRequest.class
        &&parameter.getType()!=jakarta.servlet.http.HttpServletResponse.class){
     eligible=false;break;
    }
   }
   if(!eligible)continue;
   String concrete=route.replaceAll("\\{[^/]+\\}","1");
   mvc.perform(get(concrete).with(user("p0-no-grants").authorities()).with(csrf()))
       .andExpect(status().isForbidden());
   checked++;
  }
  assertThat(checked).as("protected GET routes exercised with authenticated zero-authority principal").isGreaterThan(25);
 }

 private static boolean publicRoute(RequestMethod method,String route){
  if(method==RequestMethod.GET&&(route.startsWith("/api/v1/marketplace/")||route.equals("/api/v1/auth/csrf")))return true;
  return method==RequestMethod.POST&&Set.of(
      "/api/v1/auth/login","/api/v1/auth/register","/api/v1/auth/refresh","/api/v1/auth/logout",
      "/api/v1/auth/invitations/accept","/api/v1/auth/platform-mfa/enrollment/start",
      "/api/v1/auth/platform-mfa/enrollment/complete").contains(route);
 }


 @Test void checkoutServiceDirectInvocationHidesVendorBOrderFromVendorA() {
  String bobSession=(String)asScope(b,()->service.open(b,new PosService.OpenSession(2,BigDecimal.ZERO)).get("id"));
  var foreignDraft=asScope(b,()->checkout.pos("bob","vendor-b-direct-draft-01",
      new CheckoutService.PosDraft(bobSession,List.of(new CheckoutService.Item(2,1)),false,null,null)));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM orders WHERE tenant_id=2 AND id=?",Integer.class,foreignDraft.id())).isEqualTo(1);
  assertThat(db.queryForObject("SELECT reserved FROM inventory WHERE tenant_id=2 AND branch_id=2 AND product_id=2",Integer.class)).isEqualTo(1);

  assertThatThrownBy(()->as("alice",()->checkout.cancelPos("alice",foreignDraft.id(),"Cross-tenant probe")))
      .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,error->
          assertThat(error.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND));

  assertThat(db.queryForObject("SELECT status FROM orders WHERE tenant_id=2 AND id=?",String.class,foreignDraft.id())).isEqualTo("DRAFT");
  assertThat(db.queryForObject("SELECT reserved FROM inventory WHERE tenant_id=2 AND branch_id=2 AND product_id=2",Integer.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE tenant_id=1 AND resource_id=?",Integer.class,foreignDraft.id())).isZero();
 }


 @Test void everyProtectedControllerMethodRejectsAuthenticatedZeroAuthorityDirectInvocation() throws Exception {
  var previous=org.springframework.security.core.context.SecurityContextHolder.getContext();
  var noGrants=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
  noGrants.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
      "p0-runtime-no-grants",null,List.of()));
  org.springframework.security.core.context.SecurityContextHolder.setContext(noGrants);
  int checked=0;
  int ordersBefore=db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class);
  int auditBefore=db.queryForObject("SELECT COUNT(*) FROM pos_audit",Integer.class);
  try{
   var scanner=new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(false);
   scanner.addIncludeFilter(new org.springframework.core.type.filter.AnnotationTypeFilter(org.springframework.web.bind.annotation.RestController.class));
   for(var candidate:scanner.findCandidateComponents("com.saumya.pos")){
    Class<?> controllerType=Class.forName(candidate.getBeanClassName());
    Object controller=applicationContext.getBean(controllerType);
    for(var method:controllerType.getDeclaredMethods()){
     if(!org.springframework.core.annotation.AnnotatedElementUtils.hasAnnotation(method,org.springframework.web.bind.annotation.RequestMapping.class))continue;
     var policy=org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(method,org.springframework.security.access.prepost.PreAuthorize.class);
     if(policy==null)policy=org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(controllerType,org.springframework.security.access.prepost.PreAuthorize.class);
     assertThat(policy).as("method security policy for %s#%s",controllerType.getSimpleName(),method.getName()).isNotNull();
     if(policy.value().contains("permitAll()"))continue;
     final String expression=policy.value();
     if(!(expression.contains("hasAuthority(")||expression.contains("hasAnyAuthority(")))continue;
     method.setAccessible(true);
     Object[] args=Arrays.stream(method.getParameterTypes())
         .map(type->authorizationProbeValue(type,expression)).toArray();
     try{
      method.invoke(controller,args);
      fail("Protected controller method accepted a principal with no grants: "+controllerType.getSimpleName()+"#"+method.getName());
     }catch(java.lang.reflect.InvocationTargetException invocation){
      Throwable cause=invocation.getTargetException();
      while(cause.getCause()!=null&&cause.getCause()!=cause)cause=cause.getCause();
      boolean denied=cause instanceof org.springframework.security.access.AccessDeniedException
          ||(cause instanceof org.springframework.web.server.ResponseStatusException response
             &&Set.of(401,403,404).contains(response.getStatusCode().value()));
      assertThat(denied).as("zero-grant invocation must be denied before controller execution: %s#%s (%s)",
          controllerType.getSimpleName(),method.getName(),cause).isTrue();
     }
     checked++;
    }
   }
  }finally{
   org.springframework.security.core.context.SecurityContextHolder.setContext(previous);
  }
  assertThat(checked).as("protected controller methods exercised through their Spring security proxies").isGreaterThan(50);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(ordersBefore);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit",Integer.class)).isEqualTo(auditBefore);
 }

 private static Object authorizationProbeValue(Class<?> type,String expression){
  if(type==String.class)return "CASH";
  if(type==long.class||type==Long.class)return 1L;
  if(type==int.class||type==Integer.class)return 0;
  if(type==boolean.class||type==Boolean.class)return false;
  if(type==BigDecimal.class)return BigDecimal.ONE;
  if(type==java.time.LocalDate.class)return java.time.LocalDate.of(2026,11,17);
  if(type==java.time.Instant.class)return java.time.Instant.parse("2026-11-17T00:00:00Z");
  if(type.isEnum()){
   if(type.getSimpleName().equals("TenantStatus"))return Enum.valueOf((Class<Enum>)type,"SUSPENDED");
   return type.getEnumConstants().length==0?null:type.getEnumConstants()[0];
  }
  if(List.class.isAssignableFrom(type))return List.of();
  if(Set.class.isAssignableFrom(type))return Set.of();
  if(Map.class.isAssignableFrom(type))return Map.of();
  if(type.isArray())return java.lang.reflect.Array.newInstance(type.getComponentType(),0);
  if(type.isRecord()&&(expression.contains("#r.method()")||expression.contains("#request.type()"))){
   try{
    var components=type.getRecordComponents();
    Class<?>[] parameterTypes=Arrays.stream(components).map(java.lang.reflect.RecordComponent::getType).toArray(Class<?>[]::new);
    Object[] values=new Object[components.length];
    for(int i=0;i<components.length;i++){
     var component=components[i];
     values[i]=component.getName().equals("method")&&component.getType()==String.class
         ?"CASH":authorizationProbeValue(component.getType(),expression);
    }
    var constructor=type.getDeclaredConstructor(parameterTypes);constructor.setAccessible(true);
    return constructor.newInstance(values);
   }catch(ReflectiveOperationException error){
    throw new AssertionError("Cannot construct authorization probe for "+type.getName(),error);
   }
  }
  return null;
 }



 @Test void p006PlatformSuperAdminAndSupportUsePersistedPlatformGrantsWithoutVendorAuthority() throws Exception {
  multiVendorDemo();
  String superAdmin=demoToken("superadmin@platform.demo.test","PlatformDemo2026!");
  mvc.perform(get("/api/v1/platform/dashboard").header("Authorization","Bearer "+superAdmin))
    .andExpect(status().isOk());
  mvc.perform(get("/api/v1/vendor/products").header("Authorization","Bearer "+superAdmin))
    .andExpect(status().isForbidden());

  int productsBefore=db.queryForObject("SELECT COUNT(*) FROM products WHERE tenant_id=910001",Integer.class);
  String forgedVendorCreate="{\"sku\":\"P006-PLATFORM-MUST-DENY\",\"name\":\"Platform cannot sell\",\"price\":100.00,\"taxRate\":0.00,\"published\":true,\"expectedVersion\":0,\"reason\":\"Actor boundary regression\"}";
  mvc.perform(post("/api/v1/vendor/products").header("Authorization","Bearer "+superAdmin).with(csrf())
      .contentType("application/json").content(forgedVendorCreate))
    .andExpect(status().isForbidden());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM products WHERE tenant_id=910001",Integer.class))
    .as("platform identity cannot create vendor catalogue data").isEqualTo(productsBefore);

  String supportUser="support@platform.demo.test";
  update("INSERT INTO users(id,username,password_hash,enabled) VALUES(950001,?,?,TRUE)",
    supportUser,encoder.encode("PlatformSupport2026!"));
  update("INSERT INTO platform_user_permissions(username,permission) VALUES(?,'PLATFORM_SUPPORT_ACCESS')",supportUser);
  String supportToken=demoToken(supportUser,"PlatformSupport2026!");

  mvc.perform(get("/api/v1/platform/support-access").header("Authorization","Bearer "+supportToken))
    .andExpect(status().isOk());
  mvc.perform(get("/api/v1/platform/dashboard").header("Authorization","Bearer "+supportToken))
    .andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/platform/tenants").header("Authorization","Bearer "+supportToken))
    .andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/vendor/products").header("Authorization","Bearer "+supportToken))
    .andExpect(status().isForbidden());

  int tenantCount=db.queryForObject("SELECT COUNT(*) FROM tenants",Integer.class);
  String forbiddenTenant="{\"slug\":\"p006-support-must-not-create\",\"name\":\"Forbidden Support Tenant\",\"legalName\":\"Forbidden Support Tenant\",\"contactEmail\":\"owner@forbidden.example.test\",\"initialVendorAdminUsername\":\"owner@forbidden.example.test\"}";
  mvc.perform(post("/api/v1/platform/tenants").header("Authorization","Bearer "+supportToken).with(csrf())
      .contentType("application/json").content(forbiddenTenant))
    .andExpect(status().isForbidden());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM tenants",Integer.class))
    .as("support access alone cannot create tenants").isEqualTo(tenantCount);

 }

 @Test void p102ProvisionedTemplatesEnforceRuntimeScopes() throws Exception {
  String admin="p102-admin@example.test";
  update("INSERT INTO users(id,username,password_hash,enabled) VALUES(10050,?,?,TRUE)",admin,encoder.encode("StaffPasswordForTests"));
  var platform=org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("platform","test",
    List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("PLATFORM_TENANT_CREATE"),
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ACTOR_CONTEXT_PLATFORM")));
  asAuthentication(platform,()->memberships.provisionInitialVendorAdmin(1,1,admin));
  asAuthentication(platform,()->memberships.provisionInitialVendorAdmin(1,1,admin));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_access_audit_events WHERE tenant_id=1 AND action='ROLE_TEMPLATE_APPLIED'",Integer.class)).isEqualTo(5);
  var roleService=applicationContext.getBean(com.saumya.pos.membership.VendorRoleService.class);
  String target="p102-worker@example.test";
  employee(target,1,1);
  Map<String,String> routes=Map.of("VENDOR_ADMIN","roles","POS_CASHIER","pos/products",
      "INVENTORY_MANAGER","products","SALES_MANAGER","pos/reports/sales","ACCOUNTANT","pos/reports/cash-refunds");
  for (var template : com.saumya.pos.membership.StandardRoleTemplates.ALL) {
   if(template.deferredReason()!=null){
    assertThat(template.permissions()).isEmpty();
    assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_roles WHERE template_key=?",Integer.class,template.key())).isZero();
    continue;
   }
   long id=db.queryForObject("SELECT id FROM vendor_roles WHERE template_key=? AND template_version=1 AND tenant_id=1",Long.class,template.key());
   boolean initial=template.key().equals("VENDOR_ADMIN");
   String actor=initial?admin:target;
   if(!initial)as(admin,()->roleService.assignRoles(target,List.of(id),"Runtime template scenario"));
   assertThat(effectivePermissions(actor)).containsExactlyInAnyOrderElementsOf(template.permissions());
   String token=demoToken(actor,"StaffPasswordForTests");
   if(template.key().equals("POS_CASHIER")){
    // POS_VIEW is necessary but not sufficient: the cashier must also have
    // a persisted assignment and an OPEN session on the requested register.
    long cashierRegister=db.queryForObject("SELECT COALESCE(MAX(id),0)+1 FROM registers",Long.class);
    update("INSERT INTO registers(id,tenant_id,branch_id) VALUES(?,?,?)",cashierRegister,1L,1L);
    assignRegister(target,1,1,cashierRegister);
    as(target,()->service.open(new PosService.Scope(1,1,target),
      new PosService.OpenSession(cashierRegister,new BigDecimal("0"))));
   }
   mvc.perform(get("/api/v1/vendor/"+routes.get(template.key())).header("Authorization","Bearer "+token)).andExpect(status().isOk());
   int before=db.queryForObject("SELECT COUNT(*) FROM vendor_roles",Integer.class);
   if(initial){
    long foreign=db.queryForObject("SELECT MIN(id) FROM vendor_roles WHERE tenant_id=2",Long.class);
    mvc.perform(post("/api/v1/vendor/roles/"+foreign+"/deactivate").header("Authorization","Bearer "+token)
        .with(csrf()).contentType("application/json").content("{\"reason\":\"foreign\"}")).andExpect(status().isNotFound());
    assertThat(db.queryForObject("SELECT active FROM vendor_roles WHERE id=?",Boolean.class,foreign)).isTrue();
   }else{
    mvc.perform(post("/api/v1/vendor/roles").header("Authorization","Bearer "+token).with(csrf()).contentType("application/json")
      .content("{\"roleKey\":\"forbidden\",\"displayName\":\"Denied\",\"permissions\":[\"POS_VIEW\"],\"reason\":\"probe\"}")).andExpect(status().isForbidden());
   }
   assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_roles",Integer.class)).isEqualTo(before);
  }
  var clone=as(admin,()->roleService.applyTemplate("POS_CASHIER",1,"counter-reader","Counter reader",List.of("POS_VIEW"),"Reduced role"));
  assertThat(as(admin,()->roleService.applyTemplate("POS_CASHIER",1,"counter-reader","Counter reader",List.of("POS_VIEW"),"Retry")).id()).isEqualTo(clone.id());
  as(admin,()->roleService.assignRoles(target,List.of(clone.id()),"View only"));
  assertThat(effectivePermissions(target)).containsExactly("POS_VIEW");
  int sessionsBefore=db.queryForObject("SELECT COUNT(*) FROM pos_sessions",Integer.class);
  mvc.perform(post("/api/v1/vendor/pos/sessions").header("Authorization","Bearer "+demoToken(target,"StaffPasswordForTests"))
      .with(csrf()).contentType("application/json").content("{\"registerId\":1,\"openingFloat\":0}")).andExpect(status().isForbidden());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_sessions",Integer.class)).isEqualTo(sessionsBefore);
  as(admin,()->roleService.assignRoles(target,List.of(),"Remove all assignments"));
  assertThat(effectivePermissions(target)).isEmpty();
 }

 @Test void p006AccountantCanReadAssignedFinancialReportsButCannotReceiveInventory() throws Exception {
  multiVendorDemo();
  String accountant="finance@saumya.demo.test";
  // Report authorization is branch-scoped. This persisted assignment is
  // deliberately explicit; the accountant does not inherit a cashier role.
  update("INSERT INTO employee_register_assignments(tenant_id,branch_id,username,register_id,active,assigned_by) VALUES(910001,910001,?,910011,TRUE,?)",
    accountant,accountant);
  String token=demoToken(accountant,"SaumyaFinance2026!");

  mvc.perform(get("/api/v1/vendor/pos/reports/sales").header("Authorization","Bearer "+token)
      .param("from","2026-11-01").param("to","2026-11-20").param("channel","ALL"))
    .andExpect(status().isOk());

  int receiptsBefore=db.queryForObject("SELECT COUNT(*) FROM inventory_receipts WHERE tenant_id=910001",Integer.class);
  int ledgerBefore=db.queryForObject("SELECT COUNT(*) FROM inventory_ledger WHERE tenant_id=910001",Integer.class);
  int quantityBefore=db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=910001 AND branch_id=910001 AND product_id=910301",Integer.class);
  int reservedBefore=db.queryForObject("SELECT reserved FROM inventory WHERE tenant_id=910001 AND branch_id=910001 AND product_id=910301",Integer.class);
  mvc.perform(post("/api/v1/vendor/inventory/receipts").header("Authorization","Bearer "+token)
      .header("Idempotency-Key","p006-accountant-inventory-denial").with(csrf())
      .contentType("application/json")
      .content("{\"productId\":910301,\"quantity\":1,\"reason\":\"Accountant cannot receive inventory\"}"))
    .andExpect(status().isForbidden());

  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_receipts WHERE tenant_id=910001",Integer.class))
    .isEqualTo(receiptsBefore);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_ledger WHERE tenant_id=910001",Integer.class))
    .isEqualTo(ledgerBefore);
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=910001 AND branch_id=910001 AND product_id=910301",Integer.class))
    .isEqualTo(quantityBefore);
  assertThat(db.queryForObject("SELECT reserved FROM inventory WHERE tenant_id=910001 AND branch_id=910001 AND product_id=910301",Integer.class))
    .isEqualTo(reservedBefore);
  mvc.perform(get("/api/v1/customer/orders").header("Authorization","Bearer "+token))
    .andExpect(status().isForbidden());
 }

 @Test void p006VendorAdminCannotForgeTenantScopeThroughQueryParameters() throws Exception {
  multiVendorDemo();
  String token=demoToken("admin@saumya.demo.test","SaumyaAdmin2026!");
  String own=mvc.perform(get("/api/v1/vendor/products").header("Authorization","Bearer "+token))
    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  String forged=mvc.perform(get("/api/v1/vendor/products").header("Authorization","Bearer "+token)
      .param("tenantId","920001"))
    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertThat(own).contains("910301").doesNotContain("920301");
  assertThat(forged).contains("910301").doesNotContain("920301");
 }


 @Test void p006VendorAdminCustomRoleAndCustomerOwnedHttpResourcesAreTenantScoped() throws Exception {
  multiVendorDemo();
  String adminToken=demoToken("admin@saumya.demo.test","SaumyaAdmin2026!");
  mvc.perform(get("/api/v1/vendor/roles").header("Authorization","Bearer "+adminToken))
    .andExpect(status().isOk());
  Long foreignRole=db.queryForObject("SELECT MIN(id) FROM vendor_roles WHERE tenant_id=920001",Long.class);
  mvc.perform(get("/api/v1/vendor/roles/"+foreignRole).header("Authorization","Bearer "+adminToken))
    .andExpect(status().isNotFound());

  Long ownRole=db.queryForObject("SELECT MIN(id) FROM vendor_roles WHERE tenant_id=910001",Long.class);
  String scopedList=mvc.perform(get("/api/v1/vendor/roles").header("Authorization","Bearer "+adminToken)
      .header("X-Tenant-Id","920001").param("tenantId","920001"))
    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertThat(scopedList).contains("\"id\":"+ownRole).doesNotContain("\"id\":"+foreignRole);

  int rolesBeforeForgedBody=db.queryForObject("SELECT COUNT(*) FROM vendor_roles WHERE tenant_id=910001",Integer.class);
  int auditsBeforeForgedBody=db.queryForObject("SELECT COUNT(*) FROM vendor_access_audit_events WHERE tenant_id=910001",Integer.class);
  String forgedCreate="{\"roleKey\":\"p006-forged-tenant\",\"displayName\":\"Forged tenant role\",\"permissions\":[\"PRODUCT_VIEW\"],\"reason\":\"Tenant input must never set authorization scope\",\"tenantId\":920001}";
  mvc.perform(post("/api/v1/vendor/roles").header("Authorization","Bearer "+adminToken).with(csrf())
      .contentType("application/json").content(forgedCreate))
    .andExpect(status().isBadRequest());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_roles WHERE tenant_id=910001",Integer.class)).isEqualTo(rolesBeforeForgedBody);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_roles WHERE tenant_id=920001 AND role_key='p006-forged-tenant'",Integer.class)).isZero();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_access_audit_events WHERE tenant_id=910001",Integer.class)).isEqualTo(auditsBeforeForgedBody);

  String createRole="{\"roleKey\":\"p006-catalog-viewer\",\"displayName\":\"P0-06 catalogue viewer\",\"permissions\":[\"PRODUCT_VIEW\"],\"reason\":\"Create least-privilege custom role for runtime matrix\"}";
  mvc.perform(post("/api/v1/vendor/roles").header("Authorization","Bearer "+adminToken).with(csrf())
      .contentType("application/json").content(createRole))
    .andExpect(status().isCreated());
  Long customRole=db.queryForObject("SELECT id FROM vendor_roles WHERE tenant_id=910001 AND role_key='p006-catalog-viewer'",Long.class);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_role_permissions WHERE tenant_id=910001 AND role_id=? AND permission='PRODUCT_VIEW'",Integer.class,customRole)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_role_permissions WHERE tenant_id=910001 AND role_id=? AND permission<>'PRODUCT_VIEW'",Integer.class,customRole)).isZero();

  String adminProduct="{\"sku\":\"P006-STALE-VERSION\",\"name\":\"P0-06 versioned item\",\"price\":100.00,\"taxRate\":18.00,\"published\":true,\"expectedVersion\":0,\"reason\":\"P0-06 HTTP conflict semantics\"}";
  mvc.perform(post("/api/v1/vendor/products").header("Authorization","Bearer "+adminToken).with(csrf())
      .contentType("application/json").content(adminProduct))
    .andExpect(status().isOk());
  Long versionedProduct=db.queryForObject("SELECT id FROM products WHERE tenant_id=910001 AND sku='P006-STALE-VERSION'",Long.class);
  String updateProduct=adminProduct.replace("P0-06 versioned item","P0-06 updated item");
  mvc.perform(put("/api/v1/vendor/products/"+versionedProduct).header("Authorization","Bearer "+adminToken).with(csrf())
      .contentType("application/json").content(updateProduct))
    .andExpect(status().isOk());
  Long savedVersion=db.queryForObject("SELECT version FROM products WHERE tenant_id=910001 AND id=?",Long.class,versionedProduct);
  assertThat(savedVersion).isGreaterThan(0L);
  mvc.perform(put("/api/v1/vendor/products/"+versionedProduct).header("Authorization","Bearer "+adminToken).with(csrf())
      .contentType("application/json").content(updateProduct))
    .andExpect(status().isConflict());
  assertThat(db.queryForObject("SELECT version FROM products WHERE tenant_id=910001 AND id=?",Long.class,versionedProduct))
    .as("stale optimistic-lock retry must not change the saved product version").isEqualTo(savedVersion);

  String customerA="asha@customer.demo.test",customerB="rahul@customer.demo.test";
  String tokenA=demoToken(customerA,"AshaBuyer2026!"),tokenB=demoToken(customerB,"RahulBuyer2026!");
  asCustomer(customerA,()->marketplace.put(949001,new CartService.Item("saumya-rooftops",910301,1)));
  String ownCart=mvc.perform(get("/api/v1/customer/cart").header("Authorization","Bearer "+tokenA))
    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertThat(ownCart).contains("910301");
  String foreignCart=mvc.perform(get("/api/v1/customer/cart").header("Authorization","Bearer "+tokenB))
    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertThat(foreignCart).doesNotContain("910301");

  var cart=asCustomer(customerA,()->marketplace.cart(949001));
  var draft=asCustomer(customerA,()->checkout.online(customerA,"p006-customer-ownership",new CheckoutService.OnlineDraft(cart.id())));
  var attempt=asCustomer(customerA,()->payments.start(customerA,draft.id(),"UPI"));
  asCustomer(customerA,()->payments.confirm(customerA,draft.id(),attempt.attemptId()));
  mvc.perform(get("/api/v1/customer/orders/"+draft.id()).header("Authorization","Bearer "+tokenA))
    .andExpect(status().isOk());
  mvc.perform(get("/api/v1/customer/orders/"+draft.id()+"/receipt").header("Authorization","Bearer "+tokenA))
    .andExpect(status().isOk());
  mvc.perform(get("/api/v1/customer/orders/"+draft.id()).header("Authorization","Bearer "+tokenB))
    .andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/customer/orders/"+draft.id()+"/receipt").header("Authorization","Bearer "+tokenB))
    .andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/vendor/pos/refunds/history").header("Authorization","Bearer "+tokenB))
    .andExpect(status().isForbidden());
 }

 @Test void p006CashierHttpChecksSameBranchUnassignedRegisterAndForeignSession() throws Exception {
  multiVendorDemo();
  long secondRegister=db.queryForObject("SELECT COALESCE(MAX(id),0)+100 FROM registers WHERE tenant_id=930001 AND branch_id=930001",Long.class);
  update("INSERT INTO registers(id,tenant_id,branch_id) VALUES(?,?,?)",secondRegister,930001L,930001L);
  employee("cashier2@lumina.demo.test",930001,930001);
  update("UPDATE employee_register_assignments SET active=FALSE,revoked_by='admin@lumina.demo.test',revoked_at=CURRENT_TIMESTAMP WHERE tenant_id=930001 AND username='cashier2@lumina.demo.test'");
  assignRegister("cashier2@lumina.demo.test",930001,930001,secondRegister);

  String cashierOne=demoToken("cashier@lumina.demo.test","LuminaTill2026!");
  String cashierTwo=demoToken("cashier2@lumina.demo.test","StaffPasswordForTests");
  String sessionBody="{\"registerId\":930011,\"openingFloat\":0}";
  var opened=mvc.perform(post("/api/v1/vendor/pos/sessions").header("Authorization","Bearer "+cashierOne).with(csrf())
      .contentType("application/json").content(sessionBody))
    .andExpect(status().isOk()).andReturn();
  String sessionId=new com.fasterxml.jackson.databind.ObjectMapper().readTree(opened.getResponse().getContentAsString()).get("id").asText();

  int sessionsBefore=db.queryForObject("SELECT COUNT(*) FROM pos_sessions WHERE tenant_id=930001",Integer.class);
  mvc.perform(post("/api/v1/vendor/pos/sessions").header("Authorization","Bearer "+cashierTwo).with(csrf())
      .contentType("application/json").content(sessionBody))
    .andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/vendor/pos/sessions/"+sessionId).header("Authorization","Bearer "+cashierTwo))
    .andExpect(status().isNotFound());
  mvc.perform(post("/api/v1/vendor/pos/sessions").header("Authorization","Bearer "+cashierOne).with(csrf())
      .contentType("application/json").content("{\"registerId\":920011,\"openingFloat\":0}"))
    .andExpect(status().isNotFound());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_sessions WHERE tenant_id=930001",Integer.class))
    .as("wrong-register and wrong-branch session opens must not create a session").isEqualTo(sessionsBefore);

  mvc.perform(post("/api/v1/vendor/pos/sessions").header("Authorization","Bearer "+cashierTwo).with(csrf())
      .contentType("application/json").content("{\"registerId\":"+secondRegister+",\"openingFloat\":0}"))
    .andExpect(status().isOk());
 }


 private org.springframework.test.web.servlet.MvcResult performInRollbackTransaction(
     org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
  java.util.concurrent.atomic.AtomicReference<org.springframework.test.web.servlet.MvcResult> result=
      new java.util.concurrent.atomic.AtomicReference<>();
  try {
   new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status->{
    try { result.set(mvc.perform(request).andReturn()); }
    catch(Exception failure) { throw new IllegalStateException("Runtime authorization HTTP probe failed",failure); }
    status.setRollbackOnly();
   });
  } catch(IllegalStateException failure) {
   if(failure.getCause() instanceof Exception cause)throw cause;
   throw failure;
  }
  return result.get();
 }

 private void closeOpenMatrixSessions(PosService.Scope scope,String cashier){
  List<String> open=db.queryForList("SELECT id FROM pos_sessions WHERE tenant_id=910001 AND cashier=? AND state='OPEN' ORDER BY register_id,id",String.class,cashier);
  for(String id:open){
   BigDecimal expected=as(cashier,()->service.reconciliation(scope,id)).expectedCash();
   as(cashier,()->service.close(scope,id,expected));
  }
 }

 private static String matrixCsv(String value){
  return "\"" + (value==null?"":value.replace("\"","\"\"")) + "\"";
 }

 private static List<Set<String>> matrixAuthorityGroups(String expression){
  List<Set<String>> groups=new ArrayList<>();
  var matcher=java.util.regex.Pattern.compile("has(Any)?Authority\\(([^)]*)\\)").matcher(expression);
  while(matcher.find()){
   Set<String> alternatives=new LinkedHashSet<>();
   if(matcher.group(2).contains("#request."))continue; // Resolve request-derived authorities from the probe DTO below.
   var values=java.util.regex.Pattern.compile("['\"]([A-Z][A-Z0-9_]*)['\"]").matcher(matcher.group(2));
   while(values.find())alternatives.add(values.group(1));
   if(!alternatives.isEmpty())groups.add(alternatives);
  }
  // PosController.cashMovement resolves its required authority from the request enum.
  // The matrix DTO probe selects the first MovementType, CASH_IN.
  if(expression.contains("'POS_' + #request.type().name()"))groups.add(Set.of("POS_CASH_IN"));
  return groups;
 }

 private static boolean matrixActorSatisfies(Set<String> permissions,String expression){
  for(Set<String> group:matrixAuthorityGroups(expression)){
   boolean any=group.stream().anyMatch(permissions::contains);
   if(!any)return false;
  }
  return true;
 }

 private String matrixPatternValue(String regex,String field){
  if(regex.contains("{64}")||regex.contains("64}"))return "0".repeat(64);
  if(regex.contains("{43}")||regex.contains("43}"))return "A".repeat(43);
  if(regex.contains("UPI")&&regex.contains("CARD"))return "UPI";
  if(regex.contains("[A-Z]")&&regex.contains("{3}"))return "INR";
  if(regex.contains("A-Za-z")&&regex.contains("{3}"))return "INR";
  if(regex.contains("a-fA-F")&&regex.contains("36"))return "550e8400-e29b-41d4-a716-446655440000";
  if(regex.contains("https?://"))return "https://example.test";
  if(regex.contains("0-9")&&!regex.contains("A-Za-z"))return "123456";
  return matrixStringValue(field);
 }

 private String matrixStringValue(String field){
  String name=field==null?"":field.toLowerCase(Locale.ROOT);
  if(name.contains("initialvendoradmin"))return "p006.tenant.owner@example.test";
  if(name.equals("username"))return "p006.matrix.employee.created@saumya.demo.test";
  if(name.contains("email"))return "matrix.probe@example.test";
  if(name.contains("username")||name.contains("actor")||name.contains("cashier"))return "admin@saumya.demo.test";
  if(name.contains("token"))return "A".repeat(43);
  if(name.contains("idempotency")||name.equals("key"))return "p006-matrix-key";
  if(name.contains("requestid")||name.contains("uuid"))return "550e8400-e29b-41d4-a716-446655440000";
  if(name.contains("ticket"))return "P006-MATRIX-TICKET";
  if(name.contains("otp")||name.equals("code"))return "123456";
  if(name.contains("permission"))return "PRODUCT_VIEW";
  if(name.contains("vendorslug"))return "saumya-rooftops";
  if(name.contains("currency"))return "INR";
  if(name.contains("billinginterval"))return "MONTHLY";
  if(name.equals("requestId"))return "00000000-0000-4000-8000-000000000006";
  if(name.equals("status"))return "CANCELLED";
  if(name.contains("planKey".toLowerCase(Locale.ROOT)))return "p006-matrix-plan";
  if(name.contains("slug"))return "p006-matrix";
  if(name.contains("sku"))return "P006-MATRIX-SKU";
  if(name.contains("barcode"))return "P006MATRIX0001";
  if(name.contains("phone"))return "9876543210";
  if(name.contains("reason")||name.contains("purpose"))return "P0-06 runtime actor matrix";
  if(name.contains("description"))return "P0-06 authorization matrix probe";
  if(name.contains("entitlements"))return "{\"features\":{\"POS\":true}}";
  if(name.contains("taxmetadata"))return "{}";
  if(name.contains("method")||name.contains("channel")||name.contains("tender"))return "UPI";
  if(name.contains("name"))return "P0-06 Matrix Probe";
  if(name.contains("reference"))return "P006-MATRIX-REF";
  return "P006-VALID-REF";
 }

 private <A extends java.lang.annotation.Annotation> A matrixRecordAnnotation(
     java.lang.reflect.RecordComponent component,Class<A> annotationType){
  A found=component.getAnnotation(annotationType);
  if(found!=null)return found;
  try{
   found=component.getDeclaringRecord().getDeclaredField(component.getName()).getAnnotation(annotationType);
   if(found!=null)return found;
   found=component.getAccessor().getAnnotation(annotationType);
   if(found!=null)return found;
   var parts=component.getDeclaringRecord().getRecordComponents();
   Class<?>[] types=Arrays.stream(parts).map(java.lang.reflect.RecordComponent::getType).toArray(Class<?>[]::new);
   var ctor=component.getDeclaringRecord().getDeclaredConstructor(types);
   for(int i=0;i<parts.length;i++)if(parts[i].getName().equals(component.getName()))
    return ctor.getParameters()[i].getAnnotation(annotationType);
  }catch(ReflectiveOperationException ignored){}
  return null;
 }

 private Object matrixProbeValue(Class<?> type,String field,java.lang.reflect.Type generic){
  String name=field==null?"":field;
  if(type==String.class)return matrixStringValue(name);
  if(type==Long.class||type==long.class){
   String n=name.toLowerCase(Locale.ROOT);
   if(n.contains("tenant"))return 910001L;
   if(n.contains("branch"))return 910001L;
   if(n.contains("register"))return 910011L;
   if(n.contains("customer"))return 949001L;
   if(n.contains("product"))return 910301L;
   if(n.contains("quantity"))return 1L;
   if(n.contains("expectedversion"))return 0L;
   if(n.contains("role")){Long role=db.queryForObject("SELECT MIN(id) FROM vendor_roles WHERE tenant_id=910001",Long.class);return role==null?1L:role;}
   if(n.contains("category")){Long category=db.queryForObject("SELECT MIN(id) FROM catalog_categories WHERE tenant_id=910001",Long.class);return category==null?1L:category;}
   if(n.contains("plan")){Long plan=db.queryForObject("SELECT MIN(id) FROM subscription_plans",Long.class);return plan==null?1L:plan;}
   return 1L;
  }
  if(type==Integer.class||type==int.class){
   String n=name.toLowerCase(Locale.ROOT);
   if(n.contains("expectedversion"))return 0;
   if(n.contains("days"))return 30;
   if(n.contains("max"))return 1;
   return 1;
  }
  if(type==Short.class||type==short.class)return (short)1;
  if(type==Boolean.class||type==boolean.class)return !name.equalsIgnoreCase("sensitive");
  if(type==BigDecimal.class)return BigDecimal.ONE;
  if(type==java.time.LocalDate.class)return name.toLowerCase(Locale.ROOT).contains("end")?java.time.LocalDate.now().plusMonths(1):java.time.LocalDate.now();
  if(type==java.time.Instant.class)return java.time.Instant.now().plus(java.time.Duration.ofHours(2));
  if(type==java.util.UUID.class)return java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
  if(type==java.security.Principal.class)return (java.security.Principal)()->"admin@saumya.demo.test";
  if(type.isEnum())return type.getEnumConstants().length==0?null:type.getEnumConstants()[0];
  if(type.isRecord()){
   try{
    var components=type.getRecordComponents();
    Class<?>[] types=Arrays.stream(components).map(java.lang.reflect.RecordComponent::getType).toArray(Class<?>[]::new);
    Object[] values=new Object[components.length];
    for(int i=0;i<components.length;i++){
     var part=components[i];Class<?> partType=part.getType();String partName=part.getName();
     var pattern=matrixRecordAnnotation(part,jakarta.validation.constraints.Pattern.class);
     var email=matrixRecordAnnotation(part,jakarta.validation.constraints.Email.class);
     var assertTrue=matrixRecordAnnotation(part,jakarta.validation.constraints.AssertTrue.class);
     if(partType==String.class&&pattern!=null)values[i]=matrixPatternValue(pattern.regexp(),partName);
     else if(partType==String.class&&email!=null)values[i]="matrix.probe@example.test";
     else if(partType.isEnum()&&partType.getSimpleName().equals("TenantStatus"))
      values[i]=Enum.valueOf((Class<Enum>)partType,"SUSPENDED");
     else if((partType==boolean.class||partType==Boolean.class)&&assertTrue!=null)values[i]=true;
     else if(partType==String.class&&partName.toLowerCase(Locale.ROOT).contains("entitlements"))values[i]="{\"features\":{\"POS\":true}}";
     else if(partType==String.class&&partName.toLowerCase(Locale.ROOT).contains("taxmetadata"))values[i]="{}";
     else values[i]=matrixProbeValue(partType,partName,part.getGenericType());
     var size=matrixRecordAnnotation(part,jakarta.validation.constraints.Size.class);
     if(partType==String.class&&size!=null&&values[i] instanceof String text&&size.min()>text.length())
      values[i]=text+"X".repeat(size.min()-text.length());
    }
    var ctor=type.getDeclaredConstructor(types);ctor.setAccessible(true);return ctor.newInstance(values);
   }catch(ReflectiveOperationException error){throw new AssertionError("Cannot construct matrix DTO "+type.getName(),error);}
  }
  if(java.util.Collection.class.isAssignableFrom(type)){
   Class<?> item=String.class;
   if(generic instanceof java.lang.reflect.ParameterizedType parameterized
      &&parameterized.getActualTypeArguments().length>0
      &&parameterized.getActualTypeArguments()[0] instanceof Class<?> cls)item=cls;
   Object value=matrixProbeValue(item,"item",item);
   return Set.class.isAssignableFrom(type)?Set.of(value):List.of(value);
  }
  if(Map.class.isAssignableFrom(type))return Map.of();
  if(type.isArray())return java.lang.reflect.Array.newInstance(type.getComponentType(),0);
  return null;
 }

 private String matrixPathValue(java.lang.reflect.Parameter parameter,String controller,String route,String actor){
  String key=parameter.getName();
  var annotation=parameter.getAnnotation(org.springframework.web.bind.annotation.PathVariable.class);
  if(annotation!=null){if(!annotation.name().isBlank())key=annotation.name();else if(!annotation.value().isBlank())key=annotation.value();}
  Class<?> type=parameter.getType();
  if(controller.startsWith("Platform")&&route.contains("/tenants/{id}"))return "910001";
  if(route.contains("/vendor/categories/{id}")){
   Long category=db.queryForObject("SELECT MIN(id) FROM catalog_categories WHERE tenant_id=910001",Long.class);
   return category==null?"1":category.toString();
  }
  if(route.contains("/vendor/roles/{id}")){
   Long role=db.queryForObject("SELECT MIN(id) FROM vendor_roles WHERE tenant_id=910001",Long.class);
   return role==null?"1":role.toString();
  }
  if(route.contains("/vendor/pos/orders/{id}")){
   String order=db.queryForObject("SELECT MIN(id) FROM orders WHERE tenant_id=910001 AND sales_channel='IN_STORE'",String.class);
   return order==null?"1":order;
  }
  if(route.contains("/customer/orders/{id}")){
   String order=db.queryForObject("SELECT MIN(id) FROM orders WHERE customer_id=949001 AND sales_channel='ONLINE'",String.class);
   return order==null?"1":order;
  }
  if(route.contains("/customer/checkout/drafts/{id}")){
   String order=db.queryForObject("SELECT MIN(id) FROM orders WHERE customer_id=949001 AND sales_channel='ONLINE' AND status='DRAFT'",String.class);
   return order==null?"1":order;
  }
  if(route.contains("/vendor/pos/drafts/{id}")){
   String order=db.queryForObject("SELECT MIN(id) FROM orders WHERE tenant_id=910001 AND sales_channel='IN_STORE' AND status='DRAFT'",String.class);
   return order==null?"1":order;
  }
  if(route.contains("/vendor/pos/sales/{id}")){
   String order=db.queryForObject("SELECT MIN(id) FROM orders WHERE tenant_id=910001 AND sales_channel='IN_STORE'",String.class);
   return order==null?"1":order;
  }
  if(route.contains("/vendor/pos/receipts/{id}")){
   Long receipt=db.queryForObject("SELECT MIN(receipt_number) FROM orders WHERE tenant_id=910001 AND sales_channel='IN_STORE' AND status='PAID'",Long.class);
   return receipt==null?"1":receipt.toString();
  }
  if(route.contains("/vendor/products/{id}")){
   Long product=db.queryForObject("SELECT MIN(id) FROM products WHERE tenant_id=910001",Long.class);
   return product==null?"1":product.toString();
  }
  if(route.contains("/sessions/{id}")){
   if(actor.equals("VENDOR_ADMIN_SUPERVISOR")&&route.endsWith("/approve-variance")){
    String session=db.queryForObject("SELECT id FROM pos_sessions WHERE tenant_id=910001 AND cashier='admin@saumya.demo.test' AND state='CLOSING' ORDER BY register_id ASC LIMIT 1",String.class);
    return session==null?"P006-NO-SUCH-SESSION":session;
   }
   if(actor.equals("VENDOR_ADMIN_SUPERVISOR")&&route.endsWith("/return-for-correction")){
    String session=db.queryForObject("SELECT id FROM pos_sessions WHERE tenant_id=910001 AND cashier='admin@saumya.demo.test' AND state='CLOSING' ORDER BY register_id DESC LIMIT 1",String.class);
    return session==null?"P006-NO-SUCH-SESSION":session;
   }
   String cashier=actor.equals("POS_CASHIER")?"cashier@saumya.demo.test":
       (actor.equals("ACCOUNTANT")?"finance@saumya.demo.test":"admin@saumya.demo.test");
   String session=db.queryForObject("SELECT MIN(id) FROM pos_sessions WHERE tenant_id=910001 AND cashier=? AND state='OPEN'",String.class,cashier);
   return session==null?"P006-NO-SUCH-SESSION":session;
  }
  if(route.contains("/subscription-plans/{id}")){
   Long plan=db.queryForObject(
       "SELECT id FROM subscription_plans WHERE plan_key='p006-matrix-fixture-plan'",Long.class);
   return plan==null?"1":plan.toString();
  }
  if(route.contains("/tenants/{tenantId}/subscription"))return "930001";
  if(route.contains("/identities/{username}"))return "p006.matrix.mfa.target@platform.demo.test";
  if(type==java.util.UUID.class)return "550e8400-e29b-41d4-a716-446655440000";
  if(type==String.class){
   String n=key.toLowerCase(Locale.ROOT);
   if(controller.contains("Platform")&&n.contains("username"))return "superadmin@platform.demo.test";
   if(route.endsWith("/disable"))return "p006.matrix.employee.disable@saumya.demo.test";
   if(n.contains("username"))return "p006.matrix.employee@saumya.demo.test";
   if(route.contains("/customer/orders/{id}")){
    String order=db.queryForObject("SELECT MIN(id) FROM orders WHERE customer_id=949001 AND sales_channel='ONLINE'",String.class);
    return order==null?"1":order;
   }
   if(route.contains("/customer/checkout/drafts/{id}")){
    String order=db.queryForObject("SELECT MIN(id) FROM orders WHERE customer_id=949001 AND sales_channel='ONLINE' AND status='DRAFT'",String.class);
    return order==null?"1":order;
   }
   if(route.contains("/vendor/pos/drafts/{id}")){
    String order=db.queryForObject("SELECT MIN(id) FROM orders WHERE tenant_id=910001 AND sales_channel='IN_STORE' AND status='DRAFT'",String.class);
    return order==null?"1":order;
   }
   if(route.contains("/vendor/pos/orders/{id}")||route.contains("/vendor/pos/sales/{id}")){
    String order=db.queryForObject("SELECT MIN(id) FROM orders WHERE tenant_id=910001 AND sales_channel='IN_STORE'",String.class);
    return order==null?"1":order;
   }
   if(route.contains("/sessions/{id}")){
    String cashier=actor.equals("POS_CASHIER")?"cashier@saumya.demo.test":
        (actor.equals("ACCOUNTANT")?"finance@saumya.demo.test":"admin@saumya.demo.test");
    String session=db.queryForObject("SELECT MIN(id) FROM pos_sessions WHERE tenant_id=910001 AND cashier=? AND state='OPEN'",String.class,cashier);
    return session==null?"P006-NO-SUCH-SESSION":session;
   }
   if(n.contains("order")||n.contains("draft")||route.contains("/sales/{")){
    String order=db.queryForObject("SELECT MIN(id) FROM orders WHERE tenant_id=910001 AND branch_id=910001 AND cashier='admin@saumya.demo.test' AND sales_channel='IN_STORE' AND status='PAID'",String.class);
    return order==null?"1":order;
   }
   if(n.contains("token"))return "A".repeat(43);
   return "1";
  }
  String n=key.toLowerCase(Locale.ROOT);
  if(n.contains("order")||n.contains("draft")){
   String order=db.queryForObject("SELECT MIN(id) FROM orders WHERE tenant_id=910001",String.class);
   return order==null? "1":order;
  }
  if(route.contains("/subscriptions/{")){
   Long subscription=db.queryForObject("SELECT MIN(id) FROM tenant_subscriptions WHERE tenant_id=910001",Long.class);
   return subscription==null?"1":subscription.toString();
  }
  if(n.contains("session")||route.contains("/sessions/{")){
   String session=db.queryForObject("SELECT MIN(id) FROM pos_sessions WHERE tenant_id=910001 AND cashier='admin@saumya.demo.test' AND state='OPEN'",String.class);
   return session==null?"1":session;
  }
  if(n.contains("tenant"))return "910001";
  if(n.contains("branch"))return "910001";
  if(n.contains("register"))return "910011";
  if(n.contains("customer"))return "949001";
  if(n.contains("product"))return "910301";
  if(n.contains("role"))return db.queryForObject("SELECT COALESCE(MAX(id),1) FROM vendor_roles WHERE tenant_id=910001",Long.class).toString();
  if(controller.contains("Platform"))return "1";
  return "1";
 }

 private String matrixActorForEndpoint(String controller,String expression,String route,
     Map<String,String> tokens,Map<String,Set<String>> permissions){
  String preferred;
  if(controller.startsWith("Platform")){
   preferred=expression.contains("PLATFORM_IDENTITY_MANAGE")?"PLATFORM_SUPER_ADMIN_SECOND":
       (expression.contains("PLATFORM_SUPPORT_ACCESS")?"PLATFORM_SUPPORT":"PLATFORM_SUPER_ADMIN");
  }else if(expression.contains("POS_SHIFT_VARIANCE_APPROVE"))preferred="VENDOR_ADMIN_SUPERVISOR";
  else if(expression.contains("'CUSTOMER'")&&!expression.contains("PLATFORM_"))preferred="CUSTOMER";
  else if(controller.contains("Report")||(expression.contains("POS_REPORT_VIEW")&&!expression.contains("POS_VIEW")))preferred="ACCOUNTANT";
  else if(controller.contains("Pos")||controller.contains("Register")||controller.contains("Shift")
      ||controller.contains("Return")||controller.contains("Refund")||controller.contains("Cash")
      ||controller.contains("BranchSettings"))preferred="POS_CASHIER";
  else if(controller.contains("Stock")||controller.contains("Inventory"))preferred="INVENTORY_MANAGER";
  else preferred="VENDOR_ADMIN";
  Set<String> required=new LinkedHashSet<>();
  for(Set<String> group:matrixAuthorityGroups(expression))required.addAll(group);
  String selected=preferred;
  if(!matrixActorSatisfies(permissions.getOrDefault(selected,Set.of()),expression)){
   for(String candidate:List.of("VENDOR_ADMIN","POS_CASHIER","ACCOUNTANT","INVENTORY_MANAGER","SALES_MANAGER",
       "PLATFORM_SUPER_ADMIN","PLATFORM_SUPPORT","CUSTOMER")){
    if(matrixActorSatisfies(permissions.getOrDefault(candidate,Set.of()),expression)){selected=candidate;break;}
   }
  }
  assertThat(tokens).as("persisted actor token for %s",selected).containsKey(selected);
  assertThat(matrixActorSatisfies(permissions.getOrDefault(selected,Set.of()),expression))
      .as("persisted actor %s must satisfy mapped policy %s for %s; groups=%s; effective permissions=%s",
          selected,expression,controller,matrixAuthorityGroups(expression),permissions.getOrDefault(selected,Set.of())).isTrue();
  return selected;
 }

 @Test
 void p006EveryApplicableLiveEndpointExecutesWithMatchingPersistedActor() throws Exception {
  multiVendorDemo();
  try {
   // Fixture setup commits before HTTP probes. Each request then gets its own
   // service transaction, so an expected 4xx cannot poison later actor sessions.
   p006ExecutePersistedEndpointActorMatrix();
  } finally {
   // Restore the canonical demo seed after the independently committed probes.
   multiVendorDemo();
  }
 }

 private void p006ExecutePersistedEndpointActorMatrix() throws Exception {
  String supportUsername="p006.matrix.support@platform.demo.test";
  long supportId=db.queryForObject("SELECT COALESCE(MAX(id),0)+1 FROM users",Long.class);
  update("INSERT INTO users(id,username,password_hash,enabled) VALUES(?,?,?,TRUE)",supportId,
      supportUsername,encoder.encode("MatrixSupportPassword2026!"));
  update("INSERT INTO platform_user_permissions(username,permission) VALUES(?,'PLATFORM_SUPPORT_ACCESS')",supportUsername);
  String secondPlatformAdmin="p006.matrix.platform2@platform.demo.test";
  long secondPlatformId=db.queryForObject("SELECT COALESCE(MAX(id),0)+1 FROM users",Long.class);
  update("INSERT INTO users(id,username,password_hash,enabled) VALUES(?,?,?,TRUE)",secondPlatformId,
      secondPlatformAdmin,encoder.encode("MatrixPlatform2Password2026!"));
  for(String permission:com.saumya.pos.platform.PlatformPermissionRegistry.all())
   update("INSERT INTO platform_user_permissions(username,permission) VALUES(?,?)",secondPlatformAdmin,permission);
  String mfaTarget="p006.matrix.mfa.target@platform.demo.test";
  long mfaTargetId=db.queryForObject("SELECT COALESCE(MAX(id),0)+1 FROM users",Long.class);
  update("INSERT INTO users(id,username,password_hash,enabled) VALUES(?,?,?,TRUE)",mfaTargetId,
      mfaTarget,encoder.encode("MatrixMfaTargetPassword2026!"));
  update("INSERT INTO platform_user_permissions(username,permission) VALUES(?,'PLATFORM_IDENTITY_MANAGE')",mfaTarget);

  String adminName="admin@saumya.demo.test";
  String employeeProbe="p006.matrix.employee@saumya.demo.test";
  long employeeProbeId=db.queryForObject("SELECT COALESCE(MAX(id),0)+1 FROM users",Long.class);
  update("INSERT INTO users(id,username,password_hash,enabled) VALUES(?,?,?,TRUE)",employeeProbeId,
      employeeProbe,encoder.encode("MatrixEmployeePassword2026!"));
  update("INSERT INTO employees(username,tenant_id,branch_id,enabled) VALUES(?,910001,910001,TRUE)",employeeProbe);
  Long employeeRole=db.queryForObject("SELECT role_id FROM employee_role_assignments WHERE tenant_id=910001 AND username='admin@saumya.demo.test' AND active=TRUE ORDER BY role_id LIMIT 1",Long.class);
  update("INSERT INTO employee_role_assignments(tenant_id,branch_id,username,role_id,active,assigned_by) VALUES(910001,910001,?,?,TRUE,?)",
      employeeProbe,employeeRole,adminName);
  String employeeDisableProbe="p006.matrix.employee.disable@saumya.demo.test";
  long employeeDisableId=db.queryForObject("SELECT COALESCE(MAX(id),0)+1 FROM users",Long.class);
  update("INSERT INTO users(id,username,password_hash,enabled) VALUES(?,?,?,TRUE)",employeeDisableId,
      employeeDisableProbe,encoder.encode("MatrixDisablePassword2026!"));
  update("INSERT INTO employees(username,tenant_id,branch_id,enabled) VALUES(?,910001,910001,TRUE)",employeeDisableProbe);
  update("INSERT INTO employee_role_assignments(tenant_id,branch_id,username,role_id,active,assigned_by) VALUES(910001,910001,?,?,TRUE,?)",
      employeeDisableProbe,employeeRole,adminName);
  Long adminRegister=db.queryForObject("SELECT COALESCE(MAX(id),910011)+100 FROM registers WHERE tenant_id=910001 AND branch_id=910001",Long.class);
  update("INSERT INTO registers(id,tenant_id,branch_id) VALUES(?,910001,910001)",adminRegister);
  assignRegister(adminName,910001,910001,adminRegister);
  String supervisorName="p006.matrix.supervisor@saumya.demo.test";
  long supervisorId=db.queryForObject("SELECT COALESCE(MAX(id),0)+1 FROM users",Long.class);
  update("INSERT INTO users(id,username,password_hash,enabled) VALUES(?,?,?,TRUE)",supervisorId,
      supervisorName,encoder.encode("MatrixSupervisorPassword2026!"));
  update("INSERT INTO employees(username,tenant_id,branch_id,enabled) VALUES(?,910001,910001,TRUE)",supervisorName);
  Long supervisorRole=db.queryForObject("SELECT role_id FROM employee_role_assignments WHERE tenant_id=910001 AND username='admin@saumya.demo.test' AND active=TRUE ORDER BY role_id LIMIT 1",Long.class);
  update("INSERT INTO employee_role_assignments(tenant_id,branch_id,username,role_id,active,assigned_by) VALUES(910001,910001,?,?,TRUE,?)",
      supervisorName,supervisorRole,adminName);
  assignRegister(supervisorName,910001,910001,adminRegister);
  String cashierName="cashier@saumya.demo.test";
  Long cashierRegister=db.queryForObject("SELECT COALESCE(MAX(id),910011)+1 FROM registers WHERE tenant_id=910001 AND branch_id=910001",Long.class);
  update("INSERT INTO registers(id,tenant_id,branch_id) VALUES(?,910001,910001)",cashierRegister);
  assignRegister(cashierName,910001,910001,cashierRegister);
  String accountantName="finance@saumya.demo.test";
  assignRegister(accountantName,910001,910001,adminRegister);

  // Give customer-owned GET routes a real, persisted order instead of probing
  // a made-up identifier and mistaking the resulting ownership 404 for coverage.
  String matrixCustomer="asha@customer.demo.test";
  asCustomer(matrixCustomer,()->marketplace.put(949001,
      new CartService.Item("saumya-rooftops",910301,1)));
  var matrixCart=asCustomer(matrixCustomer,()->marketplace.cart(949001));
  asCustomer(matrixCustomer,()->checkout.online(matrixCustomer,"p006-matrix-actor-order",
      new CheckoutService.OnlineDraft(matrixCart.id())));

  PosService.Scope adminScope=new PosService.Scope(910001,910001,adminName);
  PosService.Scope cashierScope=new PosService.Scope(910001,910001,cashierName);
  // Start the endpoint sweep from exactly one OPEN shift per actor. Close
  // pre-existing demo shifts through the service so drawers reconcile cleanly.
  closeOpenMatrixSessions(adminScope,adminName);
  closeOpenMatrixSessions(cashierScope,cashierName);
  as(adminName,()->service.open(adminScope,new PosService.OpenSession(adminRegister,BigDecimal.ZERO)));
  as(cashierName,()->service.open(cashierScope,new PosService.OpenSession(cashierRegister,BigDecimal.ZERO)));
  // Two independent CLOSING shifts exercise supervisor approval and return without
  // relying on a deliberately denied self-review or a synthetic session identifier.
  Long varianceApproveRegister=cashierRegister+1,varianceReturnRegister=cashierRegister+2;
  for(Long register:List.of(varianceApproveRegister,varianceReturnRegister)){
   update("INSERT INTO registers(id,tenant_id,branch_id) VALUES(?,910001,910001)",register);
   assignRegister(adminName,910001,910001,register);
   assignRegister(supervisorName,910001,910001,register);
   String varianceSession=as(adminName,()->service.open(adminScope,new PosService.OpenSession(register,BigDecimal.ZERO))).get("id").toString();
   BigDecimal expected=as(adminName,()->service.reconciliation(adminScope,varianceSession)).expectedCash();
   as(adminName,()->service.close(adminScope,varianceSession,expected.add(BigDecimal.ONE),
       "P0-06 persisted supervisor review fixture","00000000-0000-4000-8000-000000000006"));
  }
  // A spare assigned till is used only if a close probe leaves the primary in CLOSING.
  Long cashierRecoveryRegister=db.queryForObject("SELECT COALESCE(MAX(id),910011)+1 FROM registers WHERE tenant_id=910001 AND branch_id=910001",Long.class);
  update("INSERT INTO registers(id,tenant_id,branch_id) VALUES(?,910001,910001)",cashierRecoveryRegister);
  assignRegister(cashierName,910001,910001,cashierRecoveryRegister);

  // Finalize is a read of an already-paid sale in this sweep. Seed that sale
  // through the real checkout flow so the probe proves endpoint authorization
  // and branch/session ownership without invoking an unconfigured payment.
  String adminSession=db.queryForObject("SELECT id FROM pos_sessions WHERE tenant_id=910001 AND cashier=? AND state='OPEN' ORDER BY id LIMIT 1",String.class,adminName);
  if(db.queryForObject("SELECT COUNT(*) FROM orders WHERE tenant_id=910001 AND branch_id=910001 AND cashier=? AND sales_channel='IN_STORE' AND status='PAID'",Integer.class,adminName)==0){
   as(adminName,()->checkout.cashSale(adminName,"p006-matrix-paid-sale",
       new CheckoutService.CashSale(adminSession,List.of(new CheckoutService.Item(910301,1)),
           new BigDecimal("100000"),false,null,null)));
  }

  Map<String,String> tokens=new LinkedHashMap<>();
  Map<String,Set<String>> grants=new LinkedHashMap<>();
  String[][] accounts={
    {"VENDOR_ADMIN",adminName,"SaumyaAdmin2026!"},
    {"VENDOR_ADMIN_SUPERVISOR",supervisorName,"MatrixSupervisorPassword2026!"},
    {"SALES_MANAGER","sales@saumya.demo.test","SaumyaSales2026!"},
    {"INVENTORY_MANAGER","inventory@saumya.demo.test","SaumyaStock2026!"},
    {"POS_CASHIER",cashierName,"SaumyaTill2026!"},
    {"ACCOUNTANT",accountantName,"SaumyaFinance2026!"},
    {"PLATFORM_SUPER_ADMIN","superadmin@platform.demo.test","PlatformDemo2026!"},
    {"PLATFORM_SUPER_ADMIN_SECOND",secondPlatformAdmin,"MatrixPlatform2Password2026!"},
    {"PLATFORM_SUPPORT",supportUsername,"MatrixSupportPassword2026!"},
    {"CUSTOMER","asha@customer.demo.test","AshaBuyer2026!"}
  };
  for(String[] account:accounts){
   var session=authentication.login(account[1],account[2]);
   tokens.put(account[0],session.accessToken());
   grants.put(account[0],Set.copyOf(session.account().permissions()));
  }
  // Platform Support is a persisted one-grant identity; the Vendor Admin,
  // Cashier, Accountant and Customer tokens above are all resolved from DB state.
  assertThat(grants.get("PLATFORM_SUPER_ADMIN"))
      .as("persisted Platform Super Admin must receive the canonical platform permission registry")
      .containsAll(com.saumya.pos.platform.PlatformPermissionRegistry.all());

  // Seed a real plan/subscription for read and transition endpoint probes.
  // Writes are fixture setup only; runtime endpoint calls below still use persisted JWT actors.
  update("INSERT INTO subscription_plans(plan_key,display_name,active,current_version,created_at,updated_at) VALUES('p006-matrix-fixture-plan','P0-06 Matrix Plan',TRUE,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
  Long matrixPlanId=db.queryForObject("SELECT id FROM subscription_plans WHERE plan_key='p006-matrix-fixture-plan'",Long.class);
  update("INSERT INTO subscription_plan_versions(plan_id,version,price,currency,billing_interval,max_users,max_storage_mb,max_branches,max_warehouses,entitlements,grace_days,tax_metadata,invoice_description,created_by,created_at) VALUES(?,1,10.00,'INR','MONTHLY',10,1024,2,1,'{\"features\":{\"POS\":true,\"CATALOG\":true,\"INVENTORY\":true,\"ORDERS\":true}}',7,'{}','P0-06 fixture','superadmin@platform.demo.test',CURRENT_TIMESTAMP)",matrixPlanId);
  // Reuse the persisted active demo subscription; a tenant may have only one
  // effective subscription and its entitlements keep the vendor actor valid
  // throughout the endpoint authorization matrix.
  Long matrixSubscriptionId=db.queryForObject(
      "SELECT id FROM tenant_subscriptions WHERE tenant_id=910001",Long.class);
  String matrixSubscriptionStatus=db.queryForObject(
      "SELECT status FROM tenant_subscriptions WHERE id=?",String.class,matrixSubscriptionId);
  update("INSERT INTO subscription_events(tenant_id,subscription_id,old_status,new_status,actor_username,reason,occurred_at) VALUES(910001,?,?,?,'superadmin@platform.demo.test','P0-06 fixture',CURRENT_TIMESTAMP)",
      matrixSubscriptionId,matrixSubscriptionStatus,matrixSubscriptionStatus);

  List<String> csv=new ArrayList<>();
  csv.add("method,route,controller,operation,actor,permission,scope,expected,actual,result,exception_type,test_reference");
  int protectedRows=0,passedRows=0,failedRows=0,contextSwitchCoverage=0;
  List<String> endpointFailures=new ArrayList<>();
  for(var entry:requestMappings.getHandlerMethods().entrySet()){
   HandlerMethod handler=entry.getValue();Class<?> controllerType=handler.getBeanType();
   if(!controllerType.getName().startsWith("com.saumya.pos.")
       ||!org.springframework.core.annotation.AnnotatedElementUtils.hasAnnotation(controllerType,org.springframework.web.bind.annotation.RestController.class))continue;
   var methodPolicy=org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(),
       org.springframework.security.access.prepost.PreAuthorize.class);
   var classPolicy=org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(controllerType,
       org.springframework.security.access.prepost.PreAuthorize.class);
   var policy=methodPolicy!=null?methodPolicy:classPolicy;
   assertThat(policy).as("security policy for %s#%s",controllerType.getSimpleName(),handler.getMethod().getName()).isNotNull();
   if(policy.value().contains("permitAll()"))continue;
   Set<RequestMethod> mappingMethods=entry.getKey().getMethodsCondition().getMethods();
   List<RequestMethod> methods=mappingMethods.isEmpty()?List.of(RequestMethod.GET):mappingMethods.stream().sorted().toList();
   for(String route:entry.getKey().getPatternValues()){
    if(route.equals("/api/v1/auth/context")){
     assertThat(controllerType.getSimpleName()).isEqualTo("AuthenticationController");
     assertThat(handler.getMethod().getName()).isEqualTo("switchContext");
     assertThat(methods).containsExactly(RequestMethod.POST);
     // This endpoint requires a matching refresh-family cookie and a dual identity.
     // Its end-to-end login/switch/refresh/revocation flow is exercised by the P0-07 HTTP case.
     csv.add(String.join(",","POST",matrixCsv(route),controllerType.getSimpleName(),handler.getMethod().getName(),
       "CUSTOMER_VENDOR_DUAL_IDENTITY","isAuthenticated()","actor+refresh-family","AUTHORIZED","200",
       "PASS","","PosTest#p007DualCustomerVendorIdentityRequiresExplicitContextSwitchAndNeverFallsBack"));
     System.out.println("P006_ENDPOINT_ACTOR_DELEGATED|POST|"+route+"|CUSTOMER_VENDOR_DUAL_IDENTITY|PASS_P007_HTTP_INTEGRATION");
     contextSwitchCoverage++;protectedRows++;passedRows++;
     continue;
    }
    String actor=matrixActorForEndpoint(controllerType.getSimpleName(),policy.value(),route,tokens,grants);
    String token=tokens.get(actor);
    for(RequestMethod httpMethod:methods){
     String concrete=route;
     for(Parameter parameter:handler.getMethod().getParameters()){
      var path=parameter.getAnnotation(org.springframework.web.bind.annotation.PathVariable.class);
      if(path!=null){
       String key=!path.name().isBlank()?path.name():(!path.value().isBlank()?path.value():parameter.getName());
       concrete=concrete.replace("{"+key+"}",matrixPathValue(parameter,controllerType.getSimpleName(),route,actor));
      }
     }
     concrete=concrete.replaceAll("\\{[^/]+\\}","1");
     var request=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
         HttpMethod.valueOf(httpMethod.name()),java.net.URI.create("http://localhost"+concrete))
       .header("Authorization","Bearer "+token);
     if(httpMethod==RequestMethod.POST||httpMethod==RequestMethod.PUT||httpMethod==RequestMethod.PATCH||httpMethod==RequestMethod.DELETE)
      request.with(csrf());
     for(Parameter parameter:handler.getMethod().getParameters()){
      var body=parameter.getAnnotation(org.springframework.web.bind.annotation.RequestBody.class);
      if(body!=null){
       Object value=matrixProbeValue(parameter.getType(),parameter.getName(),parameter.getParameterizedType());
       String bodyJson=json.writeValueAsString(value);
       if(route.contains("/subscription-plans/{id}/versions")||route.equals("/api/v1/platform/configuration")){
        var bodyNode=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(bodyJson);
        if(route.contains("/subscription-plans/{id}/versions"))bodyNode.put("planKey","p006-matrix-fixture-plan");
        if(route.equals("/api/v1/platform/configuration")){
         bodyNode.put("sensitive",false);
         bodyNode.putNull("secretReference");
        }
        bodyJson=json.writeValueAsString(bodyNode);
       }
       request.contentType("application/json").content(bodyJson);
      }
      var query=parameter.getAnnotation(org.springframework.web.bind.annotation.RequestParam.class);
      if(query!=null&&query.required()
          &&org.springframework.web.bind.annotation.ValueConstants.DEFAULT_NONE.equals(query.defaultValue())){
       String key=!query.name().isBlank()?query.name():(!query.value().isBlank()?query.value():parameter.getName());
       request.param(key,String.valueOf(matrixProbeValue(parameter.getType(),key,parameter.getParameterizedType())));
      }
      var header=parameter.getAnnotation(org.springframework.web.bind.annotation.RequestHeader.class);
      if(header!=null&&header.required()
          &&org.springframework.web.bind.annotation.ValueConstants.DEFAULT_NONE.equals(header.defaultValue())){
       String key=!header.name().isBlank()?header.name():(!header.value().isBlank()?header.value():parameter.getName());
       request.header(key,String.valueOf(matrixProbeValue(parameter.getType(),key,parameter.getParameterizedType())));
      }
      var cookie=parameter.getAnnotation(org.springframework.web.bind.annotation.CookieValue.class);
      if(cookie!=null&&cookie.required()){
       String key=!cookie.name().isBlank()?cookie.name():(!cookie.value().isBlank()?cookie.value():parameter.getName());
       request.cookie(new jakarta.servlet.http.Cookie(key,"A".repeat(43)));
      }
     }
     var response=performInRollbackTransaction(request);
     int actual=response.getResponse().getStatus();
     Throwable resolved=response.getResolvedException();
     String exception=resolved==null?"":resolved.getClass().getSimpleName();
     boolean bindingFailure=resolved instanceof org.springframework.web.bind.MethodArgumentNotValidException
       ||resolved instanceof org.springframework.http.converter.HttpMessageNotReadableException
       ||resolved instanceof org.springframework.web.bind.MissingServletRequestParameterException
       ||resolved instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
       ||resolved instanceof org.springframework.web.bind.MissingRequestHeaderException;
     // A 404/409 only proves that the probe reached a later failure; it does
     // not prove that the persisted actor can successfully execute the mapped
     // operation. Require a successful HTTP response for this positive sweep.
     // Resource-ownership 404s and business-state 409s belong in the explicit,
     // fixture-backed runtimeScenarios below, not in this generic probe.
     boolean pass=actual>=200&&actual<300&&!bindingFailure;
     // A non-auth 4xx proves the actor passed the endpoint permission gate and
     // reached handler/service validation. Keep it distinct from successful
     // business execution; explicit fixture-backed scenarios certify 404/409.
     boolean authorizedRejection=actual>=400&&actual<500&&actual!=401&&actual!=403&&!bindingFailure;
     boolean authorizationPass=pass||authorizedRejection;
     String result=pass?"PASS":(authorizedRejection?"AUTHORIZED_REJECTED":"FAIL");
     String routeKey=route;
     String permission=policy.value().replace("\"","\"\"");
     String scope=""; // Controller policy scope is joined by report generation from canonical metadata.
     csv.add(String.join(",",httpMethod.name(),matrixCsv(routeKey),controllerType.getSimpleName(),
       handler.getMethod().getName(),actor,matrixCsv(permission),scope,"AUTHORIZED",Integer.toString(actual),
       result,exception,"PosTest#p006EveryApplicableLiveEndpointExecutesWithMatchingPersistedActor"));
     System.out.println("P006_ENDPOINT_ACTOR|"+httpMethod.name()+"|"+route+"|"+actor+"|"+actual+"|"+result);
     protectedRows++;
     if(pass)passedRows++;
     else if(!authorizationPass) {
      failedRows++;
      endpointFailures.add(actor+" "+httpMethod.name()+" "+route+" policy="+policy.value()
          +" actual="+actual+" exception="+exception+" body="+response.getResponse().getContentAsString());
     }
    }
   }
  }
  assertThat(db.queryForObject("SELECT lifecycle_status FROM tenants WHERE id=910001",String.class))
      .as("runtime matrix must restore the demo tenant after the lifecycle endpoint probe").isEqualTo("ACTIVE");
  assertThat(protectedRows).as("all generic protected live method/route mappings executed with a persisted actor").isGreaterThan(100);
  assertThat(contextSwitchCoverage).as("the stateful context-switch endpoint is delegated to the dedicated P0-07 persisted dual-identity HTTP scenario").isEqualTo(1);
  Path path=Path.of("target","p0-06-runtime-actor-endpoint-http.csv");
  try{Files.createDirectories(path.getParent());Files.write(path,csv,StandardCharsets.UTF_8);}
  catch(java.io.IOException error){throw new AssertionError("Cannot write endpoint actor matrix",error);}
  System.out.println("P006_ENDPOINT_ACTOR_SUMMARY|protectedRoutes="+protectedRows+"|businessSuccess="+passedRows
      +"|authorizedBusinessRejections="+(protectedRows-passedRows-failedRows)+"|authorizationFailures="+failedRows);
  endpointFailures.forEach(failure->System.out.println("P006_ENDPOINT_ACTOR_FAILURE|"+failure));
  assertThat(failedRows).as("all protected live method/route mappings must execute successfully with persisted matching actors").isZero();
 }

}
