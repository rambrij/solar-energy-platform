package com.saumya.pos;
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

@SpringBootTest(properties={"spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:pos;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}","spring.datasource.username=${TEST_DB_USER:sa}","spring.datasource.password=${TEST_DB_PASSWORD:}"})
@AutoConfigureMockMvc
@org.springframework.test.context.TestExecutionListeners(listeners=org.springframework.test.context.support.DependencyInjectionTestExecutionListener.class,inheritListeners=false)
class PosTest {
 @Autowired PosService service; @Autowired JdbcTemplate db; @Autowired MockMvc mvc;
 PosService.Scope a=new PosService.Scope(1,1,"alice"),b=new PosService.Scope(2,2,"bob");
 String session;
 @BeforeEach void seed(){
  for(String table:List.of("pos_cash_movements","vendor_invoices","inventory_ledger","order_items","orders","pos_audit","pos_sessions","employee_permissions","inventory","products","registers","employees","branches","tenants")) db.update("DELETE FROM "+table);
  db.update("INSERT INTO tenants VALUES(1,'Vendor A',TRUE),(2,'Vendor B',TRUE)");
  db.update("INSERT INTO branches(id,tenant_id,name) VALUES(1,1,'A'),(2,2,'B')");
  db.update("INSERT INTO employees VALUES('alice','unused',1,1,TRUE),('bob','unused',2,2,TRUE)");
  db.update("INSERT INTO registers VALUES(1,1,1),(2,2,2)");
  db.update("INSERT INTO products VALUES(1,1,'P1','123','Panel',100,10,TRUE),(2,2,'P2',NULL,'Private',100,0,TRUE)");
  db.update("INSERT INTO inventory VALUES(1,1,1,1),(2,2,2,1)");
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
  assertThatThrownBy(()->db.update("INSERT INTO inventory VALUES(1,1,2,10)"))
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
   .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->db.update(sql,"refund",1,1,"alice",session,"CASH_REFUND",1,"Float","db-refund"))
   .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
 }
}
