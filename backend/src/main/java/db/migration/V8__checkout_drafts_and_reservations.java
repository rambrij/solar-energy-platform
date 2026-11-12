package db.migration;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.api.migration.*;

/** Evolves the original anonymous channel check without editing an applied migration. */
public class V8__checkout_drafts_and_reservations extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        Connection c=context.getConnection();boolean mysql=c.getMetaData().getDatabaseProductName().equals("MySQL");
        String schema=mysql?"DATABASE()":"SCHEMA()";
        String query="SELECT t.CONSTRAINT_NAME,k.CHECK_CLAUSE FROM information_schema.TABLE_CONSTRAINTS t JOIN information_schema.CHECK_CONSTRAINTS k ON k.CONSTRAINT_SCHEMA=t.CONSTRAINT_SCHEMA AND k.CONSTRAINT_NAME=t.CONSTRAINT_NAME WHERE t.TABLE_SCHEMA="+schema+" AND t.TABLE_NAME='orders' AND t.CONSTRAINT_TYPE='CHECK'";
        var names=new ArrayList<String>();
        try(var statement=c.createStatement();var rows=statement.executeQuery(query)){while(rows.next())if(rows.getString(2).toLowerCase(Locale.ROOT).contains("sales_channel"))names.add(rows.getString(1));}
        String quote=c.getMetaData().getIdentifierQuoteString().trim();
        for(String name:names)execute(c,"ALTER TABLE orders DROP "+(mysql?"CHECK ":"CONSTRAINT ")+quote+name.replace(quote,quote+quote)+quote);
        for(String[] column:new String[][]{{"branch_id","BIGINT"},{"cashier","VARCHAR(100)"},{"session_id","VARCHAR(36)"},{"cash_received","DECIMAL(19,2)"},{"change_given","DECIMAL(19,2)"},{"receipt_number","BIGINT"}})
            execute(c,"ALTER TABLE orders "+(mysql?"MODIFY COLUMN "+column[0]+" "+column[1]+" NULL":"ALTER COLUMN "+column[0]+" DROP NOT NULL"));
        execute(c,"ALTER TABLE orders ADD status VARCHAR(24) NOT NULL DEFAULT 'PAID'");
        execute(c,"ALTER TABLE orders ADD expires_at TIMESTAMP NULL");
        execute(c,"ALTER TABLE orders ADD stock_branch_id BIGINT NULL");
        execute(c,"ALTER TABLE orders ADD draft_owner VARCHAR(80) NULL");
        execute(c,"ALTER TABLE orders ADD invoice_requested BOOLEAN NOT NULL DEFAULT FALSE");
        execute(c,"ALTER TABLE orders ADD cancellation_reason VARCHAR(250) NULL");
        execute(c,"ALTER TABLE orders ADD status_changed_at TIMESTAMP NULL");
        execute(c,"UPDATE orders SET status_changed_at=created_at");
        execute(c,"UPDATE orders SET stock_branch_id=branch_id");
        execute(c,"CREATE UNIQUE INDEX uk_active_draft_owner ON orders(tenant_id,draft_owner)");
        execute(c,"CREATE UNIQUE INDEX uk_customer_order_key ON orders(customer_id,idempotency_key)");
        execute(c,"ALTER TABLE orders ADD CONSTRAINT fk_online_customer FOREIGN KEY(customer_id) REFERENCES customer_profiles(id)");
        execute(c,"ALTER TABLE orders ADD CONSTRAINT fk_order_stock_branch FOREIGN KEY(tenant_id,stock_branch_id) REFERENCES branches(tenant_id,id)");
        execute(c,"ALTER TABLE orders ADD CONSTRAINT chk_order_channel_context CHECK((sales_channel='ONLINE' AND customer_id IS NOT NULL AND branch_id IS NULL AND cashier IS NULL AND session_id IS NULL AND cash_received IS NULL AND change_given IS NULL AND receipt_number IS NULL) OR (sales_channel='IN_STORE' AND branch_id IS NOT NULL AND cashier IS NOT NULL AND session_id IS NOT NULL AND (status<>'PAID' OR (cash_received IS NOT NULL AND change_given IS NOT NULL AND receipt_number IS NOT NULL))))");
        execute(c,"ALTER TABLE orders ADD CONSTRAINT chk_order_draft_state CHECK(status IN ('DRAFT','PAID','CANCELLED','EXPIRED'))");
        execute(c,"ALTER TABLE tenants ADD online_branch_id BIGINT NULL");
        execute(c,"ALTER TABLE tenants ADD CONSTRAINT fk_vendor_online_branch FOREIGN KEY(id,online_branch_id) REFERENCES branches(tenant_id,id)");
        execute(c,"ALTER TABLE inventory ADD reserved INT NOT NULL DEFAULT 0");
        execute(c,"ALTER TABLE inventory ADD CONSTRAINT chk_reserved_stock CHECK(reserved>=0 AND reserved<=quantity)");
        execute(c,"CREATE TABLE stock_reservations(tenant_id BIGINT NOT NULL,order_id VARCHAR(36) NOT NULL,product_id BIGINT NOT NULL,branch_id BIGINT NOT NULL,quantity INT NOT NULL CHECK(quantity>0),state VARCHAR(16) NOT NULL CHECK(state IN ('HELD','RELEASED','CONSUMED')),expires_at TIMESTAMP NOT NULL,PRIMARY KEY(tenant_id,order_id,product_id),FOREIGN KEY(tenant_id,order_id) REFERENCES orders(tenant_id,id),FOREIGN KEY(tenant_id,branch_id,product_id) REFERENCES inventory(tenant_id,branch_id,product_id))");
        execute(c,"CREATE INDEX ix_draft_expiry ON orders(status,expires_at)");
    }
    private void execute(Connection c,String sql)throws SQLException{try(var statement=c.createStatement()){statement.execute(sql);}}
}
