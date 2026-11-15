package com.saumya.pos;
import com.saumya.pos.cart.CartService;
import com.saumya.pos.catalog.CatalogService;
import com.saumya.pos.retail.PosService;
import com.saumya.pos.checkout.CheckoutService;
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
@org.springframework.test.context.ActiveProfiles("test")
@org.springframework.test.context.TestPropertySource(properties="payments.mock-enabled=true")
@org.springframework.test.context.TestExecutionListeners(listeners=org.springframework.test.context.support.DependencyInjectionTestExecutionListener.class,inheritListeners=false)
class PosTest {
 @Autowired com.saumya.pos.checkout.CheckoutReceiptService receiptReader;
 @Autowired PosService service; @Autowired JdbcTemplate db; @Autowired MockMvc mvc;
 @Autowired CartService marketplace;
 @Autowired CatalogService catalogue;
 @Autowired com.saumya.pos.inventory.InventoryService stock;
 @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
 @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager em;
 @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
 @Autowired com.saumya.pos.checkout.CheckoutService checkout;
 @Autowired com.saumya.pos.checkout.PaymentCheckoutService payments;
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
 PosService.Scope a=new PosService.Scope(1,1,"alice"),b=new PosService.Scope(2,2,"bob");
 String session;
 @BeforeEach void seed(){
  update("DELETE FROM pos_active_drafts");
  update("DELETE FROM pos_drawer_tenders");
  update("DELETE FROM inventory_receipts");
  update("DELETE FROM order_buyer_snapshots");
  update("DELETE FROM vendor_payment_attempts");
  update("UPDATE tenants SET online_branch_id=NULL");
  for(String table:List.of("catalog_change_events","branch_settings_events","identity_refresh_tokens","pos_customer_challenges","tenant_customers","catalog_discount_policy","receipt_document_audit","report_export_audit","pos_cash_refund_items","pos_cash_refunds","inventory_return_dispositions","pos_return_events","pos_return_items","pos_returns","catalog_promotion_audit","catalog_promotions","product_categories","catalog_categories","stock_reservations","vendor_invoices","inventory_ledger","order_items","orders","cart_items","carts","customer_profiles","pos_shift_reviews","pos_cash_movements","pos_audit","pos_sessions","employee_permissions","inventory","products","registers","employees","branches","tenants","users")) update("DELETE FROM "+table);
  update("INSERT INTO tenants(id,name,active) VALUES(1,'Vendor A',TRUE),(2,'Vendor B',TRUE)");
  update("INSERT INTO branches(id,tenant_id,name) VALUES(1,1,'A'),(2,2,'B')");
  employee("alice",1,1);employee("bob",2,2);update("UPDATE identity_sequence SET next_id=10000 WHERE id=1");update("UPDATE retail_sequences SET next_id=10000 WHERE name='REGISTER'");update("UPDATE catalog_sequences SET next_id=10000");
  update("INSERT INTO registers VALUES(1,1,1),(2,2,2)");
  update("INSERT INTO products(id,tenant_id,sku,barcode,name,price,tax_rate,published) VALUES(1,1,'P1','123','Panel',100,10,TRUE),(2,2,'P2',NULL,'Private',100,0,TRUE)");
  update("INSERT INTO inventory(tenant_id,branch_id,product_id,quantity) VALUES(1,1,1,1),(2,2,2,1)");
  session=(String)service.open(a,new PosService.OpenSession(1,new BigDecimal("50"))).get("id");
 }
 int update(String sql,Object...args){int result=db.update(sql,args);if(sql.matches("(?is)^\\s*(?:UPDATE\\s+inventory\\b|INSERT\\s+INTO\\s+inventory\\b|DELETE\\s+FROM\\s+inventory\\b).*")){db.update("DELETE FROM catalog_stock");db.update("INSERT INTO catalog_stock SELECT tenant_id,branch_id,product_id,quantity-reserved FROM inventory");}return result;}
 void employee(String username,long tenant,long branch){long id=db.queryForObject("SELECT COALESCE(MAX(id),0)+1 FROM users",Long.class);update("INSERT INTO users VALUES(?,?,?,TRUE)",id,username,encoder.encode("StaffPasswordForTests"));update("INSERT INTO employees VALUES(?,?,?,TRUE)",username,tenant,branch);}
 org.springframework.test.web.servlet.request.RequestPostProcessor bearer(String username,String password)throws Exception{var response=mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("username",username,"password",password)))).andExpect(status().isOk()).andReturn();String token=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).get("accessToken").asText();return request->{request.addHeader("Authorization","Bearer "+token);return request;};}
 CheckoutService.CashSale sale(long product,String cash){return new CheckoutService.CashSale(session,List.of(new CheckoutService.Item(product,1)),new BigDecimal(cash),false,null,null);}
 com.saumya.pos.checkout.PaymentCheckoutService.View terminal(String username,String id,String method){var amount=payments.tenderStatus(username,id).remaining();var result=payments.tender(username,id,"terminal-"+method,new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest(method,amount,null));return result.tenders().getLast();}
 @Test void retailDraftProjectionBlocksShiftClosureUntilCollectedFundsAreFinalized(){
  update("UPDATE branches SET split_tender_enabled=TRUE WHERE id=1");var draft=checkout.pos("alice","active-draft-gate",posDraft());assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_active_drafts",Integer.class)).isEqualTo(1);assertThatThrownBy(()->service.close(a,session,new BigDecimal("50"))).hasMessageContaining("Resolve reserved");
  payments.tender("alice",draft.id(),"gate-partial",new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("CASH",new BigDecimal("40"),new BigDecimal("40")));update("UPDATE orders SET expires_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)),draft.id());checkout.expire(1,draft.id());assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_active_drafts",Integer.class)).isEqualTo(1);assertThatThrownBy(()->service.close(a,session,new BigDecimal("90"))).hasMessageContaining("Resolve reserved");
  payments.tender("alice",draft.id(),"gate-remainder",new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("CASH",new BigDecimal("70"),new BigDecimal("70")));assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_active_drafts",Integer.class)).isZero();assertThat(service.close(a,session,new BigDecimal("160")).state()).isEqualTo("CLOSED");
 }
 @Test void guestBuyerSnapshotsArePrivateTransactionFactsAndNeverCreateAccounts()throws Exception{
  var buyer=new com.saumya.pos.checkout.BuyerDetailsPolicy.Input("Private walk-in buyer","private-contact","PRIVATE-TAX","Private street");
  var receipt=checkout.cashSale("alice","guest-buyer",new CheckoutService.CashSale(session,List.of(new CheckoutService.Item(1,1)),new BigDecimal("120"),false,null,buyer));String id=(String)receipt.get("id");assertThat(receipt.get("buyer")).isEqualTo(buyer);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM users",Integer.class)).isEqualTo(2);assertThat(db.queryForObject("SELECT COUNT(*) FROM customer_profiles",Integer.class)).isZero();assertThat(db.queryForObject("SELECT COUNT(*) FROM tenant_customers",Integer.class)).isZero();
  mvc.perform(get("/api/v1/vendor/pos/receipts/"+id).with(user("alice").authorities(()->"POS_RECEIPT_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.buyer.name").value("Private walk-in buyer")).andExpect(jsonPath("$.buyer.taxId").value("PRIVATE-TAX"));
  var denied=mvc.perform(get("/api/v1/vendor/pos/receipts/"+id).with(user("bob").authorities(()->"POS_RECEIPT_VIEW"))).andExpect(status().isNotFound()).andReturn();assertThat(denied.getResponse().getContentAsString()).doesNotContain("Private walk-in buyer","PRIVATE-TAX");
  mvc.perform(get("/api/v1/vendor/pos/receipts/"+id).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isForbidden());assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE action='BUYER_SNAPSHOT_VIEW'",Integer.class)).isEqualTo(2);assertThat(db.queryForList("SELECT * FROM pos_audit").toString()).doesNotContain("private-contact","PRIVATE-TAX");
  assertThatThrownBy(()->checkout.cashSale("alice","guest-buyer",new CheckoutService.CashSale(session,List.of(new CheckoutService.Item(1,1)),new BigDecimal("120"),false,null,new com.saumya.pos.checkout.BuyerDetailsPolicy.Input("Changed buyer",null,null,null)))).hasMessageContaining("different draft request");assertThat(db.queryForObject("SELECT name FROM order_buyer_snapshots",String.class)).isEqualTo("Private walk-in buyer");
 }
 @Test void approvedInvoiceBuyerRequirementDoesNotRequireGuestContactOrRegistration(){
  update("UPDATE inventory SET quantity=4 WHERE tenant_id=1 AND product_id=1");update("UPDATE branches SET buyer_details_required=TRUE WHERE id=1");checkout.cashSale("alice","ordinary-guest",sale(1,"120"));
  assertThatThrownBy(()->checkout.cashSale("alice","invoice-no-buyer",new CheckoutService.CashSale(session,List.of(new CheckoutService.Item(1,1)),new BigDecimal("120"),true,null,null))).hasMessageContaining("Buyer name and address");assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);
  var buyer=new com.saumya.pos.checkout.BuyerDetailsPolicy.Input("Invoice buyer",null,null,"Buyer street");var receipt=checkout.cashSale("alice","invoice-buyer",new CheckoutService.CashSale(session,List.of(new CheckoutService.Item(1,1)),new BigDecimal("120"),true,null,buyer));assertThat(receipt.get("tax_invoice")).isEqualTo(true);assertThat(receipt.get("buyer")).isEqualTo(buyer);assertThat(db.queryForObject("SELECT COUNT(*) FROM customer_profiles",Integer.class)).isZero();
 }
 @Test void cancellingOrExpiringAnUnpaidDraftRemovesItsOptionalBuyerSnapshot(){
  var buyer=new com.saumya.pos.checkout.BuyerDetailsPolicy.Input("Guest",null,null,"Street");var request=new CheckoutService.PosDraft(session,List.of(new CheckoutService.Item(1,1)),false,null,buyer);var draft=checkout.pos("alice","buyer-cancel",request);assertThat(db.queryForObject("SELECT COUNT(*) FROM order_buyer_snapshots",Integer.class)).isEqualTo(1);checkout.cancelPos("alice",draft.id(),"Buyer left");assertThat(db.queryForObject("SELECT COUNT(*) FROM order_buyer_snapshots",Integer.class)).isZero();
  draft=checkout.pos("alice","buyer-expire",request);update("UPDATE orders SET expires_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)),draft.id());checkout.expire(1,draft.id());assertThat(db.queryForObject("SELECT COUNT(*) FROM order_buyer_snapshots",Integer.class)).isZero();
 }
 @Test void concurrentInventoryChangesRefreshAnAlreadyLoadedCatalogProjection(){
  var pool=Executors.newSingleThreadExecutor();
  try{new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->{
   var key=new com.saumya.pos.catalog.internal.CatalogStock.Key(1,1,1);assertThat(em.find(com.saumya.pos.catalog.internal.CatalogStock.class,key).available()).isEqualTo(1);
   try{pool.submit(()->new org.springframework.transaction.support.TransactionTemplate(transactions).execute(other->{service.lockOutlet(a);return stock.receive(1,1,1,2);})).get(20,TimeUnit.SECONDS);}catch(Exception failure){throw new IllegalStateException(failure);}
   stock.issue(1,1,1,2);return null;
  });}finally{pool.shutdownNow();}
  assertThat(stock.atBranch(1,1,1)).isEqualTo(1);assertThat(db.queryForObject("SELECT available FROM catalog_stock WHERE tenant_id=1 AND branch_id=1 AND product_id=1",Integer.class)).isEqualTo(1);
 }
 @Test void stockReceivingUsesAssignedScopeAndIdempotentAuditedCommands()throws Exception{
  String path="/api/v1/vendor/inventory/receipts",body="{\"productId\":1,\"quantity\":2,\"reason\":\"Delivery inspected\"}";
  mvc.perform(post(path).header("Idempotency-Key","stock-key").with(csrf()).with(user("alice").authorities(()->"PRODUCT_VIEW")).contentType("application/json").content(body)).andExpect(status().isForbidden());
  mvc.perform(post(path).header("Idempotency-Key","stock-key").with(csrf()).with(user("alice").authorities(()->"INVENTORY_RECEIVE")).contentType("application/json").content(body.replace("productId\":1","productId\":2"))).andExpect(status().isNotFound());
  for(int attempt=0;attempt<2;attempt++)mvc.perform(post(path).header("Idempotency-Key","stock-key").with(csrf()).with(user("alice").authorities(()->"INVENTORY_RECEIVE")).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.availableAfter").value(3));
  mvc.perform(post(path).header("Idempotency-Key","stock-key").with(csrf()).with(user("alice").authorities(()->"INVENTORY_RECEIVE")).contentType("application/json").content(body.replace("quantity\":2","quantity\":3"))).andExpect(status().isConflict());
  mvc.perform(get(path).with(user("bob").authorities(()->"INVENTORY_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
  mvc.perform(get(path).with(user("alice").authorities(()->"INVENTORY_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].actor").value("alice"));
  assertThat(stock.atBranch(1,1,1)).isEqualTo(3);assertThat(stock.atBranch(2,2,2)).isEqualTo(1);assertThat(db.queryForObject("SELECT available FROM catalog_stock WHERE tenant_id=1 AND branch_id=1 AND product_id=1",Integer.class)).isEqualTo(3);
 }
 @Test void concurrentStockReceiptRetriesCreateOneBalanceAndOneReceipt()throws Exception{
  update("INSERT INTO products(id,tenant_id,sku,name,price,tax_rate,published) VALUES(3,1,'NEW','New panel',100,10,FALSE)");String body="{\"productId\":3,\"quantity\":5,\"reason\":\"First delivery\"}";var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
  try{Callable<String> command=()->{gate.await();var response=mvc.perform(post("/api/v1/vendor/inventory/receipts").header("Idempotency-Key","same-delivery").with(csrf()).with(user("alice").authorities(()->"INVENTORY_RECEIVE")).contentType("application/json").content(body)).andExpect(status().isOk()).andReturn();return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).get("id").asText();};var first=pool.submit(command);var second=pool.submit(command);gate.countDown();assertThat(first.get(20,TimeUnit.SECONDS)).isEqualTo(second.get(20,TimeUnit.SECONDS));}finally{pool.shutdownNow();}
  assertThat(stock.atBranch(1,1,3)).isEqualTo(5);assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_receipts",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT available FROM catalog_stock WHERE tenant_id=1 AND branch_id=1 AND product_id=3",Integer.class)).isEqualTo(5);
 }
 @Test void receivingBeyondStockCapacityCannotCreateAReceiptOrChangeAvailability()throws Exception{
  update("UPDATE inventory SET quantity=2147483646 WHERE tenant_id=1 AND product_id=1");mvc.perform(post("/api/v1/vendor/inventory/receipts").header("Idempotency-Key","capacity-check").with(csrf()).with(user("alice").authorities(()->"INVENTORY_RECEIVE")).contentType("application/json").content("{\"productId\":1,\"quantity\":2,\"reason\":\"Capacity check\"}")).andExpect(status().isConflict());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_receipts",Integer.class)).isZero();assertThat(stock.atBranch(1,1,1)).isEqualTo(2147483646L);assertThat(db.queryForObject("SELECT available FROM catalog_stock WHERE tenant_id=1 AND branch_id=1 AND product_id=1",Integer.class)).isEqualTo(2147483646);
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
  assertThat(db.queryForObject("SELECT COUNT(*) FROM catalog_change_events WHERE resource_id=?",Integer.class,id)).isEqualTo(2);assertThat(stock.available(1,id)).isZero();
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
  long register=branchPolicies.createRegister("alice","New till approved");assertThat(db.queryForObject("SELECT tenant_id FROM registers WHERE id=?",Long.class,register)).isEqualTo(1L);
  assertThat(branchPolicies.current("bob").name()).isEqualTo("B");assertThat(db.queryForObject("SELECT COUNT(*) FROM branch_settings_events",Integer.class)).isEqualTo(2);
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
 @Test void vendorMembershipIsRevalidatedForEveryJwtRequest() throws Exception {
  update("INSERT INTO employee_permissions(username,permission,tenant_id) VALUES('alice','POS_VIEW',1)");var session=authentication.login("alice","StaffPasswordForTests");
  mvc.perform(get("/api/v1/vendor/pos/products").header("Authorization","Bearer "+session.accessToken())).andExpect(status().isOk());
  update("UPDATE employees SET enabled=FALSE WHERE username='alice'");
  mvc.perform(get("/api/v1/vendor/pos/products").header("Authorization","Bearer "+session.accessToken())).andExpect(status().isUnauthorized());
 }
 @Test void guestAssociationRequiresCustomerProofAndOnlyCreatesCurrentVendorRelationship(){
  configureOnline();var draft=checkout.pos("alice","customer-association",posDraft());
  assertThat(db.queryForObject("SELECT customer_id FROM orders WHERE id=?",Long.class,draft.id())).isNull();
  assertThatThrownBy(()->associations.create("alice",draft.id(),false));
  var challenge=associations.create("alice",draft.id(),true);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM tenant_customers",Integer.class)).isZero();
  assertThatThrownBy(()->associations.approve("alice",challenge.token(),true));
  assertThatThrownBy(()->associations.approve("customer",challenge.token(),false));
  assertThat(associations.approve("customer",challenge.token(),true).status()).isEqualTo("ASSOCIATED");
  assertThat(associations.approve("customer",challenge.token(),true).status()).isEqualTo("ASSOCIATED");
  assertThatThrownBy(()->associations.approve("other-customer",challenge.token(),true));
  assertThat(db.queryForObject("SELECT customer_id FROM orders WHERE id=?",Long.class,draft.id())).isEqualTo(100L);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM tenant_customers WHERE tenant_id=1 AND customer_id=100",Integer.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM tenant_customers WHERE tenant_id=2",Integer.class)).isZero();
  checkout.finalizeCash("alice",draft.id(),new BigDecimal("120"));assertThat(onlineReceipts.read("customer",draft.id()).orderId()).isEqualTo(draft.id());
 }
 @Test void expiredAssociationTokenCannotAttachCustomer(){configureOnline();var draft=checkout.pos("alice","expired-association",posDraft());var challenge=associations.create("alice",draft.id(),true);update("UPDATE pos_customer_challenges SET expires_at=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)));assertThatThrownBy(()->associations.approve("customer",challenge.token(),true));assertThat(db.queryForObject("SELECT COUNT(*) FROM tenant_customers",Integer.class)).isZero();}
 @Test void partialCashImmediatelyAppearsInDrawerAndRetriesCannotDoubleCount(){
  enableTerminal();update("UPDATE branches SET split_tender_enabled=TRUE WHERE id=1");
  var draft=checkout.pos("alice","drawer-partial",posDraft());var request=new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("CASH",new BigDecimal("40"),new BigDecimal("50"));
  payments.tender("alice",draft.id(),"drawer-cash",request);
  var scope=new PosService.Scope(1,1,"alice");var first=service.reconciliation(scope,session);
  assertThat(first.cashReceived()).isEqualByComparingTo("50");assertThat(first.changeGiven()).isEqualByComparingTo("10");assertThat(first.expectedCash()).isEqualByComparingTo("90");
  payments.tender("alice",draft.id(),"drawer-cash",request);assertThat(service.reconciliation(scope,session)).isEqualTo(first);
  payments.tender("alice",draft.id(),"drawer-rest",new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("CASH",new BigDecimal("70"),new BigDecimal("70")));
  assertThat(service.reconciliation(scope,session).expectedCash()).isEqualByComparingTo("160");
  payments.finalizeTenders("alice",draft.id());assertThat(service.reconciliation(scope,session).expectedCash()).isEqualByComparingTo("160");
 }
 @Test void expiredPartialCashRetainsStockAndCanCompleteOnlyTheRemainingBalance(){
  enableTerminal();update("UPDATE branches SET split_tender_enabled=TRUE WHERE id=1");var draft=checkout.pos("alice","expired-partial",posDraft());
  payments.tender("alice",draft.id(),"expired-cash",new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("CASH",new BigDecimal("40"),new BigDecimal("40")));
  update("UPDATE orders SET expires_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)),draft.id());checkout.expire(1,draft.id());
  assertThat(db.queryForObject("SELECT status FROM orders WHERE id=?",String.class,draft.id())).isEqualTo("DRAFT");
  assertThat(stock.atBranch(1,1,1)).isZero();assertThatThrownBy(()->checkout.cancelPos("alice",draft.id(),"Cancel")).hasMessageContaining("Reconcile");
  assertThat(payments.tender("alice",draft.id(),"expired-rest",new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("CASH",new BigDecimal("70"),new BigDecimal("70"))).orderStatus()).isEqualTo("PAID");
  assertThat(service.reconciliation(a,session).expectedCash()).isEqualByComparingTo("160");
 }
 @Test void expiredUnpaidBillCannotStartTakingFunds(){
  var draft=checkout.pos("alice","expired-unpaid",posDraft());update("UPDATE orders SET expires_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)),draft.id());
  assertThatThrownBy(()->payments.tender("alice",draft.id(),"expired-charge",new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("CASH",new BigDecimal("110"),new BigDecimal("110")))).hasMessageContaining("not payable");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_drawer_tenders",Integer.class)).isZero();
 }
 @Test void splitTenderLimitsAndChangedRetryPayloadAreRejected(){
  enableTerminal();var draft=checkout.pos("alice","tender-limits",posDraft());
  assertThatThrownBy(()->payments.tender("alice",draft.id(),"split-disabled",new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("CASH",new BigDecimal("40"),new BigDecimal("40")))).hasMessageContaining("disabled");
  update("UPDATE branches SET split_tender_enabled=TRUE WHERE id=1");
  assertThatThrownBy(()->payments.tender("alice",draft.id(),"overpayment",new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("CASH",new BigDecimal("111"),new BigDecimal("111")))).hasMessageContaining("remaining");
  payments.tender("alice",draft.id(),"partial-cash",new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("CASH",new BigDecimal("40"),new BigDecimal("40")));
  assertThatThrownBy(()->payments.tender("alice",draft.id(),"partial-cash",new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("CASH",new BigDecimal("50"),new BigDecimal("50")))).hasMessageContaining("Idempotency");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_payment_attempts",Integer.class)).isEqualTo(1);
 }
 @Test void splitCashAndVerifiedTerminalFinalizeExactlyOnce(){
  enableTerminal();update("UPDATE branches SET split_tender_enabled=TRUE WHERE id=1");
  var draft=checkout.pos("alice","split-draft",posDraft());
  var cash=payments.tender("alice",draft.id(),"split-cash",new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("CASH",new BigDecimal("40"),new BigDecimal("50")));
  assertThat(cash.remaining()).isEqualByComparingTo("70");assertThat(cash.orderStatus()).isEqualTo("DRAFT");
  assertThatThrownBy(()->payments.finalizeTenders("alice",draft.id())).hasMessageContaining("total");
  assertThatThrownBy(()->checkout.cancelPos("alice",draft.id(),"Cannot discard received cash"));
  var external=payments.tender("alice",draft.id(),"split-upi",new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("UPI",new BigDecimal("70"),null));
  var attempt=external.tenders().stream().filter(t->"UPI".equals(t.method())).findFirst().orElseThrow();
  assertThat(payments.awaitingReview("manager").getFirst().attemptId()).isEqualTo(attempt.attemptId());
  assertThatThrownBy(()->payments.tender("alice",draft.id(),"another-charge",new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("CARD",BigDecimal.ONE,null)));
  tenderReviews.review("manager",draft.id(),terminalReview(attempt.attemptId(),"CONFIRM"));
  assertThat(payments.finalizeTenders("alice",draft.id()).orderStatus()).isEqualTo("PAID");
  assertThat(payments.tender("alice",draft.id(),"split-cash",new com.saumya.pos.checkout.PaymentCheckoutService.TenderRequest("CASH",new BigDecimal("40"),new BigDecimal("50"))).confirmed()).isEqualByComparingTo("110");
  assertThat(receiptReader.receipt(a,draft.id()).get("payment_method")).isEqualTo("SPLIT");assertThat(service.reconciliation(a,session).expectedCash()).isEqualByComparingTo("90");
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
  configureOnline();marketplace.put(100,new CartService.Item("vendor-1",1,1));
  mvc.perform(post("/api/v1/customer/cart/items").with(csrf()).with(user("customer").authorities(()->"CUSTOMER"))
   .contentType("application/json").content("{\"vendorSlug\":\"vendor-2\",\"productId\":2,\"quantity\":1}"))
   .andExpect(status().isConflict()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
   .andExpect(jsonPath("$.code").value("CART_VENDOR_MISMATCH"));
  assertThat(marketplace.cart(100).vendorSlug()).isEqualTo("vendor-1");
 }
 @Test void customerReceiptUsesSavedFactsAndCopiesDoNotCreateFinancialWrites(){
  configureOnline();update("UPDATE branches SET invoice_required=TRUE WHERE id=1");var draft=onlineDraft("receipt-online");var attempt=payments.start("customer",draft.id(),"UPI");payments.confirm("customer",draft.id(),attempt.attemptId());
  update("UPDATE tenants SET name='Renamed',active=FALSE WHERE id=1");update("UPDATE branches SET name='Changed outlet' WHERE id=1");update("UPDATE products SET name='Changed panel',price=999 WHERE id=1");
  var receipt=onlineReceipts.read("customer",draft.id());assertThat(receipt.seller()).isEqualTo("Vendor A");assertThat(receipt.outlet()).isEqualTo("A");assertThat(receipt.items().getFirst().name()).isEqualTo("Panel");assertThat(receipt.total()).isEqualByComparingTo("110");assertThat(receipt.invoices().getFirst().seller()).isEqualTo("Vendor A");assertThat(receipt.copy()).isFalse();
  for(int i=0;i<2;i++){var copy=onlineReceipts.copy("customer",draft.id());assertThat(copy.copy()).isTrue();assertThat(copy.orderId()).isEqualTo(receipt.orderId());assertThat(copy.issuedAt()).isEqualTo(receipt.issuedAt());}
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE action='CUSTOMER_RECEIPT_COPY' AND actor='customer:100'",Integer.class)).isEqualTo(2);assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_ledger",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_invoices",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_payment_attempts",Integer.class)).isEqualTo(1);
 }
 @Test void onlineReceiptApiEnforcesCustomerOwnershipAndAuditedCopyCsrf() throws Exception {
  configureOnline();var draft=onlineDraft("receipt-security");String path="/api/v1/customer/orders/"+draft.id();
  mvc.perform(get(path+"/receipt")).andExpect(status().isUnauthorized());mvc.perform(get(path+"/receipt").with(user("alice").authorities(()->"POS_RECEIPT_VIEW"))).andExpect(status().isForbidden());mvc.perform(get(path+"/receipt").with(user("customer").authorities(()->"CUSTOMER"))).andExpect(status().isNotFound());
  var attempt=payments.start("customer",draft.id(),"CARD");payments.confirm("customer",draft.id(),attempt.attemptId());
  update("INSERT INTO users VALUES(300,'other-buyer','unused',TRUE)");update("INSERT INTO customer_profiles VALUES(300,300)");mvc.perform(get(path+"/receipt").with(user("other-buyer").authorities(()->"CUSTOMER"))).andExpect(status().isNotFound());
  mvc.perform(post(path+"/receipt-copy").with(user("customer").authorities(()->"CUSTOMER")).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  mvc.perform(post(path+"/receipt-copy").with(csrf()).with(user("customer").authorities(()->"CUSTOMER")).contentType("application/json").content("{\"tenantId\":2}")).andExpect(status().isBadRequest());
  mvc.perform(post(path+"/receipt-copy").with(csrf()).with(user("customer").authorities(()->"CUSTOMER")).contentType("application/json").content("{}")).andExpect(status().isOk()).andExpect(jsonPath("$.copy").value(true)).andExpect(jsonPath("$.paymentMethod").value("MOCK_CARD")).andExpect(jsonPath("$.seller").value("Vendor A"));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE action='CUSTOMER_RECEIPT_COPY'",Integer.class)).isEqualTo(1);
 }
 @Test void onlineReceiptCannotExposeGuestPosSale(){configureOnline();String id=(String)checkout.cashSale("alice","receipt-pos-private",sale(1,"120")).get("id");assertThatThrownBy(()->onlineReceipts.read("customer",id)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);}
 @Test void onlinePaymentIsDurableVendorOwnedAndConfirmedExactlyOnce(){
  configureOnline();var draft=onlineDraft("online-payment");var started=payments.start("customer",draft.id(),"UPI");
  assertThat(started.merchantAccount()).isEqualTo("mock-vendor-1");assertThat(started.state()).isEqualTo("PENDING");
  assertThat(db.queryForObject("SELECT status FROM orders",String.class)).isEqualTo("DRAFT");assertThat(stock.atBranch(1,1,1)).isZero();
  assertThat(payments.start("customer",draft.id(),"UPI").attemptId()).isEqualTo(started.attemptId());assertThatThrownBy(()->payments.start("customer",draft.id(),"CARD"));
  assertThat(payments.confirm("customer",draft.id(),started.attemptId()).state()).isEqualTo("CONFIRMED");payments.confirm("customer",draft.id(),started.attemptId());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_ledger",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT payment_method FROM orders",String.class)).isEqualTo("MOCK_UPI");
  assertThat(db.queryForObject("SELECT customer_id FROM orders",Long.class)).isEqualTo(100);assertThat(db.queryForObject("SELECT cash_received FROM orders",BigDecimal.class)).isNull();
  assertThat(marketplace.cart(100).id()).isNull();
 }
 @Test void onlinePaymentKeepsEditedCartAndIssuesConfiguredTaxInvoice(){
  configureOnline();update("UPDATE inventory SET quantity=3 WHERE tenant_id=1");update("UPDATE branches SET invoice_required=TRUE WHERE id=1");var draft=onlineDraft("online-edited-cart");var attempt=payments.start("customer",draft.id(),"CARD");
  marketplace.put(100,new CartService.Item("vendor-1",1,2));payments.confirm("customer",draft.id(),attempt.attemptId());
  assertThat(marketplace.cart(100).items().getFirst().quantity()).isEqualTo(2);assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_invoices",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT seller_name FROM vendor_invoices",String.class)).isEqualTo("Vendor A");
 }
 void enableTerminal(){supervisor("manager");update("UPDATE branches SET manual_tender_enabled=TRUE,upi_merchant_reference='vendor-upi-account',card_merchant_reference='vendor-card-account' WHERE id=1");}
 com.saumya.pos.checkout.PaymentCheckoutService.Review terminalReview(String attempt,String decision){return new com.saumya.pos.checkout.PaymentCheckoutService.Review(attempt,decision,"terminal-result-123","evidence-record-123","Independent account and amount verification");}
 @Test void terminalVerificationIsDefaultDisabledAndOnlineSimulationCannotConfirmPos(){
  var draft=checkout.pos("alice","terminal-disabled",posDraft());assertThat(payments.tenderStatus("alice",draft.id()).tenders()).isEmpty();assertThatThrownBy(()->terminal("alice",draft.id(),"UPI"));assertThatThrownBy(()->payments.confirm("alice",draft.id(),"arbitrary"));assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_payment_attempts",Integer.class)).isZero();
 }
 @Test void terminalUnknownCanConfirmAfterOriginalTtlWithoutRechargingOrReleasingStock(){
  enableTerminal();var draft=checkout.pos("alice","terminal-late-result",posDraft());var attempt=terminal("alice",draft.id(),"UPI");payments.unknown("alice",draft.id(),attempt.attemptId());update("UPDATE orders SET expires_at=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)));checkout.expire(1,draft.id());
  assertThat(stock.atBranch(1,1,1)).isZero();assertThatThrownBy(()->checkout.cancelPos("alice",draft.id(),"Cannot discard funds"));assertThatThrownBy(()->terminal("alice",draft.id(),"CARD"));assertThatThrownBy(()->tenderReviews.review("bob",draft.id(),terminalReview(attempt.attemptId(),"CONFIRM")));
  assertThat(payments.awaitingReview("manager")).hasSize(1);tenderReviews.review("manager",draft.id(),terminalReview(attempt.attemptId(),"CONFIRM"));assertThat(payments.awaitingReview("manager")).isEmpty();assertThat(payments.reviewStatus("manager",draft.id()).state()).isEqualTo("CONFIRMED");assertThat(db.queryForObject("SELECT reviewed_by FROM vendor_payment_attempts",String.class)).isEqualTo("manager");assertThat(db.queryForObject("SELECT evidence_reference FROM vendor_payment_attempts",String.class)).isEqualTo("evidence-record-123");assertThat(db.queryForObject("SELECT reviewed_at FROM vendor_payment_attempts",java.sql.Timestamp.class)).isNotNull();assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_payment_attempts",Integer.class)).isEqualTo(1);
 }
 @Test void terminalReviewApiRequiresIndependentPermissionCsrfAndSafeEvidence() throws Exception {
  enableTerminal();var draft=checkout.pos("alice","terminal-review-api",posDraft());var attempt=terminal("alice",draft.id(),"CARD");String path="/api/v1/vendor/pos/payments/review/"+draft.id();String body="{\"attemptId\":\""+attempt.attemptId()+"\",\"decision\":\"CONFIRM\",\"reference\":\"terminal-123\",\"evidence\":\"proof-123\",\"reason\":\"Verified original vendor account and full amount\"}";
  mvc.perform(get(path).with(user("manager").authorities(()->"POS_VIEW"))).andExpect(status().isForbidden());mvc.perform(post(path).with(user("manager").authorities(()->"POS_TENDER_MANUAL_VERIFY")).contentType("application/json").content(body)).andExpect(status().isForbidden());
  mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"POS_TENDER_MANUAL_VERIFY")).contentType("application/json").content(body)).andExpect(status().isForbidden());mvc.perform(post(path).with(csrf()).with(user("manager").authorities(()->"POS_TENDER_MANUAL_VERIFY")).contentType("application/json").content(body.replace("terminal-123","4111111111111111"))).andExpect(status().isBadRequest());
  mvc.perform(post(path).with(csrf()).with(user("manager").authorities(()->"POS_TENDER_MANUAL_VERIFY")).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CONFIRMED"));assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE action='TERMINAL_TENDER_CONFIRM'",Integer.class)).isEqualTo(1);
 }
 @Test void posCardPaymentIssuesReceiptWithoutIncreasingCashDrawer(){
  enableTerminal();
  var draft=checkout.pos("alice","pos-card-payment",posDraft());var attempt=terminal("alice",draft.id(),"CARD");
  assertThatThrownBy(()->checkout.finalizeCash("alice",draft.id(),new BigDecimal("120")));
  assertThatThrownBy(()->payments.tenderStatus("bob",draft.id()));assertThatThrownBy(()->payments.confirm("alice",draft.id(),"forged"));
  assertThatThrownBy(()->tenderReviews.review("alice",draft.id(),terminalReview(attempt.attemptId(),"CONFIRM")));
  tenderReviews.review("manager",draft.id(),terminalReview(attempt.attemptId(),"CONFIRM"));var receipt=receiptReader.receipt(a,draft.id());assertThat(receipt.get("payment_method")).isEqualTo("CARD");assertThat(receipt.get("payment_reference")).isEqualTo("terminal-result-123");assertThat(receipt.get("cash_received")).isEqualTo(new BigDecimal("0.00"));assertThat(service.reconciliation(a,session).expectedCash()).isEqualByComparingTo("50");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM customer_profiles",Integer.class)).isZero();
  assertThatThrownBy(()->new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx->returnSaleAccess.cashSale(1,1,draft.id()))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }
 @Test void expiredOrChangedPaymentCannotConsumeStockOrConfirm(){
  enableTerminal();
  var draft=checkout.pos("alice","expire-payment",posDraft());var attempt=terminal("alice",draft.id(),"UPI");
  update("UPDATE products SET price=200 WHERE id=1");assertThat(tenderReviews.review("manager",draft.id(),terminalReview(attempt.attemptId(),"CONFIRM")).orderStatus()).isEqualTo("DRAFT");assertThat(db.queryForObject("SELECT state FROM vendor_payment_attempts",String.class)).isEqualTo("CONFIRMED");payments.unknown("alice",draft.id(),attempt.attemptId());
  update("UPDATE products SET price=100 WHERE id=1");update("UPDATE orders SET expires_at=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)));checkout.expire(1,draft.id());
  assertThatThrownBy(()->checkout.cancelPos("alice",draft.id(),"Do not discard uncertain payment"));assertThat(stock.atBranch(1,1,1)).isZero();assertThat(db.queryForObject("SELECT status FROM orders",String.class)).isEqualTo("DRAFT");assertThatThrownBy(()->tenderReviews.review("manager",draft.id(),terminalReview(attempt.attemptId(),"FAIL")));assertThat(stock.atBranch(1,1,1)).isZero();assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_ledger",Integer.class)).isZero();
 }
 @Test void concurrentPaymentConfirmationIssuesOneReceiptAndOneStockMovement() throws Exception {
  enableTerminal();
  var draft=checkout.pos("alice","concurrent-payment",posDraft());var attempt=terminal("alice",draft.id(),"UPI");
  try(var pool=Executors.newFixedThreadPool(2)){Callable<String> confirm=()->tenderReviews.review("manager",draft.id(),terminalReview(attempt.attemptId(),"CONFIRM")).state();for(var result:pool.invokeAll(List.of(confirm,confirm)))assertThat(result.get()).isEqualTo("CONFIRMED");}
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_ledger",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT receipt_sequence FROM branches WHERE id=1",Integer.class)).isEqualTo(1);
 }
 @Test void paymentApiEnforcesPermissionsCsrfAndStrictPayloads() throws Exception {
  enableTerminal();
  var draft=checkout.pos("alice","http-payment",posDraft());String path="/api/v1/vendor/pos/sales/"+draft.id()+"/tenders";
  mvc.perform(get(path)).andExpect(status().isUnauthorized());mvc.perform(get(path).with(user("alice"))).andExpect(status().isForbidden());
  mvc.perform(post(path).header("Idempotency-Key","http-terminal").with(csrf()).with(user("alice").authorities(()->"POS_SALE_CREATE")).contentType("application/json").content("{\"method\":\"UPI\",\"appliedAmount\":110}")).andExpect(status().isForbidden());
  mvc.perform(post(path).header("Idempotency-Key","http-terminal").with(user("alice").authorities(()->"POS_SALE_CREATE",()->"POS_TENDER_EXTERNAL_RECORD")).contentType("application/json").content("{\"method\":\"UPI\",\"appliedAmount\":110}")).andExpect(status().isForbidden());
  mvc.perform(post(path).header("Idempotency-Key","http-terminal").with(csrf()).with(user("alice").authorities(()->"POS_SALE_CREATE",()->"POS_TENDER_EXTERNAL_RECORD")).contentType("application/json").content("{\"method\":\"UPI\",\"amount\":1}")).andExpect(status().isBadRequest());
  mvc.perform(post(path).header("Idempotency-Key","http-terminal").with(csrf()).with(user("alice").authorities(()->"POS_SALE_CREATE",()->"POS_TENDER_EXTERNAL_RECORD")).contentType("application/json").content("{\"method\":\"UPI\",\"appliedAmount\":110}")).andExpect(status().isOk()).andExpect(jsonPath("$.tenders[0].merchantAccount").value("vendor-upi-account")).andExpect(jsonPath("$.tenders[0].mockEnabled").value(false));
 }
 @Test void guestCashIsAtomicAndIdempotent(){
  var r=checkout.cashSale(a.cashier(),"checkout-1",sale(1,"120"));
  assertThat(r.get("customer_id")).isNull();assertThat(r.get("total")).isEqualTo(new BigDecimal("110.00"));assertThat(r.get("change_given")).isEqualTo(new BigDecimal("10.00"));
  assertThat(checkout.cashSale(a.cashier(),"checkout-1",sale(1,"120")).get("id")).isEqualTo(r.get("id"));
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isZero();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);
  assertThatThrownBy(()->checkout.cashSale(a.cashier(),"checkout-1",sale(1,"130")));
  assertThatThrownBy(()->receiptReader.receipt(b,(String)r.get("id")));
  assertThat(receiptReader.reprint(a,(String)r.get("id")).get("copy")).isEqualTo(true);
 }
 @Test void forbiddenProductAndUnderpaymentLeaveNoSale(){
  assertThatThrownBy(()->checkout.cashSale(a.cashier(),"foreign-1",sale(2,"120")));
  assertThatThrownBy(()->checkout.cashSale(a.cashier(),"underpay1",sale(1,"1")));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isZero();
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isEqualTo(1);
 }
 @Test void invoiceIsConditionalAndImmutable(){
  update("UPDATE branches SET invoice_required=TRUE WHERE id=1");
  var receipt=checkout.cashSale(a.cashier(),"invoice-1",sale(1,"110"));
  update("UPDATE tenants SET name='Renamed' WHERE id=1");
  assertThat(db.queryForObject("SELECT seller_name FROM vendor_invoices",String.class)).isEqualTo("Vendor A");
  assertThat(((List<?>)receipt.get("invoices"))).hasSize(1);
  checkout.cashSale(a.cashier(),"invoice-1",sale(1,"110"));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_invoices",Integer.class)).isEqualTo(1);
 }
 @Test void concurrentSalesCannotOversell() throws Exception {
  try(var pool=Executors.newFixedThreadPool(2)) {
   var jobs=List.of((Callable<Boolean>)()->attempt("concur-01"),()->attempt("concur-02"));
   var results=pool.invokeAll(jobs);int successes=0;for(var result:results) if(result.get())successes++;
   assertThat(successes).isEqualTo(1);
  }
 }
 boolean attempt(String key){try{checkout.cashSale(a.cashier(),key,sale(1,"110"));return true;}catch(org.springframework.web.server.ResponseStatusException e){return false;}}
 @Test void registerAndSessionScopeAreEnforced(){
  assertThatThrownBy(()->service.open(a,new PosService.OpenSession(1,BigDecimal.ZERO)));
  assertThatThrownBy(()->checkout.cashSale(b.cashier(),"bad-sess1",sale(2,"110")));
  assertThatThrownBy(()->service.close(a,session,BigDecimal.ZERO));
  service.close(a,session,new BigDecimal("50"));
  assertThatThrownBy(()->checkout.cashSale(a.cashier(),"closed-01",sale(1,"110")));
 }
 @Test void authenticationPermissionsCsrfAndForgedFields() throws Exception {
  mvc.perform(get("/api/v1/vendor/pos/products")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/vendor/pos/products").with(user("alice"))).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/vendor/pos/sales").with(user("alice").authorities(()->"POS_SALE_CREATE"))).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/vendor/pos/sales").with(csrf()).with(user("alice").authorities(()->"POS_SALE_CREATE",()->"POS_TENDER_CASH_RECORD",()->"POS_RECEIPT_VIEW"))
   .header("Idempotency-Key","forged-01").contentType("application/json").content("{\"tenantId\":2,\"sessionId\":\"x\",\"items\":[{\"productId\":1,\"quantity\":1}],\"cashReceived\":110}"))
   .andExpect(status().isBadRequest());
 }
 @Test void httpCheckoutUsesAuthenticatedMembership() throws Exception {
  String payload="{\"sessionId\":\""+session+"\",\"items\":[{\"productId\":1,\"quantity\":1}],\"cashReceived\":120,\"invoiceRequested\":true}";
  mvc.perform(post("/api/v1/vendor/pos/sales").with(csrf()).with(user("alice").authorities(()->"POS_SALE_CREATE",()->"POS_TENDER_CASH_RECORD",()->"POS_RECEIPT_VIEW"))
   .header("Idempotency-Key","http-sale1").contentType("application/json").content(payload))
   .andExpect(status().isOk()).andExpect(jsonPath("$.customer_id").doesNotExist()).andExpect(jsonPath("$.total").value(110)).andExpect(jsonPath("$.invoices[0].seller_name").value("Vendor A"));
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
  checkout.cashSale(a.cashier(),"drawer-sale",sale(1,"120"));
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
  employee("charlie",1,1);
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
  String path="/api/v1/vendor/pos/sessions/"+session+"/cash-movements";
  String body="{\"type\":\"CASH_OUT\",\"amount\":10,\"reason\":\"Supplies\"}";
  mvc.perform(post(path).with(csrf()).contentType("application/json").content(body).header("Idempotency-Key","http-move1")).andExpect(status().isUnauthorized());
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
  String bobSession=(String)service.open(b,new PosService.OpenSession(2,BigDecimal.ZERO)).get("id");
  employee("charlie",1,1);
  update("INSERT INTO registers VALUES(3,1,1)");
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
  checkout.cashSale(a.cashier(),"resume-sale",sale(1,"120"));
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
  String path="/api/v1/vendor/pos/sessions/"+session;
  mvc.perform(get("/api/v1/vendor/pos/sessions/active")).andExpect(status().isUnauthorized());
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
  employee(name,1,1);
  return new PosService.Scope(1,1,name);
 }
 @Test void discrepancyRequiresReasonAndFreezesTheCountAndRegister(){
  assertThatThrownBy(()->service.close(a,session,BigDecimal.ZERO," "));
  assertThat(service.closeStatus(a,session).state()).isEqualTo("OPEN");
  var closing=service.close(a,session,BigDecimal.ZERO,"Drawer shortage");
  assertThat(closing.state()).isEqualTo("CLOSING");assertThat(closing.variance()).isEqualByComparingTo("-50");
  assertThat(service.close(a,session,BigDecimal.ZERO,"Drawer shortage").requestId()).isEqualTo(closing.requestId());
  assertThatThrownBy(()->service.close(a,session,new BigDecimal("50"),"Changed count"));
  assertThatThrownBy(()->checkout.cashSale(a.cashier(),"frozen-sale",sale(1,"110")));
  assertThatThrownBy(()->service.cashMovement(a,session,"frozen-cash",movement(PosService.MovementType.CASH_IN,"1","Float")));
  assertThatThrownBy(()->service.open(a,new PosService.OpenSession(1,BigDecimal.ZERO)));
  assertThatThrownBy(()->update("INSERT INTO pos_sessions(id,tenant_id,branch_id,cashier,register_id,state,opening_float) VALUES('occupied',1,1,'alice',1,'OPEN',0)"))
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
  String list="/api/v1/vendor/pos/sessions/pending-variances",path="/api/v1/vendor/pos/sessions/"+session+"/approve-variance";
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
  for(int i=0;i<2;i++)mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"POS_SESSION_CLOSE")).contentType("application/json").content(body))
   .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CLOSING")).andExpect(jsonPath("$.requestId").value(key)).andExpect(jsonPath("$.variance").value(-50));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE action='SHIFT_VARIANCE_SUBMIT'",Integer.class)).isEqualTo(1);
 }
 void customers(){
  update("UPDATE tenants SET slug=CONCAT('vendor-',id),online_branch_id=id");
  update("INSERT INTO users VALUES(100,'customer',?,TRUE),(101,'other-customer',?,TRUE)",encoder.encode("CustomerPassword"),encoder.encode("OtherPassword"));
  update("INSERT INTO customer_profiles VALUES(100,100),(101,101)");
 }
 @Test void anonymousMarketplaceExposesOnlyActivePublishedCatalogue() throws Exception {
  customers();update("INSERT INTO products(id,tenant_id,sku,barcode,name,price,tax_rate,published) VALUES(3,1,'SECRET',NULL,'Private draft',1,0,FALSE)");
  mvc.perform(get("/api/v1/marketplace/vendors/vendor-1/products")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].name").value("Panel"));
  update("UPDATE tenants SET active=FALSE WHERE id=2");
  mvc.perform(get("/api/v1/marketplace/vendors")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
  mvc.perform(get("/api/v1/marketplace/vendors/vendor-2/products")).andExpect(status().isNotFound());
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
  var receipt=checkout.cashSale(a.cashier(),"shared-stock",sale(1,"120"));
  assertThat((BigDecimal)receipt.get("total")).isEqualByComparingTo(cart.total());
  assertThat(catalogue.products("vendor-1").getFirst().inStock()).isFalse();
  assertThatThrownBy(()->marketplace.quote(100));
  assertThat(marketplace.cart(100).items().getFirst().available()).isFalse();
  update("UPDATE inventory SET quantity=5 WHERE tenant_id=1");
  update("UPDATE products SET price=200,tax_rate=5 WHERE id=1");
  assertThat(marketplace.quote(100).total()).isEqualByComparingTo("210");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);
  update("UPDATE products SET published=FALSE WHERE id=1");
  assertThat(marketplace.cart(100).items().getFirst().name()).isEqualTo("Product unavailable");
  assertThat(marketplace.cart(100).items().getFirst().unitPrice()).isNull();
  assertThatThrownBy(()->marketplace.quote(100));
 }
 @Test void customerAuthenticationPermissionsAndCsrfProtectCart() throws Exception {
  customers();String path="/api/v1/customer/cart/items",body="{\"vendorSlug\":\"vendor-1\",\"productId\":1,\"quantity\":1}";
  mvc.perform(get("/api/v1/customer/cart")).andExpect(status().isUnauthorized());
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
  customers();var request=new CartService.Item("vendor-1",1,2);var cart=marketplace.put(100,request);
  marketplace.put(100,request);
  assertThat(marketplace.cart(100).items().getFirst().quantity()).isEqualTo(2);
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isEqualTo(1);
  assertThatThrownBy(()->update("INSERT INTO cart_items VALUES(1,?,2,1)",cart.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
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
 @Test void globalIdentityCannotDuplicateEmployeeAndDisabledCustomerTokensAreRejected() throws Exception {
  customers();assertThatThrownBy(()->update("INSERT INTO users VALUES(102,'alice','unused',TRUE)")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  mvc.perform(get("/api/v1/customer/cart").with(bearer("alice","StaffPasswordForTests"))).andExpect(status().isForbidden());
  var token=bearer("customer","CustomerPassword");update("UPDATE users SET enabled=FALSE WHERE username='customer'");mvc.perform(get("/api/v1/customer/cart").with(token)).andExpect(status().isUnauthorized());
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
  update("UPDATE products SET published=FALSE WHERE id=1");
  assertThatThrownBy(()->marketplace.restore(100,new CartService.Restore(first.id(),second.id())));
  assertThat(marketplace.cart(100).id()).isEqualTo(second.id());
  update("UPDATE products SET published=TRUE WHERE id=1");update("UPDATE tenants SET active=FALSE WHERE id=1");
  assertThatThrownBy(()->marketplace.restore(100,new CartService.Restore(first.id(),second.id())));
  assertThat(marketplace.cart(100).id()).isEqualTo(second.id());
 }
 @Test void jpaStockAndJdbcFinancialWritesShareOneRollbackBoundary(){
  assertThat(transactions).isInstanceOf(org.springframework.orm.jpa.JpaTransactionManager.class);
  assertThatThrownBy(()->new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->{
   stock.issue(1,1,1,1);
   assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isZero();
   update("INSERT INTO pos_audit(id,tenant_id,actor,action,resource_id) VALUES('rollback',1,'alice','ROLLBACK_TEST','test')");
   throw new IllegalStateException("Simulate failure after stock and financial work");
  })).isInstanceOf(IllegalStateException.class);
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE id='rollback'",Integer.class)).isZero();
 }
 @Test void archivedCartHttpPermissionsAndCsrfAreEnforced() throws Exception {
  customers();var first=marketplace.put(100,new CartService.Item("vendor-1",1,1));
  var second=marketplace.switchVendor(100,new CartService.Switch(first.id(),"vendor-2",2,1));
  mvc.perform(get("/api/v1/customer/cart/history")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/customer/cart/history").with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/customer/cart/history").with(user("other-customer").authorities(()->"CUSTOMER"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
  String payload="{\"archivedCartId\":\""+first.id()+"\",\"expectedCartId\":\""+second.id()+"\"}";
  mvc.perform(post("/api/v1/customer/cart/restore").with(user("customer").authorities(()->"CUSTOMER")).contentType("application/json").content(payload)).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/customer/cart/restore").with(csrf()).with(user("customer").authorities(()->"CUSTOMER")).contentType("application/json").content(payload)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(first.id()));
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
 com.saumya.pos.checkout.CheckoutService.PosDraft posDraft(){return new CheckoutService.PosDraft(session,List.of(new CheckoutService.Item(1,1)),true,null,null);}
 com.saumya.pos.checkout.CheckoutService.Draft onlineDraft(String key){
  var cart=marketplace.put(100,new CartService.Item("vendor-1",1,1));
  return checkout.online("customer",key,new com.saumya.pos.checkout.CheckoutService.OnlineDraft(cart.id()));
 }
 void configureOnline(){customers();update("UPDATE tenants SET online_branch_id=id");}
 @Test void onlineDraftReservesCanonicalStockAndCancelReleasesExactlyOnce(){
  configureOnline();var draft=onlineDraft("online-hold1");
  assertThat(draft.channel()).isEqualTo("ONLINE");assertThat(draft.status()).isEqualTo("DRAFT");assertThat(draft.paymentAvailable()).isTrue();
  assertThat(db.queryForObject("SELECT customer_id FROM orders",Long.class)).isEqualTo(100);
  assertThat(db.queryForMap("SELECT branch_id,cashier,session_id,cash_received,receipt_number FROM orders").values()).allMatch(Objects::isNull);
  assertThat(stock.available(1,1)).isZero();assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isEqualTo(1);
  assertThatThrownBy(()->checkout.cashSale(a.cashier(),"competing-pos",sale(1,"110")));
  var cart=marketplace.cart(100);
  assertThat(checkout.online("customer","online-hold1",new com.saumya.pos.checkout.CheckoutService.OnlineDraft(cart.id())).id()).isEqualTo(draft.id());
  assertThatThrownBy(()->checkout.online("customer","online-new1",new com.saumya.pos.checkout.CheckoutService.OnlineDraft(cart.id())));
  assertThatThrownBy(()->checkout.cancelOnline("other-customer",draft.id(),"Not mine"));
  checkout.cancelOnline("customer",draft.id(),"Changed mind");checkout.cancelOnline("customer",draft.id(),"Retry");
  assertThat(stock.available(1,1)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT reserved FROM inventory WHERE tenant_id=1",Integer.class)).isZero();
  checkout.cashSale(a.cashier(),"after-release",sale(1,"110"));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM orders WHERE status='PAID'",Integer.class)).isEqualTo(1);
 }
 @Test void posReservedCashFinalizationKeepsOneOrderReceiptAndInvoice(){
  var draft=checkout.pos("alice","pos-reserve1",posDraft());
  assertThat(checkout.pos("alice","pos-reserve1",posDraft()).id()).isEqualTo(draft.id());
  assertThatThrownBy(()->service.close(a,session,new BigDecimal("50")));
  assertThatThrownBy(()->checkout.finalizeCash("alice",draft.id(),BigDecimal.ONE));
  assertThatThrownBy(()->checkout.finalizeCash("bob",draft.id(),new BigDecimal("120")));
  update("UPDATE products SET price=101 WHERE id=1");
  assertThatThrownBy(()->checkout.finalizeCash("alice",draft.id(),new BigDecimal("120")));
  update("UPDATE products SET price=100 WHERE id=1");
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
  update("UPDATE orders SET expires_at=TIMESTAMP '2020-01-01 00:00:00' WHERE id=?",draft.id());
  assertThatThrownBy(()->checkout.finalizeCash("alice",draft.id(),new BigDecimal("110")));
  checkout.expire(1,draft.id());checkout.expire(1,draft.id());
  assertThat(stock.available(1,1)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT status FROM orders",String.class)).isEqualTo("EXPIRED");
  assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_ledger",Integer.class)).isZero();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_invoices",Integer.class)).isZero();
  checkout.pos("alice","after-expiry",posDraft());
 }
 @Test void reservationFailureRollsBackEveryLineAndOrder(){
  update("INSERT INTO products(id,tenant_id,sku,barcode,name,price,tax_rate,published) VALUES(3,1,'P3',NULL,'Missing stock',100,0,TRUE)");
  var request=new CheckoutService.PosDraft(session,List.of(new CheckoutService.Item(1,1),new CheckoutService.Item(3,1)),false,null,null);
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
  mvc.perform(post("/api/v1/customer/checkout/drafts").with(csrf()).contentType("application/json").content(payload).header("Idempotency-Key","http-draft1")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/v1/customer/checkout/drafts").with(user("customer").authorities(()->"CUSTOMER")).contentType("application/json").content(payload).header("Idempotency-Key","http-draft1")).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/customer/checkout/drafts").with(csrf()).with(user("customer").authorities(()->"CUSTOMER")).contentType("application/json").content(payload).header("Idempotency-Key","http-draft1")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.paymentAvailable").value(true));
  mvc.perform(get("/api/v1/customer/checkout/drafts").with(user("other-customer").authorities(()->"CUSTOMER"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
  var draft=checkout.onlineList("customer").getFirst();
  mvc.perform(post("/api/v1/customer/checkout/drafts/"+draft.id()+"/cancel").with(csrf()).with(user("other-customer").authorities(()->"CUSTOMER")).contentType("application/json").content("{\"reason\":\"Not mine\"}")).andExpect(status().isNotFound());
  assertThatThrownBy(()->update("UPDATE orders SET branch_id=1 WHERE id=?",draft.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->update("UPDATE inventory SET reserved=2 WHERE tenant_id=1")).isInstanceOf(org.springframework.dao.DataAccessException.class);
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
  mvc.perform(post("/api/v1/vendor/pos/drafts").with(csrf()).with(user("alice").authorities(()->"POS_VIEW")).header("Idempotency-Key","pos-http-key").contentType("application/json").content(body)).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/vendor/pos/drafts").with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_SALE_CREATE")).header("Idempotency-Key","pos-http-key").contentType("application/json").content(body.replace("\"items\"","\"tenantId\":2,\"items\""))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/v1/vendor/pos/drafts").with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_SALE_CREATE")).header("Idempotency-Key","pos-http-key").contentType("application/json").content(body)).andExpect(status().isOk());
  var draft=checkout.posList("alice",session).getFirst();
  mvc.perform(get("/api/v1/vendor/pos/receipts/"+draft.id()).with(user("alice").authorities(()->"POS_RECEIPT_VIEW"))).andExpect(status().isNotFound());
  mvc.perform(post("/api/v1/vendor/pos/drafts/"+draft.id()+"/cancel").with(csrf()).with(user("alice").authorities(()->"POS_VIEW")).contentType("application/json").content("{\"reason\":\"Cancel\"}")).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/vendor/pos/drafts/"+draft.id()+"/finalize-cash").with(csrf()).with(user("alice").authorities(()->"POS_SALE_CREATE")).contentType("application/json").content("{\"cashReceived\":120}")).andExpect(status().isForbidden());
  assertThat(checkout.posList("bob",session)).isEmpty();
 }

 @Test void orderHistoryScopesPaginationDatesAndImmutableDetails() throws Exception {
  var receipt=checkout.cashSale(a.cashier(),"history-cash1",sale(1,"120"));String id=(String)receipt.get("id");
  employee("colleague",1,1);
  update("INSERT INTO branches(id,tenant_id,name) VALUES(3,1,'Other branch')");
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
  checkout.cancelOnline("customer",draft.id(),"Buying later");
  mvc.perform(get("/api/v1/customer/orders").with(user("customer").authorities(()->"CUSTOMER")).param("status","CANCELLED")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].channel").value("ONLINE"));
  mvc.perform(get("/api/v1/customer/orders/"+draft.id()).with(user("customer").authorities(()->"CUSTOMER"))).andExpect(status().isOk()).andExpect(jsonPath("$.cancellationReason").value("Buying later")).andExpect(jsonPath("$.cashReceived").doesNotExist());
  mvc.perform(get("/api/v1/customer/orders").with(user("other-customer").authorities(()->"CUSTOMER"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  mvc.perform(get("/api/v1/customer/orders/"+draft.id()).with(user("other-customer").authorities(()->"CUSTOMER"))).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/vendor/pos/orders/"+draft.id()).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isNotFound());
  var cash=checkout.cashSale(a.cashier(),"history-guest",sale(1,"120"));
  mvc.perform(get("/api/v1/customer/orders/"+cash.get("id")).with(user("customer").authorities(()->"CUSTOMER"))).andExpect(status().isNotFound());
 }
 @Test void orderHistoryRequiresPermissionsAndRejectsInvalidFilters() throws Exception {
  mvc.perform(get("/api/v1/vendor/pos/orders")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/customer/orders").with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/vendor/pos/orders").with(user("alice"))).andExpect(status().isForbidden());
  for(String[] parameter:List.of(new String[]{"size","51"},new String[]{"page","-1"},new String[]{"status","REFUNDED"},new String[]{"reference","%"},new String[]{"from","invalid"}))
   mvc.perform(get("/api/v1/vendor/pos/orders").with(user("alice").authorities(()->"POS_VIEW")).param(parameter[0],parameter[1])).andExpect(status().isBadRequest());
 }

 @Test void orderHistoryPaginationIsStableForEqualTimestampsAndUtcDayBounds() throws Exception {
  update("UPDATE inventory SET quantity=3 WHERE tenant_id=1");var ids=new ArrayList<String>();
  for(int n=0;n<3;n++)ids.add((String)checkout.cashSale(a.cashier(),"history-page"+n,sale(1,"120")).get("id"));
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
  update("UPDATE products SET published=TRUE WHERE id=1");update("UPDATE tenants SET active=FALSE WHERE id=1");
  mvc.perform(get("/api/v1/marketplace/vendors/vendor-1/products/1")).andExpect(status().isNotFound());
 }
 @Test void salesReportSeparatesPaidTaxCashAndOnlineReservationsWithoutDoubleCounting() throws Exception {
  configureOnline();update("UPDATE inventory SET quantity=10 WHERE tenant_id=1");
  update("INSERT INTO products(id,tenant_id,sku,barcode,name,price,tax_rate,published) VALUES(3,1,'CABLE',NULL,'Cable',20,0,TRUE)");update("INSERT INTO inventory(tenant_id,branch_id,product_id,quantity) VALUES(1,1,3,5)");
  checkout.cashSale(a.cashier(),"report-paid1",new CheckoutService.CashSale(session,List.of(new CheckoutService.Item(1,1),new CheckoutService.Item(3,2)),new BigDecimal("200"),true,null,null));
  var cancelled=onlineDraft("report-cancel");checkout.cancelOnline("customer",cancelled.id(),"Buying later");onlineDraft("report-unpaid");
  update("UPDATE products SET price=999,tax_rate=80 WHERE tenant_id=1");
  mvc.perform(get("/api/v1/vendor/pos/reports/sales").with(user("alice").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW")).param("tenantId","2").param("branchId","2"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.branchId").value(1)).andExpect(jsonPath("$.totals.paidOrders").value(1)).andExpect(jsonPath("$.totals.sales").value(150)).andExpect(jsonPath("$.totals.tax").value(10)).andExpect(jsonPath("$.totals.beforeTax").value(140)).andExpect(jsonPath("$.totals.cashReceived").value(200)).andExpect(jsonPath("$.totals.changeGiven").value(50)).andExpect(jsonPath("$.totals.cashApplied").value(150)).andExpect(jsonPath("$.totals.units").value(3)).andExpect(jsonPath("$.totals.invoices").value(1)).andExpect(jsonPath("$.totals.unpaidOrders").value(1)).andExpect(jsonPath("$.totals.unpaidAmount").value(110)).andExpect(jsonPath("$.totals.cancelledOrders").value(1));
  mvc.perform(get("/api/v1/vendor/pos/reports/sales").with(user("alice").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW")).param("channel","ONLINE")).andExpect(status().isOk()).andExpect(jsonPath("$.totals.paidOrders").value(0)).andExpect(jsonPath("$.totals.sales").value(0)).andExpect(jsonPath("$.totals.unpaidOrders").value(1)).andExpect(jsonPath("$.channels.length()").value(1));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(3);assertThat(db.queryForObject("SELECT reserved FROM inventory WHERE tenant_id=1 AND product_id=1",Integer.class)).isEqualTo(1);
 }
 @Test void salesReportUsesFinalizationDayAndRejectsForeignBranchesAndMissingGrants() throws Exception {
  var draft=checkout.pos("alice","report-finalize",posDraft());
  var today=java.time.LocalDate.now(java.time.ZoneOffset.UTC);var yesterday=today.minusDays(1);
  update("UPDATE orders SET created_at=? WHERE id=?",yesterday.atStartOfDay(),draft.id());checkout.finalizeCash("alice",draft.id(),new BigDecimal("120"));
  mvc.perform(get("/api/v1/vendor/pos/reports/sales").with(user("alice").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW")).param("from",today.toString()).param("to",today.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.totals.paidOrders").value(1)).andExpect(jsonPath("$.days.length()").value(1));
  mvc.perform(get("/api/v1/vendor/pos/reports/sales").with(user("alice").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW")).param("from",yesterday.toString()).param("to",yesterday.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.totals.paidOrders").value(0));
  update("INSERT INTO branches(id,tenant_id,name) VALUES(3,1,'Other branch')");employee("other-branch",1,3);
  for(String actor:List.of("bob","other-branch"))mvc.perform(get("/api/v1/vendor/pos/reports/sales").with(user(actor).authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totals.paidOrders").value(0));
  mvc.perform(get("/api/v1/vendor/pos/reports/sales")).andExpect(status().isUnauthorized());
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
  update("UPDATE tenants SET active=FALSE WHERE id=1");mvc.perform(get("/api/v1/marketplace/vendors/vendor-1/categories")).andExpect(status().isNotFound());
 }
 @Test void approvedPromotionsAreSharedAuditedAndSavedOnCashReceiptsAndReports() throws Exception {
  configureOnline();update("UPDATE inventory SET quantity=5 WHERE tenant_id=1");supervisor("manager");
  String path="/api/v1/vendor/catalog/products/1/promotion",body="{\"percent\":10,\"reason\":\"Approved solar offer\"}";
  mvc.perform(put(path).with(csrf()).with(user("manager").authorities(()->"CATALOG_PROMOTION_MANAGE",()->"POS_DISCOUNT_APPROVE")).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.price").value(90));
  mvc.perform(put(path).with(csrf()).with(user("manager").authorities(()->"CATALOG_PROMOTION_MANAGE",()->"POS_DISCOUNT_APPROVE")).contentType("application/json").content(body)).andExpect(status().isOk());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM catalog_promotion_audit",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT actor FROM catalog_promotion_audit",String.class)).isEqualTo("manager");
  mvc.perform(get("/api/v1/marketplace/vendors/vendor-1/products/1")).andExpect(status().isOk()).andExpect(jsonPath("$.listPrice").value(100)).andExpect(jsonPath("$.unitTotal").value(99));
  var cart=marketplace.put(100,new CartService.Item("vendor-1",1,1));assertThat(cart.total()).isEqualByComparingTo("99");assertThat(cart.items().getFirst().discount()).isEqualByComparingTo("10");
  var receipt=checkout.cashSale(a.cashier(),"promotion-cash",sale(1,"100"));assertThat((BigDecimal)receipt.get("total")).isEqualByComparingTo("99");
  var item=(Map<?,?>)((List<?>)receipt.get("items")).getFirst();assertThat((BigDecimal)item.get("list_price")).isEqualByComparingTo("100");assertThat((BigDecimal)item.get("discount")).isEqualByComparingTo("10");
  promotions.approve(1,"manager",1,new com.saumya.pos.catalog.PromotionService.Change(new BigDecimal("20"),"New approved offer"));
  var copy=receiptReader.reprint(a,(String)receipt.get("id"));assertThat((BigDecimal)copy.get("total")).isEqualByComparingTo("99");
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
  promotions.approve(1,"alice",1,new com.saumya.pos.catalog.PromotionService.Change(BigDecimal.TEN,"Approved offer"));
  var draft=checkout.pos("alice","promotion-draft",posDraft());assertThat(draft.total()).isEqualByComparingTo("99");assertThat(draft.items().getFirst().discount()).isEqualByComparingTo("10");
  promotions.approve(1,"alice",1,new com.saumya.pos.catalog.PromotionService.Change(new BigDecimal("20"),"Changed offer"));
  assertThatThrownBy(()->checkout.finalizeCash("alice",draft.id(),new BigDecimal("100"))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT reserved FROM inventory WHERE tenant_id=1",Integer.class)).isEqualTo(1);
  checkout.cancelPos("alice",draft.id(),"Requote changed promotion");var next=checkout.pos("alice","promotion-next",posDraft());checkout.finalizeCash("alice",next.id(),new BigDecimal("100"));assertThat(next.total()).isEqualByComparingTo("88");
 }
 @Test void promotionAndCashSaleSerializeOnTheSameProduct() throws Exception {
  try(var pool=Executors.newFixedThreadPool(2)){
   Callable<Object> approve=()->promotions.approve(1,"alice",1,new com.saumya.pos.catalog.PromotionService.Change(BigDecimal.TEN,"Concurrent approved offer"));
   Callable<Object> pay=()->checkout.cashSale(a.cashier(),"promotion-race",sale(1,"120"));
   for(var result:pool.invokeAll(List.of(approve,pay)))result.get();
  }
  BigDecimal total=db.queryForObject("SELECT total FROM orders",BigDecimal.class);assertThat(total).isIn(new BigDecimal("110.00"),new BigDecimal("99.00"));assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isZero();assertThat(db.queryForObject("SELECT COUNT(*) FROM catalog_promotion_audit",Integer.class)).isEqualTo(1);
 }

 void loadSolarDemoSeed() throws Exception {
  var file=java.nio.file.Path.of(System.getProperty("basedir"),"..","docs","demo","add-solar-demo-products.sql");
  String sql=java.nio.file.Files.readString(file).replace("USE solar;","");
  try(var connection=db.getDataSource().getConnection()){
   // H2 lacks MySQL's START TRANSACTION spelling; all seed DML is identical in both jobs.
   if(connection.getMetaData().getDatabaseProductName().equals("H2"))sql=sql.replace("START TRANSACTION;","SET AUTOCOMMIT FALSE;");
   boolean autoCommit=connection.getAutoCommit();
   try{new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.ByteArrayResource(sql.getBytes(java.nio.charset.StandardCharsets.UTF_8))).populate(connection);}
   finally{if(!connection.getAutoCommit())connection.rollback();connection.setAutoCommit(autoCommit);}
  }
 }
 void demoStore(){update("INSERT INTO tenants(id,name,active,slug) VALUES(900001,'Demo store',TRUE,'vendor-900001')");update("INSERT INTO branches(id,tenant_id,name) VALUES(900001,900001,'Demo outlet')");}
 @Test void solarDemoSeedAddsCategorizedProductsAndPreservesEditsStockHoldsAndSales() throws Exception {
  var receipt=checkout.cashSale(a.cashier(),"demo-existing",sale(1,"120"));demoStore();loadSolarDemoSeed();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM products WHERE tenant_id=900001",Integer.class)).isEqualTo(18);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM product_categories WHERE tenant_id=900001",Integer.class)).isEqualTo(18);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM catalog_categories WHERE tenant_id=900001",Integer.class)).isEqualTo(6);
  promotions.approve(900001,"demo-manager",900101,new com.saumya.pos.catalog.PromotionService.Change(BigDecimal.TEN,"Preserve demo offer"));
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
  demoStore();update("UPDATE tenants SET active=FALSE WHERE id=900001");loadSolarDemoSeed();assertThat(db.queryForObject("SELECT COUNT(*) FROM catalog_categories",Integer.class)).isZero();
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
 @Test void returnIntakeIsDisabledByDefaultAndRetryAfterShiftCloseDoesNotRefund(){
  var receipt=checkout.cashSale(a.cashier(),"return-sale1",sale(1,"120"));String order=(String)receipt.get("id");
  assertThat(returns.eligibility("alice",order).eligible()).isFalse();assertThatThrownBy(()->returns.create("alice","return-start1",returnRequest(order,1))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  enableReturns();var returned=returns.create("alice","return-start1",returnRequest(order,1));assertThat(returned.state()).isEqualTo("REQUESTED");assertThat(returned.refundStatus()).isEqualTo("NOT_INITIATED");
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isZero();assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_return_dispositions",Integer.class)).isZero();
  assertThat(service.reconciliation(a,session).expectedCash()).isEqualByComparingTo("160");service.close(a,session,new BigDecimal("160"));
  assertThat(returns.create("alice","return-start1",returnRequest(order,1)).id()).isEqualTo(returned.id());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_return_events",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_cash_movements",Integer.class)).isZero();
  assertThatThrownBy(()->returns.create("alice","return-start1",returnRequest(order,2))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }
 @Test void independentReviewAndQuarantineAreIdempotentAndNeverRestockDamagedGoods(){
  enableReturns();supervisor("manager");var receipt=checkout.cashSale(a.cashier(),"return-sale2",sale(1,"120"));var request=returns.create("alice","return-case2",returnRequest((String)receipt.get("id"),1));
  assertThatThrownBy(()->returns.review("alice",request.id(),approveReturn())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThatThrownBy(()->returns.inspect("manager",request.id(),inspectReturn("SELLABLE","RESTOCK"))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  returns.review("manager",request.id(),approveReturn());returns.review("manager",request.id(),approveReturn());
  assertThatThrownBy(()->returns.inspect("manager",request.id(),inspectReturn("DAMAGED","RESTOCK"))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  var inspected=returns.inspect("manager",request.id(),inspectReturn("DAMAGED","QUARANTINE"));assertThat(inspected.state()).isEqualTo("INSPECTED");assertThat(inspected.refundStatus()).isEqualTo("NOT_INITIATED");
  returns.inspect("manager",request.id(),inspectReturn("DAMAGED","QUARANTINE"));returns.review("manager",request.id(),approveReturn());
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isZero();assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_return_dispositions",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_return_events",Integer.class)).isEqualTo(3);
  assertThatThrownBy(()->returns.inspect("manager",request.id(),inspectReturn("SELLABLE","RESTOCK"))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThatThrownBy(()->returns.create("alice","return-again",returnRequest((String)receipt.get("id"),1))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }
 @Test void rejectedIntakeReleasesTheOriginalQuantityBalanceWithoutIssuingStock(){
  enableReturns();supervisor("manager");var receipt=checkout.cashSale(a.cashier(),"return-sale3",sale(1,"120"));String order=(String)receipt.get("id");var request=returns.create("alice","return-case3",returnRequest(order,1));
  assertThat(returns.eligibility("alice",order).items().getFirst().remaining()).isZero();returns.review("manager",request.id(),new com.saumya.pos.returns.ReturnService.Review("REJECTED","Proof did not match the original goods"));
  assertThat(returns.eligibility("alice",order).items().getFirst().remaining()).isEqualTo(1);returns.create("alice","return-new3",returnRequest(order,1));assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1",Integer.class)).isZero();
 }
 @Test void inspectionRollsBackAllStockAndDispositionRowsIfAnyRestockFails(){
  enableReturns();supervisor("manager");update("INSERT INTO products(id,tenant_id,sku,barcode,name,price,tax_rate,published) VALUES(3,1,'CABLE',NULL,'Cable',20,0,TRUE)");update("INSERT INTO inventory(tenant_id,branch_id,product_id,quantity) VALUES(1,1,3,1)");
  var receipt=checkout.cashSale(a.cashier(),"return-sale4",new CheckoutService.CashSale(session,List.of(new CheckoutService.Item(1,1),new CheckoutService.Item(3,1)),new BigDecimal("150"),false,null,null));
  var command=new com.saumya.pos.returns.ReturnService.Create((String)receipt.get("id"),session,List.of(new com.saumya.pos.returns.ReturnService.Item(1,1),new com.saumya.pos.returns.ReturnService.Item(3,1)),"Goods returned","ORIGINAL_RECEIPT","Receipt and both goods checked");var request=returns.create("alice","return-case4",command);returns.review("manager",request.id(),approveReturn());
  var inspection=new com.saumya.pos.returns.ReturnService.Inspect(List.of(new com.saumya.pos.returns.ReturnService.Inspection(1,"SELLABLE","RESTOCK","Panel checked"),new com.saumya.pos.returns.ReturnService.Inspection(3,"SELLABLE","RESTOCK","Cable checked")));
  update("UPDATE inventory SET quantity=? WHERE tenant_id=1 AND product_id=3",Integer.MAX_VALUE);assertThatThrownBy(()->returns.inspect("manager",request.id(),inspection)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThat(db.queryForObject("SELECT quantity FROM inventory WHERE tenant_id=1 AND product_id=1",Integer.class)).isZero();assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_return_dispositions",Integer.class)).isZero();assertThat(returns.get("alice",request.id()).state()).isEqualTo("APPROVED");
  update("UPDATE inventory SET quantity=0 WHERE tenant_id=1 AND product_id=3");returns.inspect("manager",request.id(),inspection);returns.inspect("manager",request.id(),inspection);
  assertThat(stock.atBranch(1,1,1)).isEqualTo(1);assertThat(stock.atBranch(1,1,3)).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_return_dispositions",Integer.class)).isEqualTo(2);assertThat(service.reconciliation(a,session).expectedCash()).isEqualByComparingTo("180");
 }
 @Test void returnWindowAndOriginalPaidPosChannelAreEnforced(){
  configureOnline();enableReturns();var draft=checkout.pos("alice","not-paid-return",posDraft());assertThatThrownBy(()->returns.eligibility("alice",draft.id())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);checkout.cancelPos("alice",draft.id(),"Not sold");
  var online=onlineDraft("online-no-return");assertThatThrownBy(()->returns.eligibility("alice",online.id())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);checkout.cancelOnline("customer",online.id(),"Cancel hold");
  var receipt=checkout.cashSale(a.cashier(),"return-sale5",sale(1,"120"));String order=(String)receipt.get("id");update("UPDATE orders SET status_changed_at=? WHERE id=?",java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusDays(31),order);
  assertThat(returns.eligibility("alice",order).eligible()).isFalse();assertThatThrownBy(()->returns.create("alice","return-old5",returnRequest(order,1))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }
 @Test void returnHttpPermissionsCsrfOriginalOwnershipAndForgedFieldsFailClosed() throws Exception {
  enableReturns();supervisor("manager");var receipt=checkout.cashSale(a.cashier(),"return-sale6",sale(1,"120"));String order=(String)receipt.get("id"),path="/api/v1/vendor/pos/returns";
  String body="{\"orderId\":\""+order+"\",\"sessionId\":\""+session+"\",\"items\":[{\"productId\":1,\"quantity\":1}],\"reason\":\"Buyer return\",\"proof\":\"ORIGINAL_RECEIPT\",\"proofNote\":\"Receipt checked\"}";
  mvc.perform(get(path)).andExpect(status().isUnauthorized());mvc.perform(get(path).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isForbidden());
  mvc.perform(post(path).with(user("alice").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW",()->"POS_RETURN_CREATE")).header("Idempotency-Key","http-return6").contentType("application/json").content(body)).andExpect(status().isForbidden());
  for(String forged:List.of(body.replace("\"items\"","\"tenantId\":2,\"items\""),body.replace("[{\"productId\":1,\"quantity\":1}]","[null]")))mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW",()->"POS_RETURN_CREATE")).header("Idempotency-Key","http-return6").contentType("application/json").content(forged)).andExpect(status().isBadRequest());
  mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW",()->"POS_RETURN_CREATE")).header("Idempotency-Key","http-return6").contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.refundStatus").value("NOT_INITIATED"));
  var request=returns.list("alice","REQUESTED").getFirst();mvc.perform(get(path+"/"+request.id()).with(user("bob").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW"))).andExpect(status().isNotFound());
  mvc.perform(get(path).with(user("bob").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
  mvc.perform(post(path+"/"+request.id()+"/review").with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW",()->"POS_RETURN_APPROVE")).contentType("application/json").content("{\"decision\":\"APPROVED\",\"reason\":\"Reviewed\"}")).andExpect(status().isForbidden());
 }
 @Test void concurrentReturnClaimsCannotExceedSoldQuantity() throws Exception {
  enableReturns();var receipt=checkout.cashSale(a.cashier(),"return-sale7",sale(1,"120"));String order=(String)receipt.get("id");
  try(var pool=Executors.newFixedThreadPool(2)){Callable<Boolean> first=()->{try{returns.create("alice","return-race1",returnRequest(order,1));return true;}catch(org.springframework.web.server.ResponseStatusException e){return false;}};Callable<Boolean> second=()->{try{returns.create("alice","return-race2",returnRequest(order,1));return true;}catch(org.springframework.web.server.ResponseStatusException e){return false;}};int successes=0;for(var result:pool.invokeAll(List.of(first,second)))if(result.get())successes++;assertThat(successes).isEqualTo(1);}
  assertThat(db.queryForObject("SELECT SUM(quantity) FROM pos_return_items",Integer.class)).isEqualTo(1);
 }
 @Test void concurrentIdenticalInspectionsRestockOnlyOnce() throws Exception {
  enableReturns();supervisor("manager");var receipt=checkout.cashSale(a.cashier(),"return-sale8",sale(1,"120"));var request=returns.create("alice","return-case8",returnRequest((String)receipt.get("id"),1));returns.review("manager",request.id(),approveReturn());
  try(var pool=Executors.newFixedThreadPool(2)){Callable<String> inspect=()->returns.inspect("manager",request.id(),inspectReturn("SELLABLE","RESTOCK")).id();for(var result:pool.invokeAll(List.of(inspect,inspect)))assertThat(result.get()).isEqualTo(request.id());}
  assertThat(stock.atBranch(1,1,1)).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM inventory_return_dispositions",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_return_events",Integer.class)).isEqualTo(3);
 }

 @Test void newReturnCanReferenceAClosedOriginalSaleFromANewOwnedShift(){
  enableReturns();var receipt=checkout.cashSale(a.cashier(),"return-old-shift",sale(1,"120"));service.close(a,session,new BigDecimal("160"));
  session=(String)service.open(a,new PosService.OpenSession(1,new BigDecimal("20"))).get("id");
  var request=returns.create("alice","return-new-shift",returnRequest((String)receipt.get("id"),1));assertThat(request.state()).isEqualTo("REQUESTED");assertThat(service.reconciliation(a,session).expectedCash()).isEqualByComparingTo("20");
 }

 String inspectedRefundReturn(){
  enableReturns();supervisor("manager");
  var receipt=checkout.cashSale(a.cashier(),"refund-original",sale(1,"120"));
  var r=returns.create("alice","refund-intake",returnRequest((String)receipt.get("id"),1));
  returns.review("manager",r.id(),approveReturn());returns.inspect("manager",r.id(),inspectReturn("DAMAGED","QUARANTINE"));return r.id();
 }
 String refundSession(String floatAmount){
  update("INSERT INTO registers VALUES(3,1,1)");
  return (String)service.open(new PosService.Scope(1,1,"manager"),new PosService.OpenSession(3,new BigDecimal(floatAmount))).get("id");
 }
 com.saumya.pos.returns.CashRefundService.Pay refundPay(String returned,String paying){
  var q=refunds.quote("manager",returned,paying);
  return new com.saumya.pos.returns.CashRefundService.Pay(returned,paying,"Independent cash handover",q.quoteHash(),"CASH_HANDED_TO_BUYER");
 }
 @Test void cashRefundIsOnceOnlyAndDebitsPayingDrawerWithoutChangingOriginalSale(){
  String returned=inspectedRefundReturn(),paying=refundSession("200");
  assertThatThrownBy(()->refunds.quote("manager",returned,paying)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  update("UPDATE branches SET cash_refunds_enabled=TRUE WHERE id=1");
  var command=refundPay(returned,paying);var paid=refunds.pay("manager","refund-once1",command);
  assertThat(paid.amount()).isEqualByComparingTo("110");assertThat(paid.items()).hasSize(1);
  assertThat(service.reconciliation(new PosService.Scope(1,1,"manager"),paying).expectedCash()).isEqualByComparingTo("90");
  assertThat(service.reconciliation(a,session).expectedCash()).isEqualByComparingTo("160");
  assertThat(returns.get("alice",returned).refundStatus()).isEqualTo("PAID");
  assertThat(stock.atBranch(1,1,1)).isZero();assertThat(db.queryForObject("SELECT status FROM orders",String.class)).isEqualTo("PAID");
  service.close(new PosService.Scope(1,1,"manager"),paying,new BigDecimal("90"));update("UPDATE branches SET cash_refunds_enabled=FALSE WHERE id=1");
  assertThat(refunds.pay("manager","refund-once1",command).id()).isEqualTo(paid.id());
  assertThat(refunds.byReturn("manager",returned).id()).isEqualTo(paid.id());assertThat(refunds.copy("manager",paid.id()).copy()).isTrue();
  assertThatThrownBy(()->refunds.get("bob",paid.id())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_cash_refunds",Integer.class)).isEqualTo(1);
 }
 @Test void cashRefundRejectsInsufficientFundsSelfApprovalAndTaxInvoice(){
  String returned=inspectedRefundReturn(),paying=refundSession("50");update("UPDATE branches SET cash_refunds_enabled=TRUE WHERE id=1");
  assertThatThrownBy(()->refunds.quote("alice",returned,session)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  var q=refunds.quote("manager",returned,paying);assertThat(q.enoughCash()).isFalse();
  assertThatThrownBy(()->refunds.pay("manager","refund-short",refundPay(returned,paying))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  update("UPDATE orders SET tax_invoice=TRUE");
  assertThatThrownBy(()->refunds.quote("manager",returned,paying)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_cash_refunds",Integer.class)).isZero();
 }
 @Test void concurrentRefundRetriesCreateOneImmutablePayment() throws Exception {
  String returned=inspectedRefundReturn(),paying=refundSession("200");update("UPDATE branches SET cash_refunds_enabled=TRUE WHERE id=1");var command=refundPay(returned,paying);
  try(var pool=Executors.newFixedThreadPool(2)){
   var jobs=List.of((Callable<String>)()->refunds.pay("manager","refund-race1",command).id(),()->refunds.pay("manager","refund-race1",command).id());
   var results=pool.invokeAll(jobs);assertThat(results.get(0).get()).isEqualTo(results.get(1).get());
  }
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_cash_refund_items",Integer.class)).isEqualTo(1);
 }
 @Test void refundHttpRequiresIndependentPrivilegesAndCsrf() throws Exception {
  String returned=inspectedRefundReturn(),paying=refundSession("200"),path="/api/v1/vendor/pos/refunds";update("UPDATE branches SET cash_refunds_enabled=TRUE WHERE id=1");
  mvc.perform(get(path)).andExpect(status().isUnauthorized());
  mvc.perform(get(path).with(user("manager").authorities(()->"POS_VIEW"))).andExpect(status().isForbidden());
  mvc.perform(get(path+"/quote/"+returned).param("sessionId",paying).with(user("manager").authorities(()->"POS_VIEW",()->"POS_REFUND_VIEW",()->"POS_REFUND_APPROVE",()->"POS_REFUND_CASH_RECORD"))).andExpect(status().isOk());
  mvc.perform(post(path).with(user("manager").authorities(()->"POS_VIEW",()->"POS_REFUND_VIEW",()->"POS_REFUND_APPROVE",()->"POS_REFUND_CASH_RECORD")).contentType("application/json").content("{}")).andExpect(status().isForbidden());
 }

 @Test void refundAndCashWithdrawalCannotOverdrawPayingSession() throws Exception {
  String returned=inspectedRefundReturn(),paying=refundSession("120");update("UPDATE branches SET cash_refunds_enabled=TRUE WHERE id=1");var command=refundPay(returned,paying);var manager=new PosService.Scope(1,1,"manager");
  try(var pool=Executors.newFixedThreadPool(2)){
   Callable<Boolean> refund=()->{try{refunds.pay("manager","refund-cash-race",command);return true;}catch(org.springframework.web.server.ResponseStatusException e){return false;}};
   Callable<Boolean> withdrawal=()->{try{service.cashMovement(manager,paying,"cash-withdraw-race",movement(PosService.MovementType.CASH_OUT,"20","Supplies"));return true;}catch(org.springframework.web.server.ResponseStatusException e){return false;}};
   int wins=0;for(var result:pool.invokeAll(List.of(refund,withdrawal)))if(result.get())wins++;assertThat(wins).isEqualTo(1);
  }
  assertThat(service.reconciliation(manager,paying).expectedCash()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
 }
 @Test void stalePartialRefundQuoteCannotPayAndFullAllocationMatchesOriginal() {
  enableReturns();supervisor("manager");update("UPDATE inventory SET quantity=3 WHERE tenant_id=1");update("UPDATE products SET price=0.05,tax_rate=10 WHERE id=1");
  var receipt=checkout.cashSale(a.cashier(),"partial-original",new CheckoutService.CashSale(session,List.of(new CheckoutService.Item(1,3)),BigDecimal.ONE,false,null,null));
  String order=(String)receipt.get("id"),paying=refundSession("10");update("UPDATE branches SET cash_refunds_enabled=TRUE WHERE id=1");
  var ids=new ArrayList<String>();for(int i=0;i<3;i++){var r=returns.create("alice","partial-return"+i,returnRequest(order,1));returns.review("manager",r.id(),approveReturn());returns.inspect("manager",r.id(),inspectReturn("DAMAGED","QUARANTINE"));ids.add(r.id());}
  var first=refundPay(ids.get(0),paying);var stale=refundPay(ids.get(1),paying);
  refunds.pay("manager","partial-refund0",first);
  assertThatThrownBy(()->refunds.pay("manager","partial-refund1",stale)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  update("UPDATE products SET price=999,tax_rate=50 WHERE id=1");
  for(int i=1;i<3;i++)refunds.pay("manager","partial-refund"+i,refundPay(ids.get(i),paying));
  assertThat(db.queryForObject("SELECT SUM(amount) FROM pos_cash_refunds",BigDecimal.class)).isEqualByComparingTo((BigDecimal)receipt.get("total"));
 }

 @Test void refundReportUsesPayoutUtcDatesAndDoesNotMultiplyMultiLineCash() throws Exception {
  enableReturns();supervisor("manager");update("INSERT INTO products(id,tenant_id,sku,barcode,name,price,tax_rate,published) VALUES(3,1,'CABLE',NULL,'Cable',20,10,TRUE)");update("INSERT INTO inventory(tenant_id,branch_id,product_id,quantity) VALUES(1,1,3,1)");
  var receipt=checkout.cashSale(a.cashier(),"refund-report-sale",new CheckoutService.CashSale(session,List.of(new CheckoutService.Item(1,1),new CheckoutService.Item(3,1)),new BigDecimal("150"),false,null,null));
  var command=new com.saumya.pos.returns.ReturnService.Create((String)receipt.get("id"),session,List.of(new com.saumya.pos.returns.ReturnService.Item(1,1),new com.saumya.pos.returns.ReturnService.Item(3,1)),"Returned goods","ORIGINAL_RECEIPT","Receipt checked");
  var returned=returns.create("alice","refund-report-return",command);returns.review("manager",returned.id(),approveReturn());
  returns.inspect("manager",returned.id(),new com.saumya.pos.returns.ReturnService.Inspect(List.of(new com.saumya.pos.returns.ReturnService.Inspection(1,"DAMAGED","QUARANTINE","Panel checked"),new com.saumya.pos.returns.ReturnService.Inspection(3,"DAMAGED","QUARANTINE","Cable checked"))));
  String paying=refundSession("200");update("UPDATE branches SET cash_refunds_enabled=TRUE WHERE id=1");var paid=refunds.pay("manager","refund-report-paid",refundPay(returned.id(),paying));
  update("UPDATE pos_cash_refunds SET paid_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.parse("2026-11-12T23:59:59Z")),paid.id());
  String path="/api/v1/vendor/pos/reports/cash-refunds";
  mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-13").param("tenantId","2").param("branchId","2").with(user("manager").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_REFUND_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.branchId").value(1)).andExpect(jsonPath("$.totals.refunds").value(1)).andExpect(jsonPath("$.totals.units").value(2)).andExpect(jsonPath("$.totals.cash").value(132)).andExpect(jsonPath("$.totals.beforeTax").value(120)).andExpect(jsonPath("$.totals.tax").value(12)).andExpect(jsonPath("$.days[0].totals.refunds").value(1)).andExpect(jsonPath("$.days[1].totals.refunds").value(0)).andExpect(jsonPath("$.cashiers[0].cashier").value("manager"));
  update("INSERT INTO branches(id,tenant_id,name) VALUES(3,1,'Other branch')");employee("other-report",1,3);
  for(String actor:List.of("bob","other-report"))mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-13").with(user(actor).authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_REFUND_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totals.cash").value(0)).andExpect(jsonPath("$.cashiers.length()").value(0));
  update("UPDATE pos_cash_refunds SET paid_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.parse("2026-11-13T00:00:00Z")),paid.id());
  for(String day:List.of("2026-11-12","2026-11-13"))mvc.perform(get(path).param("from",day).param("to",day).with(user("manager").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_REFUND_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totals.refunds").value(day.endsWith("13")?1:0));
  assertThat(service.reconciliation(new PosService.Scope(1,1,"manager"),paying).expectedCash()).isEqualByComparingTo("68");
 }
 @Test void refundReportRequiresAllGrantsAndValidDates() throws Exception {
  String path="/api/v1/vendor/pos/reports/cash-refunds";mvc.perform(get(path)).andExpect(status().isUnauthorized());
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
  promotions.approve(1,"alice",3,new com.saumya.pos.catalog.PromotionService.Change(new BigDecimal("75"),"Approved inverter offer"));
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
  update("UPDATE tenants SET active=FALSE WHERE id=1");mvc.perform(get(path)).andExpect(status().isNotFound());mvc.perform(get("/api/v1/marketplace/vendors/unknown/catalogue")).andExpect(status().isNotFound());
 }

 @Test void cataloguePriceSortingUsesRoundedPromotionsAndStableIdTies() throws Exception {
  searchableCatalogue();update("INSERT INTO products(id,tenant_id,sku,barcode,name,price,tax_rate,published) VALUES(7,1,'TINY',NULL,'Tiny connector',0.05,10,TRUE),(8,1,'TIE',NULL,'Tie inverter',50,0,TRUE)");
  promotions.approve(1,"alice",7,new com.saumya.pos.catalog.PromotionService.Change(new BigDecimal("10"),"Approved connector offer"));
  mvc.perform(get("/api/v1/marketplace/vendors/vendor-1/catalogue").param("q","tiny")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].price").value(0.05));
  mvc.perform(get("/api/v1/marketplace/vendors/vendor-1/catalogue").param("sort","price-low").param("size","2").param("page","1")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(3)).andExpect(jsonPath("$.items[1].id").value(8)).andExpect(jsonPath("$.totalElements").value(6));
 }

 @Test void posCatalogueUsesAssignedBranchAndExactServerSideScans() throws Exception {
  searchableCatalogue();String path="/api/v1/vendor/pos/catalogue";
  mvc.perform(get(path)).andExpect(status().isUnauthorized());mvc.perform(get(path).with(user("alice").authorities(()->"POS_SALE_CREATE"))).andExpect(status().isForbidden());
  mvc.perform(get(path).param("tenantId","2").param("branchId","3").param("inStock","true").with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
  employee("store3",1,3);
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
  configureOnline();var draft=checkout.pos("alice","recover-pos-draft",posDraft());supervisor("manager");
  String path="/api/v1/vendor/pos/drafts/"+draft.id();
  mvc.perform(get(path).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value(session)).andExpect(jsonPath("$.expired").value(false)).andExpect(jsonPath("$.total").value(110));
  for(String actor:List.of("bob","manager"))mvc.perform(get(path).with(user(actor).authorities(()->"POS_VIEW"))).andExpect(status().isNotFound());
  mvc.perform(get(path)).andExpect(status().isUnauthorized());mvc.perform(get(path).with(user("alice").authorities(()->"POS_SALE_CREATE"))).andExpect(status().isForbidden());
  update("UPDATE products SET price=999 WHERE id=1");mvc.perform(get(path).param("tenantId","2").param("cashier","bob").with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].unitPrice").value(100));
  checkout.cancelPos("alice",draft.id(),"Buyer cancelled");var online=onlineDraft("recover-online-draft");
  String customerPath="/api/v1/customer/checkout/drafts/"+online.id();mvc.perform(get(customerPath).with(user("customer").authorities(()->"CUSTOMER"))).andExpect(status().isOk()).andExpect(jsonPath("$.channel").value("ONLINE"));
  mvc.perform(get(customerPath).with(user("other-customer").authorities(()->"CUSTOMER"))).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/customer/checkout/drafts/"+draft.id()).with(user("customer").authorities(()->"CUSTOMER"))).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/vendor/pos/drafts/"+online.id()).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isNotFound());
 }
 @Test void elapsedDraftRecoveryDoesNotReleaseStockOrPayAndCanRecoverAfterClose() throws Exception {
  var draft=checkout.pos("alice","recover-expiry-draft",posDraft());update("UPDATE orders SET expires_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)),draft.id());
  String path="/api/v1/vendor/pos/drafts/"+draft.id();
  mvc.perform(get(path).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.expired").value(true));
  assertThat(stock.atBranch(1,1,1)).isZero();assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_invoices",Integer.class)).isZero();
  checkout.expire(1,draft.id());service.close(a,session,new BigDecimal("50"));mvc.perform(get(path).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EXPIRED"));assertThat(stock.atBranch(1,1,1)).isEqualTo(1);
 }
 @Test void paidDraftReferenceRecoveryNeverCreatesAnotherReceiptOrSale() throws Exception {
  var draft=checkout.pos("alice","recover-paid-draft",posDraft());checkout.finalizeCash("alice",draft.id(),new BigDecimal("120"));service.close(a,session,new BigDecimal("160"));
  for(int i=0;i<2;i++)mvc.perform(get("/api/v1/vendor/pos/drafts/"+draft.id()).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID")).andExpect(jsonPath("$.expired").value(false));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM vendor_invoices",Integer.class)).isEqualTo(1);
 }

 @Test void salesCsvExportIsScopedAuditedAndContainsNoBuyerFields() throws Exception {
  checkout.cashSale(a.cashier(),"export-sale",sale(1,"120"));String path="/api/v1/vendor/pos/reports/sales/export",body="{\"channel\":\"ALL\",\"reason\":\"Branch review\"}";
  mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW")).contentType("application/json").content(body)).andExpect(status().isForbidden());
  mvc.perform(post(path).with(user("alice").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_REPORT_EXPORT")).contentType("application/json").content(body)).andExpect(status().isForbidden());
  var response=mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_REPORT_EXPORT")).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().exists("Content-Disposition")).andReturn().getResponse();
  String csv=response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);assertThat(csv).contains("\"1\",\"1\"","\"110.00\"").doesNotContain("customer_id","cashier","password","buyer","Panel");
  var audit=db.queryForMap("SELECT * FROM report_export_audit WHERE id=?",response.getHeader("X-Export-Reference"));assertThat(((Number)audit.get("tenant_id")).longValue()).isEqualTo(1);assertThat(audit.get("actor")).isEqualTo("alice");assertThat(audit.get("content_sha256")).isEqualTo(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(response.getContentAsByteArray())));
  var foreign=mvc.perform(post(path).with(csrf()).with(user("bob").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_REPORT_EXPORT")).contentType("application/json").content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);assertThat(foreign).contains("\"2\",\"2\"").doesNotContain("110.00");
  update("INSERT INTO branches(id,tenant_id,name) VALUES(3,1,'Other export branch')");employee("export-other",1,3);
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
  String returned=inspectedRefundReturn(),paying=refundSession("200");update("UPDATE branches SET cash_refunds_enabled=TRUE WHERE id=1");refunds.pay("manager","export-refund-paid",refundPay(returned,paying));
  String csv=mvc.perform(post("/api/v1/vendor/pos/reports/cash-refunds/export").with(csrf()).with(user("manager").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_REPORT_EXPORT",()->"POS_REFUND_VIEW")).contentType("application/json").content("{\"reason\":\"Payout review\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
  assertThat(csv).contains("\"CASHIER\",\"manager\"","\"110.00\"").doesNotContain("Panel","Independent cash handover","return_id","session_id");
  assertThat(service.reconciliation(new PosService.Scope(1,1,"manager"),paying).expectedCash()).isEqualByComparingTo("90");assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_cash_refunds",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);
 }

 @Test void cashierSalesPagesRetainOriginalSellerAndSavedAmountsAcrossClosedShifts() throws Exception {
  configureOnline();update("UPDATE inventory SET quantity=10 WHERE tenant_id=1");update("INSERT INTO products(id,tenant_id,sku,barcode,name,price,tax_rate,published) VALUES(3,1,'CABLE',NULL,'Cable',20,10,TRUE)");update("INSERT INTO inventory(tenant_id,branch_id,product_id,quantity) VALUES(1,1,3,10)");
  promotions.approve(1,"alice",1,new com.saumya.pos.catalog.PromotionService.Change(BigDecimal.TEN,"Approved offer"));
  checkout.cashSale(a.cashier(),"cashier-alice-sale",new CheckoutService.CashSale(session,List.of(new CheckoutService.Item(1,1),new CheckoutService.Item(3,2)),new BigDecimal("200"),true,null,null));
  var manager=supervisor("manager");update("INSERT INTO registers VALUES(3,1,1)");String managerSession=(String)service.open(manager,new PosService.OpenSession(3,BigDecimal.ZERO)).get("id");
  checkout.cashSale(manager.cashier(),"cashier-manager-sale",new CheckoutService.CashSale(managerSession,List.of(new CheckoutService.Item(3,1)),new BigDecimal("25"),false,null,null));
  service.close(a,session,new BigDecimal("193"));onlineDraft("cashier-online-unpaid");update("UPDATE products SET price=999,tax_rate=50 WHERE tenant_id=1");
  String path="/api/v1/vendor/pos/reports/cashier-sales";
  mvc.perform(get(path).param("size","1").param("tenantId","2").param("branchId","2").with(user("manager").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_CASHIER_REPORT_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.items[0].cashier").value("alice")).andExpect(jsonPath("$.pageTotals.paidOrders").value(1)).andExpect(jsonPath("$.pageTotals.units").value(3)).andExpect(jsonPath("$.pageTotals.sales").value(143)).andExpect(jsonPath("$.pageTotals.tax").value(13)).andExpect(jsonPath("$.pageTotals.beforeTax").value(130)).andExpect(jsonPath("$.pageTotals.cashApplied").value(143)).andExpect(jsonPath("$.pageTotals.discounts").value(10));
  mvc.perform(get(path).param("size","1").param("page","1").with(user("manager").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_CASHIER_REPORT_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].cashier").value("manager")).andExpect(jsonPath("$.pageTotals.sales").value(22));
  mvc.perform(get(path).param("size","48").with(user("manager").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_CASHIER_REPORT_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.pageTotals.sales").value(165)).andExpect(jsonPath("$.pageTotals.units").value(4));
  update("INSERT INTO branches(id,tenant_id,name) VALUES(4,1,'Other report branch')");employee("other-seller-report",1,4);
  for(String actor:List.of("bob","other-seller-report"))mvc.perform(get(path).with(user(actor).authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_CASHIER_REPORT_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.items.length()").value(0));
 }
 @Test void cashierSalesReportRequiresItsOwnPermissionAndUtcBounds() throws Exception {
  String path="/api/v1/vendor/pos/reports/cashier-sales";mvc.perform(get(path)).andExpect(status().isUnauthorized());mvc.perform(get(path).with(user("alice").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW"))).andExpect(status().isForbidden());
  mvc.perform(get(path).with(user("unknown").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_CASHIER_REPORT_VIEW"))).andExpect(status().isForbidden());
  for(String[] param:List.of(new String[]{"size","49"},new String[]{"page","-1"},new String[]{"from","2020-01-01"},new String[]{"to","invalid"}))mvc.perform(get(path).param(param[0],param[1]).with(user("alice").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_CASHIER_REPORT_VIEW"))).andExpect(status().isBadRequest());
 }

 @Test void sellingCashierReportUsesUtcPaidEventsAndDoesNotSubtractOtherCashierRefunds() throws Exception {
  String returned=inspectedRefundReturn(),paying=refundSession("200");update("UPDATE branches SET cash_refunds_enabled=TRUE WHERE id=1");refunds.pay("manager","cashier-insights-refund",refundPay(returned,paying));String path="/api/v1/vendor/pos/reports/cashier-sales";
  mvc.perform(get(path).with(user("manager").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_CASHIER_REPORT_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].cashier").value("alice")).andExpect(jsonPath("$.pageTotals.sales").value(110));
  update("UPDATE orders SET status_changed_at=?",java.sql.Timestamp.from(java.time.Instant.parse("2026-11-12T23:59:59Z")));
  mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-12").with(user("manager").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_CASHIER_REPORT_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.pageTotals.paidOrders").value(1));
  update("UPDATE orders SET status_changed_at=?",java.sql.Timestamp.from(java.time.Instant.parse("2026-11-13T00:00:00Z")));update("UPDATE employees SET enabled=FALSE WHERE username='alice'");
  for(String day:List.of("2026-11-12","2026-11-13"))mvc.perform(get(path).param("from",day).param("to",day).with(user("manager").authorities(()->"POS_VIEW",()->"POS_REPORT_VIEW",()->"POS_CASHIER_REPORT_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.pageTotals.paidOrders").value(day.endsWith("13")?1:0));
 }

 @Test void refundHistoryPagesSearchesAndDatesRemainBranchScoped() throws Exception {
  enableReturns();supervisor("manager");update("UPDATE inventory SET quantity=3 WHERE tenant_id=1");var receipt=checkout.cashSale(a.cashier(),"history-original",new CheckoutService.CashSale(session,List.of(new CheckoutService.Item(1,3)),new BigDecimal("400"),false,null,null));String paying=refundSession("500");update("UPDATE branches SET cash_refunds_enabled=TRUE WHERE id=1");
  var records=new ArrayList<com.saumya.pos.returns.CashRefundService.View>();for(int i=0;i<3;i++){var r=returns.create("alice","history-return"+i,returnRequest((String)receipt.get("id"),1));returns.review("manager",r.id(),approveReturn());returns.inspect("manager",r.id(),inspectReturn("DAMAGED","QUARANTINE"));records.add(refunds.pay("manager","history-refund"+i,refundPay(r.id(),paying)));}
  update("UPDATE pos_cash_refunds SET paid_at=?",java.sql.Timestamp.from(java.time.Instant.parse("2026-11-12T23:59:59Z")));
  String path="/api/v1/vendor/pos/refunds/history";var ids=records.stream().map(com.saumya.pos.returns.CashRefundService.View::id).sorted(Comparator.reverseOrder()).toList();
  for(int page=0;page<3;page++)mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-12").param("size","1").param("page",Integer.toString(page)).param("tenantId","2").with(user("manager").authorities(()->"POS_VIEW",()->"POS_REFUND_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.items[0].id").value(ids.get(page))).andExpect(jsonPath("$.items[0].reason").doesNotExist()).andExpect(jsonPath("$.items[0].sessionId").doesNotExist());
  for(String ref:List.of(records.getFirst().id().substring(0,8),records.getFirst().returnId()))mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-12").param("reference",ref).with(user("manager").authorities(()->"POS_VIEW",()->"POS_REFUND_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
  mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-12").param("reference",receipt.get("receipt_number").toString()).with(user("manager").authorities(()->"POS_VIEW",()->"POS_REFUND_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
  mvc.perform(get(path).param("from","2026-11-13").param("to","2026-11-13").with(user("manager").authorities(()->"POS_VIEW",()->"POS_REFUND_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  update("INSERT INTO branches(id,tenant_id,name) VALUES(4,1,'Other history branch')");employee("history-other",1,4);for(String actor:List.of("bob","history-other"))mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-12").with(user(actor).authorities(()->"POS_VIEW",()->"POS_REFUND_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  assertThat(service.reconciliation(new PosService.Scope(1,1,"manager"),paying).expectedCash()).isEqualByComparingTo("170");assertThat(stock.atBranch(1,1,1)).isZero();
 }
 @Test void refundHistoryRejectsInvalidFiltersAndMissingPermissions() throws Exception {
  String path="/api/v1/vendor/pos/refunds/history";mvc.perform(get(path)).andExpect(status().isUnauthorized());mvc.perform(get(path).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isForbidden());
  for(String[] bad:List.of(new String[]{"reference","%"},new String[]{"size","49"},new String[]{"page","-1"},new String[]{"from","2020-01-01"}))mvc.perform(get(path).param(bad[0],bad[1]).with(user("alice").authorities(()->"POS_VIEW",()->"POS_REFUND_VIEW"))).andExpect(status().isBadRequest());
 }

 @Test void returnAuditTimelineIncludesStockAndFinancialEventsWithoutPrivateNotes() throws Exception {
  String returned=inspectedRefundReturn(),paying=refundSession("200");update("UPDATE branches SET cash_refunds_enabled=TRUE WHERE id=1");var paid=refunds.pay("manager","audit-refund-paid",refundPay(returned,paying));refunds.copy("manager",paid.id());
  int sequence=0;for(String action:List.of("REQUESTED","APPROVED","INSPECTED","CASH_REFUND","REFUND_COPY"))update("UPDATE pos_return_events SET created_at=? WHERE return_id=? AND action=?",java.sql.Timestamp.from(java.time.Instant.parse("2026-11-13T12:00:00Z").plusSeconds(sequence++)),returned,action);
  service.close(new PosService.Scope(1,1,"manager"),paying,new BigDecimal("90"));service.close(a,session,new BigDecimal("160"));
  String path="/api/v1/vendor/pos/returns/"+returned+"/events";
  mvc.perform(get(path)).andExpect(status().isUnauthorized());mvc.perform(get(path).with(user("manager").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW"))).andExpect(status().isForbidden());
  mvc.perform(get(path).param("size","2").with(user("manager").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW",()->"POS_RETURN_AUDIT_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("INSPECTED")).andExpect(jsonPath("$.totalElements").value(5)).andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.items[0].action").value("REFUND_COPY")).andExpect(jsonPath("$.items[0].note").doesNotExist()).andExpect(jsonPath("$.items[0].sessionId").doesNotExist());
  mvc.perform(get(path).param("page","2").param("size","2").with(user("manager").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW",()->"POS_RETURN_AUDIT_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].action").value("REQUESTED"));
  update("UPDATE pos_return_events SET created_at=? WHERE return_id=?",java.sql.Timestamp.from(java.time.Instant.parse("2026-11-13T12:00:00Z")),returned);
  var eventIds=db.queryForList("SELECT id FROM pos_return_events WHERE return_id=?",String.class,returned).stream().sorted(Comparator.reverseOrder()).toList();
  for(int page=0;page<3;page++)mvc.perform(get(path).param("page",Integer.toString(page)).param("size","2").with(user("manager").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW",()->"POS_RETURN_AUDIT_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(eventIds.get(page*2)));
  update("INSERT INTO branches(id,tenant_id,name) VALUES(4,1,'Other audit branch')");employee("audit-other",1,4);
  for(String actor:List.of("bob","audit-other"))mvc.perform(get(path).param("tenantId","1").param("branchId","1").with(user(actor).authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW",()->"POS_RETURN_AUDIT_VIEW"))).andExpect(status().isNotFound());
  mvc.perform(get(path).param("size","49").with(user("manager").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW",()->"POS_RETURN_AUDIT_VIEW"))).andExpect(status().isBadRequest());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_return_events",Integer.class)).isEqualTo(5);assertThat(stock.atBranch(1,1,1)).isZero();assertThat(service.reconciliation(new PosService.Scope(1,1,"manager"),paying).expectedCash()).isEqualByComparingTo("90");
 }
 @Test void pagedReturnQueueFindsCasesBeyondTwentyAndEnforcesReadOnlyBranchScope() throws Exception {
  update("UPDATE branches SET pos_return_window_days=30 WHERE id=1");update("UPDATE inventory SET quantity=25 WHERE tenant_id=1");
  String order=(String)checkout.cashSale(a.cashier(),"queue-sale",new CheckoutService.CashSale(session,List.of(new CheckoutService.Item(1,25)),new BigDecimal("2750"),false,null,null)).get("id");
  var ids=new ArrayList<String>();for(int i=0;i<25;i++)ids.add(returns.create("alice","queue-case-"+i,returnRequest(order,1)).id());
  update("UPDATE pos_returns SET created_at=?",java.sql.Timestamp.from(java.time.Instant.parse("2026-11-12T00:00:00Z")));ids.sort(Comparator.naturalOrder());
  service.close(a,session,new BigDecimal("2800"));String path="/api/v1/vendor/pos/returns/queue";
  for(int page=0;page<3;page++)mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-12").param("page",Integer.toString(page)).with(user("alice").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(25)).andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.items[0].id").value(ids.get(page*12))).andExpect(jsonPath("$.items[0].reason").doesNotExist()).andExpect(jsonPath("$.items[0].requester").doesNotExist()).andExpect(jsonPath("$.items[0].items").doesNotExist());
  mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-12").param("reference",ids.getLast()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
  mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-12").param("reference",order.toUpperCase(Locale.ROOT)).with(user("alice").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(25));
  mvc.perform(get(path).param("from","2026-11-13").param("to","2026-11-13").with(user("alice").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-12").param("state","APPROVED").with(user("alice").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  employee("queue-manager",1,1);returns.review("queue-manager",ids.getFirst(),approveReturn());
  for(String state:List.of("ALL","REQUESTED","APPROVED"))mvc.perform(get(path).param("state",state).param("from","2026-11-12").param("to","2026-11-12").with(user("alice").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(state.equals("ALL")?25:state.equals("REQUESTED")?24:1));
  update("INSERT INTO branches(id,tenant_id,name) VALUES(4,1,'Other queue branch')");employee("queue-other",1,4);
  for(String actor:List.of("bob","queue-other"))mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-12").param("tenantId","1").param("branchId","1").with(user(actor).authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_return_events",Integer.class)).isEqualTo(26);assertThat(stock.atBranch(1,1,1)).isZero();assertThat(service.reconciliation(a,session).expectedCash()).isEqualByComparingTo("2800");
 }
 @Test void returnQueueRejectsMissingPermissionsAndInvalidFilters() throws Exception {
  String path="/api/v1/vendor/pos/returns/queue";mvc.perform(get(path)).andExpect(status().isUnauthorized());mvc.perform(get(path).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isForbidden());
  for(String[] bad:List.of(new String[]{"state","PAID"},new String[]{"reference","%"},new String[]{"size","49"},new String[]{"page","-1"},new String[]{"from","2020-01-01"},new String[]{"to","bad-date"}))mvc.perform(get(path).param(bad[0],bad[1]).with(user("alice").authorities(()->"POS_VIEW",()->"POS_RETURN_VIEW"))).andExpect(status().isBadRequest());
 }

 @Test void ownClosedShiftHistoryIsPagedAndDoesNotExposeOtherCashiersOrChangeDrawers() throws Exception {
  var ids=new ArrayList<String>();ids.add(session);service.close(a,session,new BigDecimal("50"));
  for(int i=0;i<24;i++){String id=(String)service.open(a,new PosService.OpenSession(1,BigDecimal.ZERO)).get("id");service.close(a,id,BigDecimal.ZERO);ids.add(id);}
  update("UPDATE pos_sessions SET closed_at=? WHERE cashier='alice'",java.sql.Timestamp.from(java.time.Instant.parse("2026-11-12T00:00:00Z")));ids.sort(Comparator.reverseOrder());
  String open=(String)service.open(a,new PosService.OpenSession(1,BigDecimal.TEN)).get("id");
  employee("shift-colleague",1,1);update("INSERT INTO registers VALUES(3,1,1)");var colleague=new PosService.Scope(1,1,"shift-colleague");String other=(String)service.open(colleague,new PosService.OpenSession(3,new BigDecimal("99"))).get("id");service.close(colleague,other,new BigDecimal("99"));update("UPDATE pos_sessions SET closed_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.parse("2026-11-12T00:00:00Z")),other);
  String path="/api/v1/vendor/pos/sessions/history";
  for(int page=0;page<3;page++)mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-12").param("page",Integer.toString(page)).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(25)).andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.items[0].id").value(ids.get(page*12))).andExpect(jsonPath("$.items[0].cashier").doesNotExist()).andExpect(jsonPath("$.items[0].varianceReason").doesNotExist()).andExpect(jsonPath("$.items[0].closeRequestId").doesNotExist());
  mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-12").param("reference",session.toUpperCase(Locale.ROOT)).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].openingFloat").value(50)).andExpect(jsonPath("$.items[0].expectedCash").value(50)).andExpect(jsonPath("$.items[0].countedCash").value(50)).andExpect(jsonPath("$.items[0].variance").value(0));
  for(String reference:List.of(open,other))mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-12").param("reference",reference).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  mvc.perform(get(path).param("from","2026-11-13").param("to","2026-11-13").with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  update("INSERT INTO branches(id,tenant_id,name) VALUES(4,1,'Other shift branch')");employee("shift-other",1,4);
  for(String actor:List.of("bob","shift-other"))mvc.perform(get(path).param("from","2026-11-12").param("to","2026-11-12").param("cashier","alice").param("tenantId","1").param("branchId","1").with(user(actor).authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  int audit=db.queryForObject("SELECT COUNT(*) FROM pos_audit",Integer.class);mvc.perform(get(path).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk());assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit",Integer.class)).isEqualTo(audit);assertThat(service.reconciliation(a,open).expectedCash()).isEqualByComparingTo("10");assertThat(stock.atBranch(1,1,1)).isEqualTo(1);
 }
 @Test void ownShiftHistoryRejectsMissingPermissionsAndInvalidBounds() throws Exception {
  String path="/api/v1/vendor/pos/sessions/history";mvc.perform(get(path)).andExpect(status().isUnauthorized());mvc.perform(get(path).with(user("alice"))).andExpect(status().isForbidden());
  for(String[] bad:List.of(new String[]{"reference","%"},new String[]{"size","49"},new String[]{"page","-1"},new String[]{"from","2020-01-01"},new String[]{"to","bad-date"}))mvc.perform(get(path).param(bad[0],bad[1]).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isBadRequest());
  update("UPDATE employees SET enabled=FALSE WHERE username='alice'");mvc.perform(get(path).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isForbidden());
 }

 @Test void shiftHistoryExcludesPendingReviewAndPreservesApprovedVarianceSnapshot() throws Exception {
  String request=UUID.randomUUID().toString();service.close(a,session,new BigDecimal("49"),"Count difference",request);
  String path="/api/v1/vendor/pos/sessions/history";
  mvc.perform(get(path).param("reference",session).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  employee("history-supervisor",1,1);service.reviewVariance(new PosService.Scope(1,1,"history-supervisor"),session,request,"Count independently checked",PosService.ReviewAction.APPROVE);
  mvc.perform(get(path).param("reference",session).with(user("alice").authorities(()->"POS_VIEW"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].expectedCash").value(50)).andExpect(jsonPath("$.items[0].countedCash").value(49)).andExpect(jsonPath("$.items[0].variance").value(-1)).andExpect(jsonPath("$.items[0].reviewedBy").doesNotExist()).andExpect(jsonPath("$.items[0].reviewReason").doesNotExist());
  assertThat(db.queryForObject("SELECT state FROM pos_sessions WHERE id=?",String.class,session)).isEqualTo("CLOSED");assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_shift_reviews",Integer.class)).isEqualTo(1);
 }

 @Test void publicVendorDirectoryIsPagedSearchableAndContainsOnlyActivePublicCards() throws Exception {
  update("UPDATE tenants SET slug=CONCAT('vendor-',id),online_branch_id=id");
  for(int i=3;i<=27;i++)update("INSERT INTO tenants(id,name,active,slug) VALUES(?,? ,TRUE,?)",i,"Solar Partner", "solar-"+i);
  update("INSERT INTO tenants(id,name,active,slug) VALUES(28,'Hidden inactive',FALSE,'hidden-inactive'),(29,'Hidden null',TRUE,NULL),(30,'Hidden blank',TRUE,'')");
  String path="/api/v1/marketplace/directory";
  for(int page=0;page<3;page++)mvc.perform(get(path).param("q","sOlAr").param("size","12").param("page",Integer.toString(page))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(25)).andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.items[0].id").value(3+page*12)).andExpect(jsonPath("$.items[0].active").doesNotExist()).andExpect(jsonPath("$.items[0].onlineBranchId").doesNotExist());
  mvc.perform(get(path).param("q","solar-27")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].slug").value("solar-27"));
  mvc.perform(get(path).param("q","hidden")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  update("UPDATE tenants SET name='100%_! Solar' WHERE id=27");mvc.perform(get(path).param("q","%_!")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(27));
  mvc.perform(get(path).param("page","9999")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
  update("UPDATE tenants SET active=FALSE WHERE id=27");mvc.perform(get(path).param("q","solar-27")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  assertThat(stock.atBranch(1,1,1)).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isZero();assertThat(db.queryForObject("SELECT COUNT(*) FROM carts",Integer.class)).isZero();
 }
 @Test void publicVendorDirectoryRejectsUnboundedInput() throws Exception {
  for(String[] bad:List.of(new String[]{"q","x".repeat(121)},new String[]{"q","bad\nquery"},new String[]{"size","49"},new String[]{"page","-1"}))mvc.perform(get("/api/v1/marketplace/directory").param(bad[0],bad[1])).andExpect(status().isBadRequest());
 }

 @Test void browsingAnotherVendorDirectoryPagePreservesExistingCustomerCart() throws Exception {
  customers();var cart=marketplace.put(100,new CartService.Item("vendor-1",1,1));
  mvc.perform(get("/api/v1/marketplace/directory").param("q","vendor-2")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
  assertThat(marketplace.cart(100)).isEqualTo(cart);assertThat(db.queryForObject("SELECT COUNT(*) FROM carts",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM stock_reservations",Integer.class)).isZero();assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isZero();
 }

 @Test void promotionAuditHistoryIsTenantScopedPagedAndReadOnlyForWithdrawnProducts() throws Exception {
  for(int i=1;i<=25;i++)promotions.approve(1,"alice",1,new com.saumya.pos.catalog.PromotionService.Change(new BigDecimal(i),"Approved offer "+i));promotions.approve(1,"alice",1,new com.saumya.pos.catalog.PromotionService.Change(new BigDecimal("25"),"No change"));promotions.approve(2,"bob",2,new com.saumya.pos.catalog.PromotionService.Change(BigDecimal.TEN,"Foreign tenant offer"));
  update("UPDATE catalog_promotion_audit SET created_at=?",java.sql.Timestamp.from(java.time.Instant.parse("2026-11-13T12:00:00Z")));var ids=db.queryForList("SELECT id FROM catalog_promotion_audit WHERE tenant_id=1",String.class).stream().sorted(Comparator.reverseOrder()).toList();
  update("UPDATE products SET name='Renamed panel',published=FALSE WHERE id=1");String path="/api/v1/vendor/pos/promotions/1/history";
  for(int page=0;page<3;page++)mvc.perform(get(path).param("page",Integer.toString(page)).with(user("alice").authorities(()->"POS_VIEW",()->"CATALOG_PROMOTION_MANAGE",()->"POS_DISCOUNT_APPROVE"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(25)).andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.currentName").value("Renamed panel")).andExpect(jsonPath("$.published").value(false)).andExpect(jsonPath("$.items[0].id").value(ids.get(page*12))).andExpect(jsonPath("$.items[0].actor").value("alice")).andExpect(jsonPath("$.items[0].tenantId").doesNotExist());
  update("INSERT INTO branches(id,tenant_id,name) VALUES(4,1,'Other catalogue branch')");employee("promotion-viewer",1,4);mvc.perform(get(path).with(user("promotion-viewer").authorities(()->"POS_VIEW",()->"CATALOG_PROMOTION_MANAGE",()->"POS_DISCOUNT_APPROVE"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(25));
  mvc.perform(get(path).param("tenantId","1").with(user("bob").authorities(()->"POS_VIEW",()->"CATALOG_PROMOTION_MANAGE",()->"POS_DISCOUNT_APPROVE"))).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/vendor/pos/promotions/2/history").with(user("alice").authorities(()->"POS_VIEW",()->"CATALOG_PROMOTION_MANAGE",()->"POS_DISCOUNT_APPROVE"))).andExpect(status().isNotFound());
  mvc.perform(get(path).param("page","999").with(user("alice").authorities(()->"POS_VIEW",()->"CATALOG_PROMOTION_MANAGE",()->"POS_DISCOUNT_APPROVE"))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM catalog_promotion_audit",Integer.class)).isEqualTo(26);assertThat(promotions.price(1,1,new BigDecimal("100"),false).price()).isEqualByComparingTo("75");assertThat(stock.atBranch(1,1,1)).isEqualTo(1);assertThat(service.reconciliation(a,session).expectedCash()).isEqualByComparingTo("50");
 }
 @Test void promotionAuditRejectsMissingRightsInactiveMembershipAndBadBounds() throws Exception {
  String path="/api/v1/vendor/pos/promotions/1/history";mvc.perform(get(path)).andExpect(status().isUnauthorized());
  for(String missing:List.of("POS_VIEW","CATALOG_PROMOTION_MANAGE","POS_DISCOUNT_APPROVE")){var rights=List.of("POS_VIEW","CATALOG_PROMOTION_MANAGE","POS_DISCOUNT_APPROVE").stream().filter(p->!p.equals(missing)).map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).toList();mvc.perform(get(path).with(user("alice").authorities(rights))).andExpect(status().isForbidden());}
  for(String[] bad:List.of(new String[]{"page","-1"},new String[]{"size","49"}))mvc.perform(get(path).param(bad[0],bad[1]).with(user("alice").authorities(()->"POS_VIEW",()->"CATALOG_PROMOTION_MANAGE",()->"POS_DISCOUNT_APPROVE"))).andExpect(status().isBadRequest());
  update("UPDATE tenants SET active=FALSE WHERE id=1");mvc.perform(get(path).with(user("alice").authorities(()->"POS_VIEW",()->"CATALOG_PROMOTION_MANAGE",()->"POS_DISCOUNT_APPROVE"))).andExpect(status().isForbidden());
 }

 @Test void registerDirectoryIsPagedScopedAndHidesDrawerDetails() throws Exception {
  for(int id=3;id<=26;id++)update("INSERT INTO registers VALUES(?,1,1)",id);
  employee("register-colleague",1,1);var colleague=new PosService.Scope(1,1,"register-colleague");String closing=(String)service.open(colleague,new PosService.OpenSession(3,new BigDecimal("50"))).get("id");service.close(colleague,closing,new BigDecimal("49"),"Count discrepancy",UUID.randomUUID().toString());String closed=(String)service.open(a,new PosService.OpenSession(4,BigDecimal.ZERO)).get("id");service.close(a,closed,BigDecimal.ZERO);
  int audit=db.queryForObject("SELECT COUNT(*) FROM pos_audit",Integer.class);var ids=db.queryForList("SELECT id FROM registers WHERE tenant_id=1 ORDER BY id",Long.class);String path="/api/v1/vendor/pos/registers";
  for(int page=0;page<3;page++)mvc.perform(get(path).param("page",Integer.toString(page)).with(user("alice").authorities(()->"POS_VIEW",()->"POS_SESSION_OPEN"))).andExpect(status().isOk()).andExpect(jsonPath("$.branchId").value(1)).andExpect(jsonPath("$.totalElements").value(25)).andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.items[0].id").value(ids.get(page*12))).andExpect(jsonPath("$.items[0].cashier").doesNotExist()).andExpect(jsonPath("$.items[0].openingFloat").doesNotExist()).andExpect(jsonPath("$.items[0].sessionId").doesNotExist()).andExpect(jsonPath("$.items[0].state").doesNotExist());
  mvc.perform(get(path).param("size","48").with(user("alice").authorities(()->"POS_VIEW",()->"POS_SESSION_OPEN"))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].available").value(false)).andExpect(jsonPath("$.items[1].available").value(false)).andExpect(jsonPath("$.items[2].available").value(true));
  mvc.perform(get(path).param("tenantId","1").param("branchId","1").with(user("bob").authorities(()->"POS_VIEW",()->"POS_SESSION_OPEN"))).andExpect(status().isOk()).andExpect(jsonPath("$.branchId").value(2)).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(2));
  update("INSERT INTO branches(id,tenant_id,name) VALUES(4,1,'Other register branch')");employee("register-other",1,4);update("INSERT INTO registers VALUES(99,1,4)");mvc.perform(get(path).param("branchId","1").with(user("register-other").authorities(()->"POS_VIEW",()->"POS_SESSION_OPEN"))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(99));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit",Integer.class)).isEqualTo(audit);assertThat(service.reconciliation(a,session).expectedCash()).isEqualByComparingTo("50");assertThat(stock.atBranch(1,1,1)).isEqualTo(1);
  service.close(a,session,new BigDecimal("50"));mvc.perform(get(path).with(user("alice").authorities(()->"POS_VIEW",()->"POS_SESSION_OPEN"))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].available").value(true));
  mvc.perform(get(path).param("page","999").with(user("alice").authorities(()->"POS_VIEW",()->"POS_SESSION_OPEN"))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
 }
 @Test void registerDirectoryRejectsMissingRightsBadBoundsAndInactiveMembership() throws Exception {
  String path="/api/v1/vendor/pos/registers";mvc.perform(get(path)).andExpect(status().isUnauthorized());for(String right:List.of("POS_VIEW","POS_SESSION_OPEN"))mvc.perform(get(path).with(user("alice").authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(right)))).andExpect(status().isForbidden());
  for(String[] bad:List.of(new String[]{"page","-1"},new String[]{"size","49"}))mvc.perform(get(path).param(bad[0],bad[1]).with(user("alice").authorities(()->"POS_VIEW",()->"POS_SESSION_OPEN"))).andExpect(status().isBadRequest());update("UPDATE employees SET enabled=FALSE WHERE username='alice'");mvc.perform(get(path).with(user("alice").authorities(()->"POS_VIEW",()->"POS_SESSION_OPEN"))).andExpect(status().isForbidden());
 }

 @Test void directReceiptIdentitySurvivesRenamesRetriesAndAuditedCopies() throws Exception {
  var receipt=checkout.cashSale(a.cashier(),"identity-cash",sale(1,"120"));String id=(String)receipt.get("id");assertThat(receipt.get("receipt_seller_name")).isEqualTo("Vendor A");assertThat(receipt.get("receipt_outlet_name")).isEqualTo("A");assertThat(receipt.get("issued_at")).isNotNull();
  update("UPDATE tenants SET name='New seller' WHERE id=1");update("UPDATE branches SET name='New outlet' WHERE id=1");
  for(var copy:List.of(checkout.cashSale(a.cashier(),"identity-cash",sale(1,"120")),receiptReader.reprint(a,id))){assertThat(copy.get("receipt_seller_name")).isEqualTo("Vendor A");assertThat(copy.get("receipt_outlet_name")).isEqualTo("A");assertThat(copy.get("issued_at")).isEqualTo(receipt.get("issued_at"));assertThat(copy.get("receipt_number")).isEqualTo(receipt.get("receipt_number"));}
  mvc.perform(get("/api/v1/vendor/pos/receipts/"+id).with(user("bob").authorities(()->"POS_RECEIPT_VIEW"))).andExpect(status().isNotFound());assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);assertThat(stock.atBranch(1,1,1)).isZero();
 }
 @Test void reservedReceiptIdentityIsCapturedAtPaymentAndInvoiceUsesTheSameNames(){
  var draft=checkout.pos("alice","identity-draft",new CheckoutService.PosDraft(session,List.of(new CheckoutService.Item(1,1)),true,null,null));assertThat(db.queryForObject("SELECT receipt_seller_name FROM orders WHERE id=?",String.class,draft.id())).isNull();
  update("UPDATE tenants SET name='Seller at payment' WHERE id=1");update("UPDATE branches SET name='Outlet at payment' WHERE id=1");update("UPDATE orders SET created_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.parse("2026-01-01T00:00:00Z")),draft.id());
  var receipt=checkout.finalizeCash("alice",draft.id(),new BigDecimal("120"));assertThat(receipt.get("receipt_seller_name")).isEqualTo("Seller at payment");assertThat(receipt.get("receipt_outlet_name")).isEqualTo("Outlet at payment");assertThat(receipt.get("issued_at")).isEqualTo(db.queryForObject("SELECT status_changed_at FROM orders WHERE id=?",java.sql.Timestamp.class,draft.id()));
  assertThat(db.queryForObject("SELECT seller_name FROM vendor_invoices WHERE order_id=?",String.class,draft.id())).isEqualTo("Seller at payment");
  update("UPDATE tenants SET name='Later seller' WHERE id=1");var retry=checkout.finalizeCash("alice",draft.id(),new BigDecimal("120"));assertThat(retry.get("receipt_seller_name")).isEqualTo("Seller at payment");assertThat(retry.get("issued_at")).isEqualTo(receipt.get("issued_at"));
 }
 @Test void receiptIdentityRollsBackWithSaleAndLegacyReadNeverInventsNames(){
  assertThatThrownBy(()->new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->{checkout.cashSale(a.cashier(),"identity-rollback",sale(1,"120"));throw new IllegalStateException("Rollback sale");})).isInstanceOf(IllegalStateException.class);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_drawer_tenders",Integer.class)).isZero();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isZero();assertThat(stock.atBranch(1,1,1)).isEqualTo(1);assertThat(db.queryForObject("SELECT receipt_sequence FROM branches WHERE id=1",Long.class)).isZero();
  String id=(String)checkout.cashSale(a.cashier(),"identity-legacy",sale(1,"120")).get("id");update("UPDATE orders SET receipt_seller_name=NULL,receipt_outlet_name=NULL WHERE id=?",id);update("UPDATE tenants SET name='Mutable current seller' WHERE id=1");assertThat(receiptReader.receipt(a,id).get("receipt_seller_name")).isNull();assertThat(receiptReader.reprint(a,id).get("receipt_outlet_name")).isNull();assertThat(db.queryForObject("SELECT receipt_seller_name FROM orders WHERE id=?",String.class,id)).isNull();
 }

 @Test void fastCashUsesCanonicalReservationConsumptionAndRejectsChangedCashRetries(){
  var paid=checkout.cashSale("alice","unified-cash",sale(1,"120"));String id=(String)paid.get("id");
  assertThat(db.queryForObject("SELECT state FROM stock_reservations WHERE order_id=?",String.class,id)).isEqualTo("CONSUMED");assertThat(db.queryForObject("SELECT reserved FROM inventory WHERE tenant_id=1 AND product_id=1",Integer.class)).isZero();assertThat(db.queryForObject("SELECT status FROM orders WHERE id=?",String.class,id)).isEqualTo("PAID");assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit WHERE action='DRAFT_RESERVE' AND resource_id=?",Integer.class,id)).isEqualTo(1);
  assertThat(checkout.cashSale("alice","unified-cash",sale(1,"120")).get("id")).isEqualTo(id);assertThatThrownBy(()->checkout.cashSale("alice","unified-cash",sale(1,"130"))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);
 }
 @Test void failedFastCashLeavesNoDraftOrHoldAndDoesNotAliasExplicitDraftKeys(){
  assertThatThrownBy(()->checkout.cashSale("alice","unified-underpaid",sale(1,"1"))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isZero();assertThat(db.queryForObject("SELECT COUNT(*) FROM stock_reservations",Integer.class)).isZero();assertThat(db.queryForObject("SELECT receipt_sequence FROM branches WHERE id=1",Long.class)).isZero();
  var draft=checkout.pos("alice","same-operation-key",posDraft());assertThatThrownBy(()->checkout.cashSale("alice","same-operation-key",sale(1,"120"))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);assertThat(db.queryForObject("SELECT status FROM orders WHERE id=?",String.class,draft.id())).isEqualTo("DRAFT");assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);
 }

 @Test void paidStatusTimestampRetainsMicrosecondsInsteadOfRoundingIntoTheFuture(){
  var receipt=checkout.cashSale("alice","paid-precision",sale(1,"120"));String id=(String)receipt.get("id");var paid=java.time.Instant.parse("2026-11-13T12:00:00.654321Z");update("UPDATE orders SET status_changed_at=? WHERE id=?",java.sql.Timestamp.from(paid),id);
  assertThat(db.queryForObject("SELECT status_changed_at FROM orders WHERE id=?",java.sql.Timestamp.class,id).toInstant()).isEqualTo(paid);assertThat(((java.sql.Timestamp)receiptReader.receipt(a,id).get("issued_at")).toInstant()).isEqualTo(paid);
 }

 @Test void receiptPdfCopiesAreScopedAuditedAndMatchExactReturnedBytes() throws Exception {
  var sale=checkout.cashSale("alice","pdf-original",sale(1,"120"));String id=(String)sale.get("id");update("UPDATE tenants SET name='Renamed seller' WHERE id=1");service.close(a,session,new BigDecimal("160"));int audit=db.queryForObject("SELECT COUNT(*) FROM pos_audit",Integer.class);
  var response=mvc.perform(post("/api/v1/vendor/pos/receipts/"+id+"/document").with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_RECEIPT_VIEW",()->"POS_RECEIPT_REPRINT")).contentType("application/json").content("{\"reason\":\" Customer requested copy \"}")).andExpect(status().isOk()).andExpect(content().contentType("application/pdf")).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse();
  byte[] bytes=response.getContentAsByteArray();String reference=response.getHeader("X-Document-Reference");assertThat(reference).matches("[0-9a-f-]{36}");assertThat(response.getHeader("Content-Disposition")).contains(".pdf");String text;try(var reader=new org.openpdf.text.pdf.PdfReader(bytes)){text=new org.openpdf.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(1);}assertThat(text).contains("COPY - Receipt 1/1","Vendor A","110.00",reference).doesNotContain("Renamed seller","Customer requested copy");
  assertThat(db.queryForObject("SELECT content_sha256 FROM receipt_document_audit WHERE id=?",String.class,reference)).isEqualTo(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));assertThat(db.queryForObject("SELECT content_length FROM receipt_document_audit WHERE id=?",Integer.class,reference)).isEqualTo(bytes.length);assertThat(db.queryForObject("SELECT reason FROM receipt_document_audit WHERE id=?",String.class,reference)).isEqualTo("Customer requested copy");assertThat(db.queryForObject("SELECT COUNT(*) FROM pos_audit",Integer.class)).isEqualTo(audit);assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);assertThat(db.queryForObject("SELECT receipt_sequence FROM branches WHERE id=1",Long.class)).isEqualTo(1);assertThat(stock.atBranch(1,1,1)).isZero();
 }
 @Test void receiptPdfRejectsMissingRightsCsrfBadReasonForeignScopeAndUnsupportedGlyphs() throws Exception {
  update("UPDATE products SET name='Panel 😀' WHERE id=1");String id=(String)checkout.cashSale("alice","pdf-unsupported",sale(1,"120")).get("id"),path="/api/v1/vendor/pos/receipts/"+id+"/document";
  mvc.perform(post(path).contentType("application/json").content("{\"reason\":\"Copy request\"}")).andExpect(status().isForbidden());
  for(String missing:List.of("POS_VIEW","POS_RECEIPT_VIEW","POS_RECEIPT_REPRINT")){var rights=List.of("POS_VIEW","POS_RECEIPT_VIEW","POS_RECEIPT_REPRINT").stream().filter(p->!p.equals(missing)).map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).toList();mvc.perform(post(path).with(csrf()).with(user("alice").authorities(rights)).contentType("application/json").content("{\"reason\":\"Copy request\"}")).andExpect(status().isForbidden());}
  mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_RECEIPT_VIEW",()->"POS_RECEIPT_REPRINT")).contentType("application/json").content("{\"reason\":\" \"}")).andExpect(status().isBadRequest());
  mvc.perform(post(path).with(csrf()).with(user("bob").authorities(()->"POS_VIEW",()->"POS_RECEIPT_VIEW",()->"POS_RECEIPT_REPRINT")).contentType("application/json").content("{\"reason\":\"Copy request\"}")).andExpect(status().isNotFound());
  mvc.perform(post(path).with(csrf()).with(user("alice").authorities(()->"POS_VIEW",()->"POS_RECEIPT_VIEW",()->"POS_RECEIPT_REPRINT")).contentType("application/json").content("{\"reason\":\"Copy request\"}")).andExpect(status().isConflict());assertThat(db.queryForObject("SELECT COUNT(*) FROM receipt_document_audit",Integer.class)).isZero();
 }

}
