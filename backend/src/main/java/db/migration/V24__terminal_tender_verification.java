package db.migration;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.api.migration.*;
public class V24__terminal_tender_verification extends BaseJavaMigration {
 public void migrate(Context context)throws Exception{
  var c=context.getConnection();boolean mysql=c.getMetaData().getDatabaseProductName().equals("MySQL");
  dropChecks(c,mysql,"vendor_payment_attempts","state");dropChecks(c,mysql,"orders","payment_method");
  run(c,"ALTER TABLE vendor_payment_attempts "+(mysql?"MODIFY COLUMN state VARCHAR(48) NOT NULL":"ALTER COLUMN state VARCHAR(48)"));
  run(c,"ALTER TABLE vendor_payment_attempts ADD origin VARCHAR(24) NOT NULL DEFAULT 'ONLINE_MOCK'");
  run(c,"UPDATE vendor_payment_attempts SET origin='RETIRED_POS_SIMULATION' WHERE order_id IN (SELECT id FROM orders WHERE sales_channel='IN_STORE')");
  run(c,"ALTER TABLE vendor_payment_attempts ADD evidence_reference VARCHAR(100) NULL");
  run(c,"ALTER TABLE vendor_payment_attempts ADD reviewed_by VARCHAR(100) NULL");
  run(c,"ALTER TABLE vendor_payment_attempts ADD review_reason VARCHAR(250) NULL");
  run(c,"ALTER TABLE vendor_payment_attempts ADD reviewed_at TIMESTAMP NULL");
  run(c,"ALTER TABLE vendor_payment_attempts ADD CONSTRAINT chk_tender_state CHECK(state IN ('PENDING','PENDING_VERIFICATION','CONFIRMED','FAILED','UNKNOWN_RECONCILIATION_REQUIRED'))");
  run(c,"ALTER TABLE vendor_payment_attempts ADD CONSTRAINT chk_tender_origin CHECK(origin IN ('ONLINE_MOCK','POS_TERMINAL','RETIRED_POS_SIMULATION'))");
  run(c,"ALTER TABLE vendor_payment_attempts ADD CONSTRAINT chk_terminal_evidence CHECK(origin<>'POS_TERMINAL' OR state NOT IN ('CONFIRMED','FAILED') OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND provider_reference IS NOT NULL AND evidence_reference IS NOT NULL AND review_reason IS NOT NULL))");
  run(c,"ALTER TABLE branches ADD manual_tender_enabled BOOLEAN NOT NULL DEFAULT FALSE");
  run(c,"ALTER TABLE branches ADD upi_merchant_reference VARCHAR(80) NULL");
  run(c,"ALTER TABLE branches ADD card_merchant_reference VARCHAR(80) NULL");
  run(c,"ALTER TABLE orders ADD payment_reference VARCHAR(80) NULL");
  run(c,"ALTER TABLE orders ADD CONSTRAINT chk_sale_payment_method CHECK(payment_method IN ('CASH','MOCK_UPI','MOCK_CARD','UPI','CARD'))");
  run(c,"ALTER TABLE orders ADD CONSTRAINT chk_sale_cash_amount CHECK(status<>'PAID' OR sales_channel='ONLINE' OR (payment_method='CASH' AND cash_received-change_given=total) OR (payment_method IN ('MOCK_UPI','MOCK_CARD','UPI','CARD') AND cash_received=0 AND change_given=0))");
  run(c,"ALTER TABLE orders ADD CONSTRAINT chk_terminal_receipt_reference CHECK(payment_method NOT IN ('UPI','CARD') OR (sales_channel='IN_STORE' AND payment_reference IS NOT NULL))");
  run(c,"CREATE INDEX ix_terminal_review ON vendor_payment_attempts(tenant_id,origin,state,created_at)");
 }
 private void dropChecks(Connection c,boolean mysql,String table,String column)throws SQLException{
  var names=new ArrayList<String>();String schema=mysql?"DATABASE()":"SCHEMA()";
  try(var s=c.createStatement();var r=s.executeQuery("SELECT t.CONSTRAINT_NAME,k.CHECK_CLAUSE FROM information_schema.TABLE_CONSTRAINTS t JOIN information_schema.CHECK_CONSTRAINTS k ON k.CONSTRAINT_SCHEMA=t.CONSTRAINT_SCHEMA AND k.CONSTRAINT_NAME=t.CONSTRAINT_NAME WHERE t.TABLE_SCHEMA="+schema+" AND t.TABLE_NAME='"+table+"' AND t.CONSTRAINT_TYPE='CHECK'")){while(r.next())if(r.getString(2).toLowerCase(Locale.ROOT).contains(column))names.add(r.getString(1));}
  String q=c.getMetaData().getIdentifierQuoteString().trim();for(String name:names)run(c,"ALTER TABLE "+table+" DROP "+(mysql?"CHECK ":"CONSTRAINT ")+q+name.replace(q,q+q)+q);
 }
 private void run(Connection c,String sql)throws SQLException{try(var s=c.createStatement()){s.execute(sql);}}
}
