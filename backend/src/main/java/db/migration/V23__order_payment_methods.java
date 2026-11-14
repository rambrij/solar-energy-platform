package db.migration;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.api.migration.*;
public class V23__order_payment_methods extends BaseJavaMigration {
 public void migrate(Context context)throws Exception{
  var c=context.getConnection();boolean mysql=c.getMetaData().getDatabaseProductName().equals("MySQL");
  var names=new ArrayList<String>();String schema=mysql?"DATABASE()":"SCHEMA()";
  try(var s=c.createStatement();var r=s.executeQuery("SELECT t.CONSTRAINT_NAME,k.CHECK_CLAUSE FROM information_schema.TABLE_CONSTRAINTS t JOIN information_schema.CHECK_CONSTRAINTS k ON k.CONSTRAINT_SCHEMA=t.CONSTRAINT_SCHEMA AND k.CONSTRAINT_NAME=t.CONSTRAINT_NAME WHERE t.TABLE_SCHEMA="+schema+" AND t.TABLE_NAME='orders' AND t.CONSTRAINT_TYPE='CHECK'")){
   while(r.next()){String clause=r.getString(2).toLowerCase(Locale.ROOT);if(clause.contains("cash_received")&&!clause.contains("sales_channel"))names.add(r.getString(1));}
  }
  String quote=c.getMetaData().getIdentifierQuoteString().trim();
  for(String name:names)run(c,"ALTER TABLE orders DROP "+(mysql?"CHECK ":"CONSTRAINT ")+quote+name.replace(quote,quote+quote)+quote);
  run(c,"ALTER TABLE orders ADD payment_method VARCHAR(16) NOT NULL DEFAULT 'CASH'");
  run(c,"ALTER TABLE orders ADD CONSTRAINT chk_sale_payment_method CHECK(payment_method IN ('CASH','MOCK_UPI','MOCK_CARD'))");
  run(c,"ALTER TABLE orders ADD CONSTRAINT chk_sale_cash_amount CHECK(status<>'PAID' OR sales_channel='ONLINE' OR (payment_method='CASH' AND cash_received-change_given=total) OR (payment_method IN ('MOCK_UPI','MOCK_CARD') AND cash_received=0 AND change_given=0))");
 }
 private void run(Connection c,String sql)throws SQLException{try(var s=c.createStatement()){s.execute(sql);}}
}
