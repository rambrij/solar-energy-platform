package com.saumya.pos;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;
class DraftSessionMigrationTest {
 @Test void existingUnpaidOrdersPopulateTheRetailGuardWithoutRewritingTheOrder(){
  String url="jdbc:h2:mem:draft-projection-"+java.util.UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
  Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").target("35").load().migrate();var db=new JdbcTemplate(new DriverManagerDataSource(url,"sa",""));
  db.update("INSERT INTO tenants(id,name,active) VALUES(1,'Vendor',TRUE)");db.update("INSERT INTO branches(id,tenant_id,name) VALUES(1,1,'Outlet')");db.update("INSERT INTO users VALUES(1,'cashier','unused',TRUE)");db.update("INSERT INTO employees VALUES('cashier',1,1,TRUE)");db.update("INSERT INTO registers VALUES(1,1,1)");db.update("INSERT INTO pos_sessions(id,tenant_id,branch_id,cashier,register_id,state,opening_float) VALUES('shift',1,1,'cashier',1,'OPEN',0)");
  db.update("INSERT INTO orders(id,tenant_id,branch_id,cashier,session_id,sales_channel,idempotency_key,request_hash,total,tax_invoice,status,stock_branch_id) VALUES('unpaid',1,1,'cashier','shift','IN_STORE','draft-key','hash',0,FALSE,'DRAFT',1)");
  Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").load().migrate();
  assertThat(db.queryForObject("SELECT order_reference FROM pos_active_drafts WHERE tenant_id=1 AND branch_id=1 AND cashier='cashier' AND session_id='shift'",String.class)).isEqualTo("unpaid");assertThat(db.queryForObject("SELECT status FROM orders WHERE id='unpaid'",String.class)).isEqualTo("DRAFT");assertThat(db.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);
 }
}
