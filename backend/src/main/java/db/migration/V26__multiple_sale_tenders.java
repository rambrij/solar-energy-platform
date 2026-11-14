package db.migration;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.api.migration.*;
public class V26__multiple_sale_tenders extends BaseJavaMigration {
 public void migrate(Context context)throws Exception{
  var c=context.getConnection();boolean mysql=c.getMetaData().getDatabaseProductName().equals("MySQL");
  run(c,"CREATE INDEX ix_tenders_order ON vendor_payment_attempts(tenant_id,order_id)");
  var names=new ArrayList<String>();
  String schema=mysql?"DATABASE()":"SCHEMA()";
  try(var s=c.createStatement();var r=s.executeQuery("SELECT t.CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS t JOIN information_schema.KEY_COLUMN_USAGE k ON k.CONSTRAINT_SCHEMA=t.CONSTRAINT_SCHEMA AND k.CONSTRAINT_NAME=t.CONSTRAINT_NAME AND k.TABLE_NAME=t.TABLE_NAME WHERE t.TABLE_SCHEMA="+schema+" AND t.TABLE_NAME='vendor_payment_attempts' AND t.CONSTRAINT_TYPE='UNIQUE' GROUP BY t.CONSTRAINT_NAME HAVING COUNT(*)=2 AND SUM(CASE WHEN k.COLUMN_NAME IN ('tenant_id','order_id') THEN 1 ELSE 0 END)=2")){while(r.next())names.add(r.getString(1));}
  for(var name:names)run(c,"ALTER TABLE vendor_payment_attempts DROP "+(mysql?"INDEX ":"CONSTRAINT ")+c.getMetaData().getIdentifierQuoteString()+name+c.getMetaData().getIdentifierQuoteString());
  try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM vendor_payment_attempts WHERE origin='RETIRED_POS_SIMULATION'")){r.next();if(r.getLong(1)>0)throw new SQLException("Resolve retired test POS simulation records before upgrading; financial rows are not deleted automatically");}
  dropChecks(c,mysql,"vendor_payment_attempts",Set.of("method","origin"));
  dropChecks(c,mysql,"orders",Set.of("payment_method"));
  run(c,"ALTER TABLE branches ADD split_tender_enabled BOOLEAN NOT NULL DEFAULT FALSE");
  run(c,"ALTER TABLE vendor_payment_attempts ADD idempotency_key VARCHAR(80) NULL");
  run(c,"UPDATE vendor_payment_attempts SET idempotency_key=id");
  run(c,"ALTER TABLE vendor_payment_attempts ADD request_hash VARCHAR(64) NULL");
  run(c,"UPDATE vendor_payment_attempts SET request_hash=id");
  run(c,"ALTER TABLE vendor_payment_attempts "+(mysql?"MODIFY COLUMN idempotency_key VARCHAR(80) NOT NULL":"ALTER COLUMN idempotency_key SET NOT NULL"));
  run(c,"ALTER TABLE vendor_payment_attempts "+(mysql?"MODIFY COLUMN request_hash VARCHAR(64) NOT NULL":"ALTER COLUMN request_hash SET NOT NULL"));
  run(c,"ALTER TABLE vendor_payment_attempts ADD cash_received DECIMAL(19,2) NULL");
  run(c,"ALTER TABLE vendor_payment_attempts ADD change_given DECIMAL(19,2) NULL");
  run(c,"ALTER TABLE vendor_payment_attempts ADD CONSTRAINT uk_tender_key UNIQUE(tenant_id,order_id,idempotency_key)");
  run(c,"ALTER TABLE vendor_payment_attempts ADD CONSTRAINT chk_tender_method CHECK(method IN ('UPI','CARD','CASH'))");
  run(c,"ALTER TABLE vendor_payment_attempts ADD CONSTRAINT chk_tender_origin CHECK(origin IN ('ONLINE_MOCK','POS_TERMINAL','POS_CASH'))");
  run(c,"ALTER TABLE vendor_payment_attempts ADD CONSTRAINT chk_cash_tender CHECK(origin<>'POS_CASH' OR (method='CASH' AND state='CONFIRMED' AND cash_received>=amount AND change_given>=0 AND cash_received-change_given=amount))");
  run(c,"ALTER TABLE vendor_payment_attempts ADD CONSTRAINT chk_terminal_evidence CHECK(origin<>'POS_TERMINAL' OR state NOT IN ('CONFIRMED','FAILED') OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND provider_reference IS NOT NULL AND evidence_reference IS NOT NULL AND review_reason IS NOT NULL))");
  run(c,"ALTER TABLE orders ADD CONSTRAINT chk_sale_payment_method CHECK(payment_method IN ('CASH','MOCK_UPI','MOCK_CARD','UPI','CARD','SPLIT'))");
  run(c,"ALTER TABLE orders ADD CONSTRAINT chk_sale_cash_amount CHECK(status<>'PAID' OR sales_channel='ONLINE' OR (payment_method='CASH' AND cash_received-change_given=total) OR (payment_method IN ('UPI','CARD') AND cash_received=0 AND change_given=0) OR (payment_method='SPLIT' AND cash_received>=change_given AND cash_received-change_given<=total))");
  run(c,"ALTER TABLE orders ADD CONSTRAINT chk_terminal_receipt_reference CHECK(payment_method NOT IN ('UPI','CARD') OR (sales_channel='IN_STORE' AND payment_reference IS NOT NULL))");
 }
 private void dropChecks(Connection c,boolean mysql,String table,Set<String> columns)throws SQLException{
  var names=new ArrayList<String>();String schema=mysql?"DATABASE()":"SCHEMA()";
  try(var s=c.createStatement();var r=s.executeQuery("SELECT t.CONSTRAINT_NAME,k.CHECK_CLAUSE FROM information_schema.TABLE_CONSTRAINTS t JOIN information_schema.CHECK_CONSTRAINTS k ON k.CONSTRAINT_SCHEMA=t.CONSTRAINT_SCHEMA AND k.CONSTRAINT_NAME=t.CONSTRAINT_NAME WHERE t.TABLE_SCHEMA="+schema+" AND t.TABLE_NAME='"+table+"' AND t.CONSTRAINT_TYPE='CHECK'")){while(r.next()){String clause=r.getString(2).toLowerCase(Locale.ROOT);if(columns.stream().anyMatch(clause::contains))names.add(r.getString(1));}}
  String q=c.getMetaData().getIdentifierQuoteString();for(String name:names)run(c,"ALTER TABLE "+table+" DROP "+(mysql?"CHECK ":"CONSTRAINT ")+q+name+q);
 }
 private void run(Connection c,String sql)throws SQLException{try(var s=c.createStatement()){s.execute(sql);}}
}
