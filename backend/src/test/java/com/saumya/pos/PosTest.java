package com.saumya.pos;
import com.saumya.pos.cart.CartService;
import com.saumya.pos.catalog.CatalogService;
import com.saumya.pos.retail.PosService;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:pos;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}","spring.datasource.username=${TEST_DB_USER:sa}","spring.datasource.password=${TEST_DB_PASSWORD:}","checkout.cleanup-enabled=false"})
@AutoConfigureMockMvc
@org.springframework.test.context.TestExecutionListeners(listeners=org.springframework.test.context.support.DependencyInjectionTestExecutionListener.class,inheritListeners=false)
class PosTest {
 @Autowired PosService service; @Autowired JdbcTemplate db; @Autowired MockMvc mvc;
 @Autowired CartService marketplace;
 @Autowired CatalogService catalogue;
 @Autowired com.saumya.pos.inventory.InventoryService stock;
 @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
 @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
 @Autowired com.saumya.pos.checkout.CheckoutService checkout;
 PosService.Scope a=new PosService.Scope(1,1,"alice"),b=new PosService.Scope(2,2,"bob");
 String session;
 @BeforeEach void seed(){
  db.update("UPDATE tenants SET online_branch_id=NULL");
  for(String table:List.of("stock_reservations","vendor_invoices","inventory_ledger","order_items","orders","cart_items","carts","customer_profiles","users","pos_shift_reviews","pos_cash_movements","pos_audit","pos_sessions","employee_permissions","inventory","products","registers","employees","branches","tenants")) db.update("DELETE FROM "+table);
  db.update("INSERT INTO tenants(id,name,active) VALUES(1,'Vendor A',TRUE),(2,'Vendor B',TRUE)");
  db.update("INSERT INTO branches(id,tenant_id,name) VALUES(1,1,'A'),(2,2,'B')");
  db.update("INSERT INTO employees VALUES('alice','unused',1,1,TRUE),('bob','unused',2,2,TRUE)");
  db.update("INSERT INTO registers VALUES(1,1,1),(2,2,2)");
  db.update("INSERT INTO products VALUES(1,1,'P1','123','Panel',100,10,TRUE),(2,2,'P2',NULL,'Private',100,0,TRUE)");
  db.update("INSERT INTO inventory(tenant_id,branch_id,product_id,quantity) VALUES(1,1,1,1),(2,2,2,1)");
  session=(String)service.open(a,new PosService.OpenSession(1,new BigDecimal("50"))).get("id");
 }
 PosService.Checkout sale(long product,String cash){return new PosService.Checkout(session,List.of(new PosService.Line(product,1)),new BigDecimal(cash),false);}
 @Test void guestCashIsAtomicAndIdempotent(){
  var r=service.checkout(a,"checkout-1",sale(1,"120"));
  assertThat(r.get("customer_id")).isNull();assertThat(r.get("total")).isEqualTo(new BigDecimal("110.00"));assertThat(r.get("change_given")).isEqualTo(new BigDecimal("10.00"));
  assertThat(service.checkout(a,"checkout-1",sale(1,"120")).get("id")).isEqualTo(r.get("id"));
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isZero();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);
  assertThatThrownBy(()->service.checkout(a,"checkout-1",sale(1,"130")));
  assertThatThrownBy(()->service.receipt(b,(String)r.get("id")));
  assertThat(service.reprint(a,(String)r.get("id")).get("copy")).isEqualTo(true);
 }
 @Test void forbiddenProductAndUnderpaymentLeaveNoSale(){
  assertThatThrownBy(()->service.checkout(a,"foreign-1",sale(2,"120")));
  assertThatThrownBy(()->service.checkout(a,"underpay1",sale(1,"1")));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isZero();
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isEqualTo(1);
 }
 @Test void invoiceIsConditionalAndImmutable(){
  db.update("UPDATE branches SET invoice_required=TRUE WHERE id=1");
  var receipt=service.checkout(a,"invoice-1",sale(1,"110"));
  db.update("UPDATE tenants SET name='Renamed' WHERE id=1");
  assertThat(db.queryForObject("SELECT seller_name FROM vendor_invoices",String.class)).isEqualTo("Vendor A");
  assertThat(((List<?>)receipt.get("invoices"))).hasSize(1);
  service.checkout(a,"invoice-1",sale(1,"110"));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_invoices",Integer.class)).isEqualTo(1);
 }
 @Test void concurrentSalesCannotOversell() throws Exception {
  try(var pool=Executors.newFixedThreadPool(2)) {
   var jobs=List.of((Callable<Boolean>)()->attempt("concur-01"),()->attempt("concur-02"));
   var results=pool.invokeAll(jobs);int successes=0;for(var result:results) if(result.get())successes++;
   assertThat(successes).isEqualTo(1);
  }
 }
 boolean attempt(String key){try{service.checkout(a,key,sale(1,"110"));return true;}catch(org.springframework.web.server.ResponseStatusException e){return false;}}
 @Test void registerAndSessionScopeAreEnforced(){
  assertThatThrownBy(()->service.open(a,new PosService.OpenSession(1,BigDecimal.ZERO)));
  assertThatThrownBy(()->service.checkout(b,"bad-sess1",sale(2,"110")));
  assertThatThrownBy(()->service.close(a,session,BigDecimal.ZERO));
  service.close(a,session,new BigDecimal("50"));
  assertThatThrownBy(()->service.checkout(a,"closed-01",sale(1,"110")));
 }
 @Test void authenticationPermissionsCsrfAndForgedFields() throws Exception {
  mvc.perform(get("/api/vendor/pos/products")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/vendor/pos/products").with(user("alice"))).andExpect(status().isForbidden());
  mvc.perform(post("/api/vendor/pos/sales").with(user("alice").authorities(()->"POS_SALE_CREATE"))).andExpect(status().isForbidden());
  mvc.perform(post("/api/vendor/pos/sales").with(csrf()).with(user("alice").authorities(()->"POS_SALE_CREATE",()->"POS_TENDER_CASH_RECORD",()->"POS_RECEIPT_VIEW"))
   .header("Idempotency-Key","forged-01").contentType("application/json").content("{\"tenantId\":2,\"sessionId\":\"x\",\"items\":[{\"productId\":1,\"quantity\":1}],\"cashReceived\":110}"))
   .andExpect(status().isBadRequest());
 }
 @Test void httpCheckoutUsesAuthenticatedMembership() throws Exception {
  String payload="{\"sessionId\":\""+session+"\",\"items\":[{\"productId\":1,\"quantity\":1}],\"cashReceived\":120,\"invoiceRequested\":true}";
  mvc.perform(post("/api/vendor/pos/sales").with(csrf()).with(user("alice").authorities(()->"POS_SALE_CREATE",()->"POS_TENDER_CASH_RECORD",()->"POS_RECEIPT_VIEW"))
   .header("Idempotency-Key","http-sale1").contentType("application/json").content(payload))
   .andExpect(status().isOk()).andExpect(jsonPath("$.customer_id").doesNotExist()).andExpect(jsonPath("$.total").value(110)).andExpect(jsonPath("$.invoices[0].seller_name").value("Vendor A"));
 }
 @Test void databaseRejectsMixedTenantReferences(){
  assertThatThrownBy(()->db.update("INSERT INTO inventory(tenant_id,branch_id,product_id,quantity) VALUES(1,1,2,10)"))
   .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->db.update("INSERT INTO pos_sessions(id,tenant_id,branch_id,cashier,register_id,state,opening_float) VALUES('forged',1,1,'bob',1,'OPEN',0)"))
   .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
 }
 PosService.CashMovement movement(PosService.MovementType type,String amount,String reason) {
  return new PosService.CashMovement(type,new BigDecimal(amount),reason);
 }
 @Test void drawerFormulaIncludesMovementsWithoutDoubleCountingSales(){
  service.checkout(a,"drawer-sale",sale(1,"120"));
  var cashIn=movement(PosService.MovementType.CASH_IN,"100"," Extra float ");
  var first=service.cashMovement(a,session,"drawer-in1",cashIn);
  assertThat(service.cashMovement(a,session,"drawer-in1",cashIn).get("id")).isEqualTo(first.get("id"));
  service.cashMovement(a,session,"drawer-out",movement(PosService.MovementType.CASH_OUT,"25","Supplies"));
  service.cashMovement(a,session,"drawer-drop",movement(PosService.MovementType.SAFE_DROP,"100","Move to safe"));
  var report=service.reconciliation(a,session);
  assertThat(report.cashReceived()).isEqualByComparingTo("120");
  assertThat(report.changeGiven()).isEqualByComparingTo("10");
  assertThat(report.cashIn()).isEqualByComparingTo("100");
  assertThat(report.cashOut()).isEqualByComparingTo("25");
  assertThat(report.safeDrops()).isEqualByComparingTo("100");
  assertThat(report.expectedCash()).isEqualByComparingTo("135");
  assertThat(report.countedCash()).isNull();assertThat(report.variance()).isNull();
  assertThat(report.movements()).hasSize(3);
  assertThatThrownBy(()->service.close(a,session,new BigDecimal("160")));
  assertThat(service.reconciliation(a,session).state()).isEqualTo("OPEN");
  service.close(a,session,new BigDecimal("135"));
  assertThat(service.reconciliation(a,session).variance()).isEqualByComparingTo("0");
  assertThat(service.cashMovement(a,session,"drawer-in1",cashIn).get("id")).isEqualTo(first.get("id"));
  assertThatThrownBy(()->service.cashMovement(a,session,"afterclose",cashIn));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE action='CASH_IN'",Integer.class)).isEqualTo(1);
 }
 @Test void cashMovementsRejectPayloadReuseAndOtherCashiers(){
  service.cashMovement(a,session,"movement-1",movement(PosService.MovementType.CASH_IN,"5","Float"));
  assertThatThrownBy(()->service.cashMovement(a,session,"movement-1",movement(PosService.MovementType.CASH_IN,"6","Float")));
  assertThatThrownBy(()->service.cashMovement(a,session,"movement-1",movement(PosService.MovementType.CASH_OUT,"5","Float")));
  assertThatThrownBy(()->service.cashMovement(a,session,"movement-1",movement(PosService.MovementType.CASH_IN,"5","Changed reason")));
  assertThatThrownBy(()->service.cashMovement(b,session,"other-tenant",movement(PosService.MovementType.CASH_IN,"5","Float")));
  db.update("INSERT INTO employees VALUES('charlie','unused',1,1,TRUE)");
  var colleague=new PosService.Scope(1,1,"charlie");
  assertThatThrownBy(()->service.cashMovement(colleague,session,"other-user",movement(PosService.MovementType.CASH_IN,"5","Float")));
  assertThatThrownBy(()->service.reconciliation(colleague,session));
  assertThatThrownBy(()->service.reconciliation(b,session));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_cash_movements",Integer.class)).isEqualTo(1);
 }
 @Test void concurrentCashRemovalsCannotOverdraw() throws Exception {
  try(var pool=Executors.newFixedThreadPool(2)) {
   var results=pool.invokeAll(List.of((Callable<Boolean>)()->removeCash("remove-01"),()->removeCash("remove-02")));
   int successes=0;for(var result:results)if(result.get())successes++;
   assertThat(successes).isEqualTo(1);
  }
  assertThat(service.reconciliation(a,session).expectedCash()).isEqualByComparingTo("10");
 }
 boolean removeCash(String key) {
  try {service.cashMovement(a,session,key,movement(PosService.MovementType.CASH_OUT,"40","Supplies"));return true;}
  catch(org.springframework.web.server.ResponseStatusException e){return false;}
 }
 @Test void concurrentMovementRetriesHaveOneLedgerAndAuditEntry() throws Exception {
  try(var pool=Executors.newFixedThreadPool(2)) {
   Callable<Map<String,Object>> call=()->service.cashMovement(a,session,"retry-move",movement(PosService.MovementType.CASH_IN,"10","Float"));
   var results=pool.invokeAll(List.of(call,call));
   assertThat(results.get(0).get().get("id")).isEqualTo(results.get(1).get().get("id"));
  }
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_cash_movements",Integer.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE action='CASH_IN'",Integer.class)).isEqualTo(1);
 }
 @Test void movementEndpointsEnforceTypeSpecificPermissionsAndScope() throws Exception {
  String path="/api/vendor/pos/sessions/"+session+"/cash-movements";
  String body="{\"type\":\"CASH_OUT\",\"amount\":10,\"reason\":\"Supplies\"}";
  mvc.perform(post(path).with(csrf()).contentType("application/json").content(body).header("Idempotency-Key","http-move1")).andExpect(status().isUnauthorized());
  mvc.perform(post(path).with(user("alice").authorities(()->"POS_CASH_OUT")).contentType("application/json").content(body).header("Idempotency-Key","http-move1")).andExpect(status().isForbidden());
  mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"POS_CASH_IN")).contentType("application/json").content(body).header("Idempotency-Key","http-move1")).andExpect(status().isForbidden());
  mvc.perform(post(path).with(csrf()).with(user("bob").authorities(()->"POS_CASH_OUT")).contentType("application/json").content(body).header("Idempotency-Key","http-move1")).andExpect(status().isNotFound());
  mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"POS_CASH_OUT")).contentType("application/json").content(body).header("Idempotency-Key","http-move1"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.cashier").value("alice")).andExpect(jsonPath("$.movement_type").value("CASH_OUT"));
  String report="/api/vendor/pos/sessions/"+session+"/reconciliation";
  mvc.perform(get(report).with(user("alice"))).andExpect(status().isForbidden());
  mvc.perform(get(report).with(user("bob").authorities(()->"POS_VIEW"))).andExpect(status().isNotFound());
  mvc.perform(get(report).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.expectedCash").value(40));
 }
 @Test void invalidCashMovementInputsAreRejected() throws Exception {
  String path="/api/vendor/pos/sessions/"+session+"/cash-movements";
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
  assertThatThrownBy(()->db.update(sql,"foreign",2,2,"bob",session,"CASH_IN",1,"Float","db-foreign"))
   .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->db.update(sql,"zero",1,1,"alice",session,"CASH_IN",0,"Float","db-zero1"))
   .isInstanceOf(org.springframework.dao.DataAccessException.class)
   .rootCause().hasMessageContaining("chk_cash_movement_amount");
  assertThatThrownBy(()->db.update(sql,"refund",1,1,"alice",session,"CASH_REFUND",1,"Float","db-refund"))
   .isInstanceOf(org.springframework.dao.DataAccessException.class)
   .rootCause().hasMessageContaining("chk_cash_movement_type");
 }
 @Test void sessionDiscoveryAndResumeAreRestrictedToTheOwningCashier(){
  String bobSession=(String)service.open(b,new PosService.OpenSession(2,BigDecimal.ZERO)).get("id");
  db.update("INSERT INTO employees VALUES('charlie','unused',1,1,TRUE)");
  db.update("INSERT INTO registers VALUES(3,1,1)");
  var charlie=new PosService.Scope(1,1,"charlie");
  String colleagueSession=(String)service.open(charlie,new PosService.OpenSession(3,BigDecimal.ZERO)).get("id");
  assertThat(service.activeSessions(a)).extracting(PosService.SessionSummary::id).containsExactly(session);
  assertThat(service.activeSessions(b)).extracting(PosService.SessionSummary::id).containsExactly(bobSession);
  assertThatThrownBy(()->service.sessionDetails(a,bobSession));
  assertThatThrownBy(()->service.sessionDetails(a,colleagueSession));
  assertThatThrownBy(()->service.resume(a,bobSession));
  assertThatThrownBy(()->service.resume(a,colleagueSession));
  assertThatThrownBy(()->service.resume(new PosService.Scope(1,2,"alice"),session));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE action='SESSION_RESUME'",Integer.class)).isZero();
 }
 @Test void resumePreservesOriginalFloatAndFinancialHistory(){
  service.checkout(a,"resume-sale",sale(1,"120"));
  service.cashMovement(a,session,"resume-cash",movement(PosService.MovementType.CASH_IN,"10","Float"));
  var before=service.reconciliation(a,session);
  var resumed=service.resume(a,session);
  assertThat(resumed.id()).isEqualTo(session);
  assertThat(resumed.registerId()).isEqualTo(1);
  assertThat(resumed.openingFloat()).isEqualByComparingTo("50");
  service.resume(a,session);
  assertThat(service.reconciliation(a,session)).isEqualTo(before);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_sessions",Integer.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE action='SESSION_RESUME'",Integer.class)).isEqualTo(2);
  service.close(a,session,new BigDecimal("170"));
  assertThat(service.activeSessions(a)).isEmpty();
  assertThat(service.sessionDetails(a,session).state()).isEqualTo("CLOSED");
  assertThatThrownBy(()->service.resume(a,session));
  assertThat(service.sessionDetails(a,session).state()).isEqualTo("CLOSED");
 }
 @Test void sessionRecoveryHttpPermissionsAndForgedBodyAreEnforced() throws Exception {
  String path="/api/vendor/pos/sessions/"+session;
  mvc.perform(get("/api/vendor/pos/sessions/active")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/vendor/pos/sessions/active").with(user("alice"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/vendor/pos/sessions/active").with(user("bob").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
  mvc.perform(get("/api/vendor/pos/sessions/active").with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].registerId").value(1));
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
  service.close(a,session,new BigDecimal("50"));
  mvc.perform(post(path+"/resume").with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_SESSION_OPEN")).contentType("application/json").content("{}"))
   .andExpect(status().isConflict());
 }
 @Test void resumeRacingCloseCannotReopenTheRegister() throws Exception {
  try(var pool=Executors.newFixedThreadPool(2)) {
   Callable<Boolean> resume=()->{try{service.resume(a,session);return true;}catch(org.springframework.web.server.ResponseStatusException e){return false;}};
   Callable<Boolean> close=()->{service.close(a,session,new BigDecimal("50"));return true;};
   for(var result:pool.invokeAll(List.of(resume,close)))result.get();
  }
  assertThat(service.sessionDetails(a,session).state()).isEqualTo("CLOSED");
  assertThat(service.activeSessions(a)).isEmpty();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_sessions",Integer.class)).isEqualTo(1);
 }
 PosService.Scope supervisor(String name) {
  db.update("INSERT INTO employees VALUES(?,'unused',1,1,TRUE)",name);
  return new PosService.Scope(1,1,name);
 }
 @Test void discrepancyRequiresReasonAndFreezesTheCountAndRegister(){
  assertThatThrownBy(()->service.close(a,session,BigDecimal.ZERO," "));
  assertThat(service.closeStatus(a,session).state()).isEqualTo("OPEN");
  var closing=service.close(a,session,BigDecimal.ZERO,"Drawer shortage");
  assertThat(closing.state()).isEqualTo("CLOSING");assertThat(closing.variance()).isEqualByComparingTo("-50");
  assertThat(service.close(a,session,BigDecimal.ZERO,"Drawer shortage").requestId()).isEqualTo(closing.requestId());
  assertThatThrownBy(()->service.close(a,session,new BigDecimal("50"),"Changed count"));
  assertThatThrownBy(()->service.checkout(a,"frozen-sale",sale(1,"110")));
  assertThatThrownBy(()->service.cashMovement(a,session,"frozen-cash",movement(PosService.MovementType.CASH_IN,"1","Float")));
  assertThatThrownBy(()->service.open(a,new PosService.OpenSession(1,BigDecimal.ZERO)));
  assertThatThrownBy(()->db.update("INSERT INTO pos_sessions(id,tenant_id,branch_id,cashier,register_id,state,opening_float) VALUES('occupied',1,1,'alice',1,'OPEN',0)"))
   .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThat(service.resume(a,session).state()).isEqualTo("CLOSING");
 }
 @Test void separateSupervisorApprovesVarianceExactlyOnce(){
  var manager=supervisor("manager");
  var closing=service.close(a,session,BigDecimal.ZERO,"Shortage");
  assertThatThrownBy(()->service.reviewVariance(a,session,closing.requestId(),"Self approval",PosService.ReviewAction.APPROVE));
  assertThatThrownBy(()->service.reviewVariance(b,session,closing.requestId(),"Wrong tenant",PosService.ReviewAction.APPROVE));
  assertThatThrownBy(()->service.reviewVariance(new PosService.Scope(1,2,"manager"),session,closing.requestId(),"Wrong branch",PosService.ReviewAction.APPROVE));
  var approved=service.reviewVariance(manager,session,closing.requestId(),"Count and drawer reviewed",PosService.ReviewAction.APPROVE);
  assertThat(service.reviewVariance(manager,session,closing.requestId(),"Count and drawer reviewed",PosService.ReviewAction.APPROVE).get("id")).isEqualTo(approved.get("id"));
  assertThat(service.closeStatus(a,session).state()).isEqualTo("CLOSED");
  assertThat(service.closeStatus(a,session).reviewedBy()).isEqualTo("manager");
  assertThat(service.closeStatus(a,session).variance()).isEqualByComparingTo("-50");
  assertThatThrownBy(()->service.reviewVariance(manager,session,closing.requestId(),"Changed",PosService.ReviewAction.APPROVE));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_shift_reviews",Integer.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE action='SHIFT_VARIANCE_APPROVE'",Integer.class)).isEqualTo(1);
  service.open(a,new PosService.OpenSession(1,BigDecimal.ZERO));
 }
 @Test void returnedCountRetainsHistoryAndOldRetriesCannotResubmitIt(){
  var manager=supervisor("manager");String key=UUID.randomUUID().toString();
  var closing=service.close(a,session,BigDecimal.ZERO,"Need recount",key);
  service.reviewVariance(manager,session,key,"Recount the drawer",PosService.ReviewAction.RETURN_FOR_CORRECTION);
  assertThat(service.closeStatus(a,session).state()).isEqualTo("OPEN");
  assertThat(service.closeStatus(a,session).reviewReason()).isEqualTo("Recount the drawer");
  assertThat(service.close(a,session,BigDecimal.ZERO,"Need recount",key).state()).isEqualTo("OPEN");
  assertThatThrownBy(()->service.close(a,session,BigDecimal.ONE,"Need recount",key));
  var corrected=service.close(a,session,new BigDecimal("45"),"Corrected count",UUID.randomUUID().toString());
  assertThat(corrected.requestId()).isNotEqualTo(key);
  assertThatThrownBy(()->service.reviewVariance(manager,session,key,"Old request",PosService.ReviewAction.APPROVE));
  service.reviewVariance(manager,session,corrected.requestId(),"Verified recount",PosService.ReviewAction.APPROVE);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_shift_reviews",Integer.class)).isEqualTo(2);
  assertThat(db.queryForObject("SELECT counted_cash FROM pos_shift_reviews WHERE close_request_id=?",BigDecimal.class,closing.requestId())).isEqualByComparingTo("0");
  assertThat(service.closeStatus(a,session).countedCash()).isEqualByComparingTo("45");
 }
 @Test void competingReviewDecisionsCannotBothCommit() throws Exception {
  var m1=supervisor("m1");var m2=supervisor("m2");var closing=service.close(a,session,BigDecimal.ZERO,"Shortage");
  try(var pool=Executors.newFixedThreadPool(2)) {
   var jobs=List.of((Callable<Boolean>)()->reviewAttempt(m1,closing.requestId(),PosService.ReviewAction.APPROVE),()->reviewAttempt(m2,closing.requestId(),PosService.ReviewAction.RETURN_FOR_CORRECTION));
   int count=0;for(var result:pool.invokeAll(jobs))if(result.get())count++;
   assertThat(count).isEqualTo(1);
  }
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_shift_reviews",Integer.class)).isEqualTo(1);
 }
 boolean reviewAttempt(PosService.Scope manager,String key,PosService.ReviewAction action) {
  try{service.reviewVariance(manager,session,key,"Reviewed",action);return true;}catch(org.springframework.web.server.ResponseStatusException e){return false;}
 }
 @Test void reviewHttpAuthorizationCsrfAndCanonicalIdentityAreEnforced() throws Exception {
  supervisor("manager");var closing=service.close(a,session,BigDecimal.ZERO,"Shortage");
  String list="/api/vendor/pos/sessions/pending-variances",path="/api/vendor/pos/sessions/"+session+"/approve-variance";
  String body="{\"requestId\":\""+closing.requestId()+"\",\"reason\":\"Reviewed drawer\"}";
  mvc.perform(get(list)).andExpect(status().isUnauthorized());
  mvc.perform(get(list).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isForbidden());
  mvc.perform(get(list).with(user("bob").authorities(()->"POS_SHIFT_VARIANCE_APPROVE"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
  mvc.perform(get(list).with(user("manager").authorities(()->"POS_SHIFT_VARIANCE_APPROVE"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].variance").value(-50));
  mvc.perform(post(path).with(user("manager").authorities(()->"POS_SHIFT_VARIANCE_APPROVE")).contentType("application/json").content(body)).andExpect(status().isForbidden());
  mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"POS_SHIFT_VARIANCE_APPROVE")).contentType("application/json").content(body)).andExpect(status().isForbidden());
  mvc.perform(post(path).with(csrf()).with(user("ALICE").authorities(()->"POS_SHIFT_VARIANCE_APPROVE")).contentType("application/json").content(body)).andExpect(status().isForbidden());
  mvc.perform(post(path).with(csrf()).with(user("bob").authorities(()->"POS_SHIFT_VARIANCE_APPROVE")).contentType("application/json").content(body)).andExpect(status().isNotFound());
  mvc.perform(post(path).with(csrf()).with(user("manager").authorities(()->"POS_VIEW")).contentType("application/json").content(body)).andExpect(status().isForbidden());
  mvc.perform(post(path).with(csrf()).with(user("manager").authorities(()->"POS_SHIFT_VARIANCE_APPROVE")).contentType("application/json").content("{\"requestId\":\"x\",\"reason\":\" \"}")).andExpect(status().isBadRequest());
  mvc.perform(post(path).with(csrf()).with(user("manager").authorities(()->"POS_SHIFT_VARIANCE_APPROVE")).contentType("application/json").content(body)).andExpect(status().isOk());
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
  org.flywaydb.core.Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").load().migrate();
  assertThat(legacy.queryForObject("SELECT cash_variance FROM pos_sessions",BigDecimal.class)).isEqualByComparingTo("0");
  assertThat(legacy.queryForObject("SELECT close_request_id FROM pos_sessions",String.class)).isEqualTo("legacy");
  assertThat(legacy.queryForObject("SELECT state FROM pos_sessions",String.class)).isEqualTo("CLOSED");
 }
 @Test void httpCloseRequiresVarianceReasonAndPreservesClientRetryIdentity() throws Exception {
  String path="/api/vendor/pos/sessions/"+session+"/close",key=UUID.randomUUID().toString();
  mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"POS_SESSION_CLOSE")).contentType("application/json").content("{\"countedCash\":0}"))
   .andExpect(status().isBadRequest());
  String body="{\"countedCash\":0,\"reason\":\"Shortage\",\"requestId\":\""+key+"\"}";
  for(int i=0;i<2;i++)mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"POS_SESSION_CLOSE")).contentType("application/json").content(body))
   .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CLOSING")).andExpect(jsonPath("$.requestId").value(key)).andExpect(jsonPath("$.variance").value(-50));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE action='SHIFT_VARIANCE_SUBMIT'",Integer.class)).isEqualTo(1);
 }
 void customers(){
  db.update("UPDATE tenants SET slug=CONCAT('vendor-',id)");
  db.update("INSERT INTO users VALUES(100,'customer',?,TRUE),(101,'other-customer',?,TRUE)",encoder.encode("CustomerPassword"),encoder.encode("OtherPassword"));
  db.update("INSERT INTO customer_profiles VALUES(100,100),(101,101)");
 }
 @Test void anonymousMarketplaceExposesOnlyActivePublishedCatalogue() throws Exception {
  customers();db.update("INSERT INTO products VALUES(3,1,'SECRET',NULL,'Private draft',1,0,FALSE)");
  mvc.perform(get("/api/marketplace/vendors/vendor-1/products")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].name").value("Panel"));
  db.update("UPDATE tenants SET active=FALSE WHERE id=2");
  mvc.perform(get("/api/marketplace/vendors")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
  mvc.perform(get("/api/marketplace/vendors/vendor-2/products")).andExpect(status().isNotFound());
 }
 @Test void singleVendorCartSwitchIsExplicitScopedAndRetainsOldItems(){
  customers();var cart=marketplace.put(100,new CartService.Item("vendor-1",1,1));
  assertThatThrownBy(()->marketplace.put(100,new CartService.Item("vendor-2",2,1)));
  assertThat(marketplace.cart(100).vendorSlug()).isEqualTo("vendor-1");
  assertThatThrownBy(()->marketplace.switchVendor(100,new CartService.Switch(cart.id(),"vendor-2",1,1)));
  assertThat(marketplace.cart(100).id()).isEqualTo(cart.id());
  var switched=marketplace.switchVendor(100,new CartService.Switch(cart.id(),"vendor-2",2,1));
  assertThat(switched.vendorSlug()).isEqualTo("vendor-2");
  assertThat(db.queryForObject("SELECT state FROM carts WHERE id=?",String.class,cart.id())).isEqualTo("ARCHIVED");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM cart_items WHERE cart_id=?",Integer.class,cart.id())).isEqualTo(1);
  assertThatThrownBy(()->marketplace.switchVendor(100,new CartService.Switch(cart.id(),"vendor-1",1,1)));
  assertThat(marketplace.cart(101).items()).isEmpty();
  assertThatThrownBy(()->marketplace.switchVendor(101,new CartService.Switch(switched.id(),"vendor-1",1,1)));
 }
 @Test void marketplaceAndPosSharePricingAndCurrentStock(){
  customers();var cart=marketplace.put(100,new CartService.Item("vendor-1",1,1));
  assertThat(cart.total()).isEqualByComparingTo("110");
  assertThat(marketplace.quote(100).checkoutAvailable()).isFalse();
  var receipt=service.checkout(a,"shared-stock",sale(1,"120"));
  assertThat((BigDecimal)receipt.get("total")).isEqualByComparingTo(cart.total());
  assertThat(catalogue.products("vendor-1").getFirst().inStock()).isFalse();
  assertThatThrownBy(()->marketplace.quote(100));
  assertThat(marketplace.cart(100).items().getFirst().available()).isFalse();
  db.update("UPDATE inventory SET quantity=5 WHERE tenant_id=1");
  db.update("UPDATE products SET price=200,tax_rate=5 WHERE id=1");
  assertThat(marketplace.quote(100).total()).isEqualByComparingTo("210");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);
  db.update("UPDATE products SET published=FALSE WHERE id=1");
  assertThat(marketplace.cart(100).items().getFirst().name()).isEqualTo("Product unavailable");
  assertThat(marketplace.cart(100).items().getFirst().unitPrice()).isNull();
  assertThatThrownBy(()->marketplace.quote(100));
 }
 @Test void customerAuthenticationPermissionsAndCsrfProtectCart() throws Exception {
  customers();String path="/api/customer/cart/items",body="{\"vendorSlug\":\"vendor-1\",\"productId\":1,\"quantity\":1}";
  mvc.perform(get("/api/customer/cart")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/customer/cart").with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/customer/cart").with(user("unknown").authorities(()->"CUSTOMER"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/customer/cart").with(httpBasic("customer","CustomerPassword"))).andExpect(status().isOk());
  mvc.perform(get("/api/vendor/pos/products").with(httpBasic("customer","CustomerPassword"))).andExpect(status().isForbidden());
  mvc.perform(post(path).with(httpBasic("customer","CustomerPassword")).contentType("application/json").content(body)).andExpect(status().isForbidden());
  mvc.perform(post(path).with(csrf()).with(httpBasic("customer","CustomerPassword")).contentType("application/json").content(body)).andExpect(status().isOk());
  mvc.perform(get("/api/customer/cart").with(httpBasic("other-customer","OtherPassword"))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
  mvc.perform(post(path).with(csrf()).with(httpBasic("customer","CustomerPassword")).contentType("application/json").content(body.replace("\"quantity\":1","\"quantity\":1,\"customerId\":101"))).andExpect(status().isBadRequest());
 }
 @Test void cartQuantityUpdatesAreIdempotentAndDatabaseRejectsMixedTenantItems(){
  customers();var request=new CartService.Item("vendor-1",1,2);var cart=marketplace.put(100,request);
  marketplace.put(100,request);
  assertThat(marketplace.cart(100).items().getFirst().quantity()).isEqualTo(2);
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isEqualTo(1);
  assertThatThrownBy(()->db.update("INSERT INTO cart_items VALUES(1,?,2,1)",cart.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThat(marketplace.remove(100,1).items()).isEmpty();
  assertThat(marketplace.put(100,new CartService.Item("vendor-2",2,1)).vendorSlug()).isEqualTo("vendor-2");
 }
 @Test void concurrentDifferentVendorAddsCannotCreateMixedActiveCarts() throws Exception {
  customers();try(var pool=Executors.newFixedThreadPool(2)) {
   Callable<Boolean> first=()->cartAttempt("vendor-1",1),second=()->cartAttempt("vendor-2",2);
   int successes=0;for(var result:pool.invokeAll(List.of(first,second)))if(result.get())successes++;
   assertThat(successes).isEqualTo(1);
  }
  assertThat(db.queryForObject("SELECT COUNT(*) FROM carts WHERE state='ACTIVE'",Integer.class)).isEqualTo(1);
  assertThat(marketplace.cart(100).items()).hasSize(1);
 }
 boolean cartAttempt(String slug,long product){try{marketplace.put(100,new CartService.Item(slug,product,1));return true;}catch(org.springframework.web.server.ResponseStatusException e){return false;}}
 @Test void conflictingLegacyStaffAndCustomerLoginsFailClosed() throws Exception {
  customers();db.update("INSERT INTO users VALUES(102,'alice',?,TRUE)",encoder.encode("CustomerPassword"));db.update("INSERT INTO customer_profiles VALUES(102,102)");
  mvc.perform(get("/api/customer/cart").with(httpBasic("alice","CustomerPassword"))).andExpect(status().isUnauthorized());
  db.update("UPDATE users SET enabled=FALSE WHERE username='customer'");
  mvc.perform(get("/api/customer/cart").with(httpBasic("customer","CustomerPassword"))).andExpect(status().isUnauthorized());
 }
 @Test void archivedCartRestorePreservesBothVendorCartsAndScopesOwnership(){
  customers();var first=marketplace.put(100,new CartService.Item("vendor-1",1,1));
  var second=marketplace.switchVendor(100,new CartService.Switch(first.id(),"vendor-2",2,1));
  assertThat(marketplace.history(100)).extracting(CartService.Cart::id).containsExactly(first.id());
  assertThat(marketplace.history(101)).isEmpty();
  assertThatThrownBy(()->marketplace.restore(101,new CartService.Restore(first.id(),null)));
  assertThatThrownBy(()->marketplace.restore(100,new CartService.Restore(first.id(),first.id())));
  var restored=marketplace.restore(100,new CartService.Restore(first.id(),second.id()));
  assertThat(restored.id()).isEqualTo(first.id());assertThat(restored.items().getFirst().productId()).isEqualTo(1);
  assertThat(marketplace.history(100)).extracting(CartService.Cart::id).containsExactly(second.id());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM cart_items",Integer.class)).isEqualTo(2);
  assertThat(db.queryForObject("SELECT version FROM carts WHERE id=?",Long.class,first.id())).isGreaterThan(0);
  assertThatThrownBy(()->marketplace.restore(100,new CartService.Restore(first.id(),second.id())));
 }
 @Test void invalidArchivedCartCannotReplaceActiveCart(){
  customers();var first=marketplace.put(100,new CartService.Item("vendor-1",1,1));
  var second=marketplace.switchVendor(100,new CartService.Switch(first.id(),"vendor-2",2,1));
  db.update("UPDATE products SET published=FALSE WHERE id=1");
  assertThatThrownBy(()->marketplace.restore(100,new CartService.Restore(first.id(),second.id())));
  assertThat(marketplace.cart(100).id()).isEqualTo(second.id());
  db.update("UPDATE products SET published=TRUE WHERE id=1");db.update("UPDATE tenants SET active=FALSE WHERE id=1");
  assertThatThrownBy(()->marketplace.restore(100,new CartService.Restore(first.id(),second.id())));
  assertThat(marketplace.cart(100).id()).isEqualTo(second.id());
 }
 @Test void jpaStockAndJdbcFinancialWritesShareOneRollbackBoundary(){
  assertThat(transactions).isInstanceOf(org.springframework.orm.jpa.JpaTransactionManager.class);
  assertThatThrownBy(()->new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->{
   stock.issue(1,1,1,1);
   assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isZero();
   db.update("INSERT INTO pos_audit(id,tenant_id,actor,action,resource_id) VALUES('rollback',1,'alice','ROLLBACK_TEST','test')");
   throw new IllegalStateException("Simulate failure after stock and financial work");
  })).isInstanceOf(IllegalStateException.class);
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE id='rollback'",Integer.class)).isZero();
 }
 @Test void archivedCartHttpPermissionsAndCsrfAreEnforced() throws Exception {
  customers();var first=marketplace.put(100,new CartService.Item("vendor-1",1,1));
  var second=marketplace.switchVendor(100,new CartService.Switch(first.id(),"vendor-2",2,1));
  mvc.perform(get("/api/customer/cart/history")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/customer/cart/history").with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/customer/cart/history").with(user("other-customer").authorities(()->"CUSTOMER"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
  String payload="{\"archivedCartId\":\""+first.id()+"\",\"expectedCartId\":\""+second.id()+"\"}";
  mvc.perform(post("/api/customer/cart/restore").with(user("customer").authorities(()->"CUSTOMER")).contentType("application/json").content(payload)).andExpect(status().isForbidden());
  mvc.perform(post("/api/customer/cart/restore").with(csrf()).with(user("customer").authorities(()->"CUSTOMER")).contentType("application/json").content(payload)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(first.id()));
 }
 @Test void concurrentRestoresCannotReplaceANewerActiveCart() throws Exception {
  customers();var first=marketplace.put(100,new CartService.Item("vendor-1",1,1));
  var second=marketplace.switchVendor(100,new CartService.Switch(first.id(),"vendor-2",2,1));
  var current=marketplace.switchVendor(100,new CartService.Switch(second.id(),"vendor-1",1,1));
  try(var pool=Executors.newFixedThreadPool(2)) {
   Callable<Boolean> a=()->restoreAttempt(first.id(),current.id()),b=()->restoreAttempt(second.id(),current.id());
   int count=0;for(var result:pool.invokeAll(List.of(a,b)))if(result.get())count++;
   assertThat(count).isEqualTo(1);
  }
  assertThat(db.queryForObject("SELECT COUNT(*) FROM carts WHERE state='ACTIVE'",Integer.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM cart_items",Integer.class)).isEqualTo(3);
 }
 boolean restoreAttempt(String archived,String current){try{marketplace.restore(100,new CartService.Restore(archived,current));return true;}catch(org.springframework.web.server.ResponseStatusException e){return false;}}
 com.saumya.pos.checkout.CheckoutService.PosDraft posDraft(){return new com.saumya.pos.checkout.CheckoutService.PosDraft(session,List.of(new com.saumya.pos.checkout.CheckoutService.Item(1,1)),true);}
 com.saumya.pos.checkout.CheckoutService.Draft onlineDraft(String key){
  var cart=marketplace.put(100,new CartService.Item("vendor-1",1,1));
  return checkout.online("customer",key,new com.saumya.pos.checkout.CheckoutService.OnlineDraft(cart.id()));
 }
 void configureOnline(){customers();db.update("UPDATE tenants SET online_branch_id=id");}
 @Test void onlineDraftReservesCanonicalStockAndCancelReleasesExactlyOnce(){
  configureOnline();var draft=onlineDraft("online-hold1");
  assertThat(draft.channel()).isEqualTo("ONLINE");assertThat(draft.status()).isEqualTo("DRAFT");assertThat(draft.paymentAvailable()).isFalse();
  assertThat(db.queryForObject("SELECT customer_id FROM orders",Long.class)).isEqualTo(100);
  assertThat(db.queryForMap("SELECT branch_id,cashier,session_id,cash_received,receipt_number FROM orders").values()).allMatch(Objects::isNull);
  assertThat(stock.available(1,1)).isZero();assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isEqualTo(1);
  assertThatThrownBy(()->service.checkout(a,"competing-pos",sale(1,"110")));
  var cart=marketplace.cart(100);
  assertThat(checkout.online("customer","online-hold1",new com.saumya.pos.checkout.CheckoutService.OnlineDraft(cart.id())).id()).isEqualTo(draft.id());
  assertThatThrownBy(()->checkout.online("customer","online-new1",new com.saumya.pos.checkout.CheckoutService.OnlineDraft(cart.id())));
  assertThatThrownBy(()->checkout.cancelOnline("other-customer",draft.id(),"Not mine"));
  checkout.cancelOnline("customer",draft.id(),"Changed mind");checkout.cancelOnline("customer",draft.id(),"Retry");
  assertThat(stock.available(1,1)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT reserved FROM inventory WHERE tenant_id=1",Integer.class)).isZero();
  service.checkout(a,"after-release",sale(1,"110"));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM orders WHERE status='PAID'",Integer.class)).isEqualTo(1);
 }
 @Test void posReservedCashFinalizationKeepsOneOrderReceiptAndInvoice(){
  var draft=checkout.pos("alice","pos-reserve1",posDraft());
  assertThat(checkout.pos("alice","pos-reserve1",posDraft()).id()).isEqualTo(draft.id());
  assertThatThrownBy(()->service.close(a,session,new BigDecimal("50")));
  assertThatThrownBy(()->checkout.finalizeCash("alice",draft.id(),BigDecimal.ONE));
  assertThatThrownBy(()->checkout.finalizeCash("bob",draft.id(),new BigDecimal("120")));
  db.update("UPDATE products SET price=101 WHERE id=1");
  assertThatThrownBy(()->checkout.finalizeCash("alice",draft.id(),new BigDecimal("120")));
  db.update("UPDATE products SET price=100 WHERE id=1");
  var receipt=checkout.finalizeCash("alice",draft.id(),new BigDecimal("120"));
  assertThat(receipt.get("id")).isEqualTo(draft.id());assertThat(receipt.get("customer_id")).isNull();
  assertThat((BigDecimal)receipt.get("change_given")).isEqualByComparingTo("10");
  assertThat(checkout.finalizeCash("alice",draft.id(),new BigDecimal("120")).get("id")).isEqualTo(draft.id());
  service.close(a,session,new BigDecimal("160"));
  assertThat(checkout.finalizeCash("alice",draft.id(),new BigDecimal("120")).get("id")).isEqualTo(draft.id());
  assertThatThrownBy(()->checkout.finalizeCash("alice",draft.id(),new BigDecimal("130")));
  assertThatThrownBy(()->checkout.cancelPos("alice",draft.id(),"Already paid"));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_invoices",Integer.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_ledger",Integer.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT receipt_sequence FROM branches WHERE id=1",Long.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT reserved FROM inventory WHERE tenant_id=1",Integer.class)).isZero();
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isZero();
 }
 @Test void expiryReleasesHoldsWithoutPaymentAndIsIdempotent(){
  var draft=checkout.pos("alice","expire-pos1",posDraft());
  db.update("UPDATE orders SET expires_at=TIMESTAMP '2020-01-01 00:00:00' WHERE id=?",draft.id());
  assertThatThrownBy(()->checkout.finalizeCash("alice",draft.id(),new BigDecimal("110")));
  checkout.expire(1,draft.id());checkout.expire(1,draft.id());
  assertThat(stock.available(1,1)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT status FROM orders",String.class)).isEqualTo("EXPIRED");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_ledger",Integer.class)).isZero();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_invoices",Integer.class)).isZero();
  checkout.pos("alice","after-expiry",posDraft());
 }
 @Test void reservationFailureRollsBackEveryLineAndOrder(){
  db.update("INSERT INTO products VALUES(3,1,'P3',NULL,'Missing stock',100,0,TRUE)");
  var request=new com.saumya.pos.checkout.CheckoutService.PosDraft(session,List.of(new com.saumya.pos.checkout.CheckoutService.Item(1,1),new com.saumya.pos.checkout.CheckoutService.Item(3,1)),false);
  assertThatThrownBy(()->checkout.pos("alice","rollback-hold",request));
  assertThat(stock.available(1,1)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isZero();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM stock_reservations",Integer.class)).isZero();
 }
 @Test void onlineReservationRacingWalkInSaleCannotOversell() throws Exception {
  configureOnline();var cart=marketplace.put(100,new CartService.Item("vendor-1",1,1));
  try(var pool=Executors.newFixedThreadPool(2)){
   Callable<Boolean> online=()->{try{checkout.online("customer","race-online",new com.saumya.pos.checkout.CheckoutService.OnlineDraft(cart.id()));return true;}catch(org.springframework.web.server.ResponseStatusException e){return false;}};
   Callable<Boolean> pos=()->attempt("race-pos-sale");int won=0;
   for(var result:pool.invokeAll(List.of(online,pos)))if(result.get())won++;
   assertThat(won).isEqualTo(1);
  }
  assertThat(stock.available(1,1)).isZero();
  assertThat(db.queryForObject("SELECT quantity-reserved FROM inventory WHERE tenant_id=1",Integer.class)).isZero();
 }
 @Test void draftHttpSecurityOwnershipAndContextConstraints() throws Exception {
  configureOnline();var cart=marketplace.put(100,new CartService.Item("vendor-1",1,1));
  String payload="{\"expectedCartId\":\""+cart.id()+"\"}";
  mvc.perform(post("/api/customer/checkout/drafts").with(csrf()).contentType("application/json").content(payload).header("Idempotency-Key","http-draft1")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/customer/checkout/drafts").with(user("customer").authorities(()->"CUSTOMER")).contentType("application/json").content(payload).header("Idempotency-Key","http-draft1")).andExpect(status().isForbidden());
  mvc.perform(post("/api/customer/checkout/drafts").with(csrf()).with(user("customer").authorities(()->"CUSTOMER")).contentType("application/json").content(payload).header("Idempotency-Key","http-draft1")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.paymentAvailable").value(false));
  mvc.perform(get("/api/customer/checkout/drafts").with(user("other-customer").authorities(()->"CUSTOMER"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
  var draft=checkout.onlineList("customer").getFirst();
  mvc.perform(post("/api/customer/checkout/drafts/"+draft.id()+"/cancel").with(csrf()).with(user("other-customer").authorities(()->"CUSTOMER")).contentType("application/json").content("{\"reason\":\"Not mine\"}")).andExpect(status().isNotFound());
  assertThatThrownBy(()->db.update("UPDATE orders SET branch_id=1 WHERE id=?",draft.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->db.update("UPDATE inventory SET reserved=2 WHERE tenant_id=1")).isInstanceOf(org.springframework.dao.DataAccessException.class);
 }
 @Test void competingCashFinalizationAndCancellationCannotBothCommit() throws Exception {
  var draft=checkout.pos("alice","cash-cancel-race",posDraft());
  try(var pool=Executors.newFixedThreadPool(2)){
   Callable<Boolean> cash=()->{try{checkout.finalizeCash("alice",draft.id(),new BigDecimal("120"));return true;}catch(org.springframework.web.server.ResponseStatusException e){return false;}};
   Callable<Boolean> cancel=()->{try{checkout.cancelPos("alice",draft.id(),"Customer cancelled");return true;}catch(org.springframework.web.server.ResponseStatusException e){return false;}};
   int won=0;for(var result:pool.invokeAll(List.of(cash,cancel)))if(result.get())won++;assertThat(won).isEqualTo(1);
  }
  String status=db.queryForObject("SELECT status FROM orders",String.class);
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isEqualTo(status.equals("PAID")?0:1);
  assertThat(db.queryForObject("SELECT reserved FROM inventory WHERE tenant_id=1",Integer.class)).isZero();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_ledger",Integer.class)).isEqualTo(status.equals("PAID")?1:0);
 }
 @Test void concurrentReservedCashRetriesIssueOneReceipt() throws Exception {
  var draft=checkout.pos("alice","cash-retry-race",posDraft());
  try(var pool=Executors.newFixedThreadPool(2)){
   Callable<Map<String,Object>> pay=()->checkout.finalizeCash("alice",draft.id(),new BigDecimal("120"));
   for(var result:pool.invokeAll(List.of(pay,pay))){var receipt=result.get();assertThat(receipt.get("id")).isEqualTo(draft.id());assertThat((List<?>)receipt.get("invoices")).hasSize(1);assertThat((BigDecimal)receipt.get("cash_received")).isEqualByComparingTo("120");}
  }
  assertThat(db.queryForObject("SELECT receipt_sequence FROM branches WHERE id=1",Long.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_ledger",Integer.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_invoices",Integer.class)).isEqualTo(1);
 }
 @Test void posDraftEndpointEnforcesPermissionsAndRejectsForgedContext() throws Exception {
  String body="{\"sessionId\":\""+session+"\",\"items\":[{\"productId\":1,\"quantity\":1}]}";
  mvc.perform(post("/api/vendor/pos/drafts").with(csrf()).with(user("alice").authorities(()->"POS_VIEW")).header("Idempotency-Key","pos-http-key").contentType("application/json").content(body)).andExpect(status().isForbidden());
  mvc.perform(post("/api/vendor/pos/drafts").with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_SALE_CREATE")).header("Idempotency-Key","pos-http-key").contentType("application/json").content(body.replace("\"items\"","\"tenantId\":2,\"items\""))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/vendor/pos/drafts").with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_SALE_CREATE")).header("Idempotency-Key","pos-http-key").contentType("application/json").content(body)).andExpect(status().isOk());
  var draft=checkout.posList("alice",session).getFirst();
  mvc.perform(get("/api/vendor/pos/receipts/"+draft.id()).with(user("alice").authorities(()->"POS_RECEIPT_VIEW"))).andExpect(status().isNotFound());
  mvc.perform(post("/api/vendor/pos/drafts/"+draft.id()+"/cancel").with(csrf()).with(user("alice").authorities(()->"POS_VIEW")).contentType("application/json").content("{\"reason\":\"Cancel\"}")).andExpect(status().isForbidden());
  mvc.perform(post("/api/vendor/pos/drafts/"+draft.id()+"/finalize-cash").with(csrf()).with(user("alice").authorities(()->"POS_SALE_CREATE")).contentType("application/json").content("{\"cashReceived\":120}")).andExpect(status().isForbidden());
  assertThat(checkout.posList("bob",session)).isEmpty();
 }

 @Test void orderHistoryScopesPaginationDatesAndImmutableDetails() throws Exception {
  var receipt=service.checkout(a,"history-cash1",sale(1,"120"));String id=(String)receipt.get("id");
  db.update("INSERT INTO employees VALUES('colleague','unused',1,1,TRUE)");
  db.update("INSERT INTO branches(id,tenant_id,name) VALUES(3,1,'Other branch')");
  db.update("INSERT INTO employees VALUES('other-branch','unused',1,3,TRUE)");
  db.update("UPDATE products SET name='Renamed',price=999 WHERE tenant_id=1");
  mvc.perform(get("/api/vendor/pos/orders").with(user("colleague").authorities(()->"POS_VIEW")).param("status","PAID").param("size","1").param("reference",receipt.get("receipt_number").toString())).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(id));
  mvc.perform(get("/api/vendor/pos/orders/"+id).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].name").value("Panel")).andExpect(jsonPath("$.items[0].unitPrice").value(100)).andExpect(jsonPath("$.cashReceived").value(120));
  for(String who:List.of("bob","other-branch")){
   mvc.perform(get("/api/vendor/pos/orders").with(user(who).authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
   mvc.perform(get("/api/vendor/pos/orders/"+id).with(user(who).authorities(()->"POS_VIEW"))).andExpect(status().isNotFound());
  }
  mvc.perform(get("/api/vendor/pos/orders").with(user("alice").authorities(()->"POS_VIEW")).param("from","2000-01-01").param("to","2000-01-01")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  mvc.perform(get("/api/vendor/pos/orders").with(user("alice").authorities(()->"POS_VIEW")).param("reference",id.substring(0,8))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
  mvc.perform(get("/api/vendor/pos/orders").with(user("alice").authorities(()->"POS_VIEW")).param("page","1").param("size","1")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
 }
 @Test void customerHistoryKeepsCancelledOrdersAndHidesOtherChannels() throws Exception {
  configureOnline();var draft=onlineDraft("history-online");
  checkout.cancelOnline("customer",draft.id(),"Buying later");
  mvc.perform(get("/api/customer/orders").with(user("customer").authorities(()->"CUSTOMER")).param("status","CANCELLED")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].channel").value("ONLINE"));
  mvc.perform(get("/api/customer/orders/"+draft.id()).with(user("customer").authorities(()->"CUSTOMER"))).andExpect(status().isOk()).andExpect(jsonPath("$.cancellationReason").value("Buying later")).andExpect(jsonPath("$.cashReceived").doesNotExist());
  mvc.perform(get("/api/customer/orders").with(user("other-customer").authorities(()->"CUSTOMER"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  mvc.perform(get("/api/customer/orders/"+draft.id()).with(user("other-customer").authorities(()->"CUSTOMER"))).andExpect(status().isNotFound());
  mvc.perform(get("/api/vendor/pos/orders/"+draft.id()).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isNotFound());
  var cash=service.checkout(a,"history-guest",sale(1,"120"));
  mvc.perform(get("/api/customer/orders/"+cash.get("id")).with(user("customer").authorities(()->"CUSTOMER"))).andExpect(status().isNotFound());
 }
 @Test void orderHistoryRequiresPermissionsAndRejectsInvalidFilters() throws Exception {
  mvc.perform(get("/api/vendor/pos/orders")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/customer/orders").with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/vendor/pos/orders").with(user("alice"))).andExpect(status().isForbidden());
  for(String[] parameter:List.of(new String[]{"size","51"},new String[]{"page","-1"},new String[]{"status","REFUNDED"},new String[]{"reference","%"},new String[]{"from","invalid"}))
   mvc.perform(get("/api/vendor/pos/orders").with(user("alice").authorities(()->"POS_VIEW")).param(parameter[0],parameter[1])).andExpect(status().isBadRequest());
 }

 @Test void orderHistoryPaginationIsStableForEqualTimestampsAndUtcDayBounds() throws Exception {
  db.update("UPDATE inventory SET quantity=3 WHERE tenant_id=1");var ids=new ArrayList<String>();
  for(int n=0;n<3;n++)ids.add((String)service.checkout(a,"history-page"+n,sale(1,"120")).get("id"));
  db.update("UPDATE orders SET created_at=? WHERE tenant_id=1",java.time.LocalDateTime.parse("2026-11-12T23:59:59"));
  ids.sort(Comparator.reverseOrder());
  mvc.perform(get("/api/vendor/pos/orders").with(user("alice").authorities(()->"POS_VIEW")).param("size","2").param("from","2026-11-12").param("to","2026-11-12")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.items[0].id").value(ids.get(0))).andExpect(jsonPath("$.items[1].id").value(ids.get(1)));
  mvc.perform(get("/api/vendor/pos/orders").with(user("alice").authorities(()->"POS_VIEW")).param("size","2").param("page","1")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].id").value(ids.get(2)));
  mvc.perform(get("/api/vendor/pos/orders").with(user("alice").authorities(()->"POS_VIEW")).param("from","2026-11-13")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
 }

 @Test void pooledDatabaseClockUsesUtcForJdbcAndJpaHistory() {
  String databaseClock=db.queryForObject("SELECT CURRENT_TIMESTAMP",String.class);
  var local=java.time.LocalDateTime.parse(databaseClock.substring(0,19).replace(' ','T'));
  assertThat(java.time.Duration.between(local.toInstant(java.time.ZoneOffset.UTC),java.time.Instant.now()).abs()).isLessThan(java.time.Duration.ofSeconds(10));
 }
}
