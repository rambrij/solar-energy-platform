package com.saumya.pos.retail;
import com.saumya.pos.catalog.CatalogService;
import com.saumya.pos.inventory.InventoryService;
import com.saumya.pos.shared.Pricing;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
public class PosService {
    private final JdbcTemplate db;
    private final Pricing pricing;
    private final CatalogService catalogue;
    private final InventoryService stock;
    public PosService(JdbcTemplate db,Pricing pricing,CatalogService catalogue,InventoryService stock) { this.db=db;this.pricing=pricing;this.catalogue=catalogue;this.stock=stock; }
    public record Scope(long tenant, long branch, String cashier) {}
    public record Line(@Positive long productId, @Min(1) @Max(10000) int quantity) {}
    public record Checkout(@NotBlank String sessionId, @NotEmpty @Size(max=100) List<@Valid Line> items,
                           @NotNull @DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal cashReceived,
                           boolean invoiceRequested) {}
    public record OpenSession(@Positive long registerId, @NotNull @DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal openingFloat) {}
    public enum MovementType { CASH_IN, CASH_OUT, SAFE_DROP }
    public record CashMovement(
        @NotNull MovementType type,
        @NotNull @DecimalMin(value="0.00", inclusive=false) @Digits(integer=12,fraction=2) BigDecimal amount,
        @NotBlank @Size(max=250) String reason) {}
    public record Reconciliation(String sessionId, String state, BigDecimal openingFloat,
        BigDecimal cashReceived, BigDecimal changeGiven, BigDecimal cashIn,
        BigDecimal cashOut, BigDecimal safeDrops, BigDecimal expectedCash,
        BigDecimal countedCash, BigDecimal variance, List<Map<String,Object>> movements,BigDecimal cashRefunds) {}
    public Scope scope(String user) {
        return db.query("SELECT e.* FROM employees e JOIN tenants t ON t.id=e.tenant_id WHERE e.username=? AND e.enabled=TRUE AND t.active=TRUE",
            (r,n)->new Scope(r.getLong("tenant_id"),r.getLong("branch_id"),r.getString("username")),user)
            .stream().findFirst().orElseThrow(()->new ResponseStatusException(FORBIDDEN));
    }
    public List<Map<String,Object>> products(Scope s) {
        return catalogue.posProducts(s.tenant,s.branch);
    }
    public record SessionSummary(String id,long registerId,BigDecimal openingFloat,String state) {}
    private SessionSummary sessionSummary(java.sql.ResultSet rs,int row) throws java.sql.SQLException {
        return new SessionSummary(rs.getString("id"),rs.getLong("register_id"),rs.getBigDecimal("opening_float"),rs.getString("state"));
    }
    public List<SessionSummary> activeSessions(Scope s) {
        return db.query("SELECT id,register_id,opening_float,state FROM pos_sessions WHERE tenant_id=? AND branch_id=? AND cashier=? AND state IN ('OPEN','CLOSING') ORDER BY register_id,id",
            this::sessionSummary,s.tenant,s.branch,s.cashier);
    }
    public SessionSummary sessionDetails(Scope s,String id) {
        return db.query("SELECT id,register_id,opening_float,state FROM pos_sessions WHERE tenant_id=? AND branch_id=? AND cashier=? AND id=?",
            this::sessionSummary,s.tenant,s.branch,s.cashier,id).stream().findFirst()
            .orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Resource unavailable"));
    }
    @Transactional
    public SessionSummary resume(Scope s,String id) {
        var shift=one("SELECT state FROM pos_sessions WHERE tenant_id=? AND branch_id=? AND cashier=? AND id=? FOR UPDATE",s.tenant,s.branch,s.cashier,id);
        if(!Set.of("OPEN","CLOSING").contains(shift.get("state")))fail("Session is closed");
        audit(s,"SESSION_RESUME",id);
        return sessionDetails(s,id);
    }
    @Transactional
    public Map<String,Object> open(Scope s, OpenSession request) {
        // Lock the register before checking occupancy, including on MySQL.
        one("SELECT id FROM registers WHERE tenant_id=? AND branch_id=? AND id=? FOR UPDATE",s.tenant,s.branch,request.registerId);
        if (!db.queryForList("SELECT id FROM pos_sessions WHERE tenant_id=? AND register_id=? AND state IN ('OPEN','CLOSING')",s.tenant,request.registerId).isEmpty()) fail("Register already occupied");
        String id=UUID.randomUUID().toString();
        db.update("INSERT INTO pos_sessions(id,tenant_id,branch_id,cashier,register_id,state,opening_float) VALUES(?,?,?,?,?,'OPEN',?)",id,s.tenant,s.branch,s.cashier,request.registerId,request.openingFloat);
        audit(s,"SESSION_OPEN",id);
        return Map.of("id",id);
    }
    private void session(Scope s,String id) {
        var row=one("SELECT state FROM pos_sessions WHERE tenant_id=? AND branch_id=? AND cashier=? AND id=? FOR UPDATE",s.tenant,s.branch,s.cashier,id);
        if (!"OPEN".equals(row.get("state"))) fail("Session is closed");
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void requireOpen(Scope s,String id){session(s,id);}
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void lockOutlet(Scope s){one("SELECT id FROM branches WHERE tenant_id=? AND id=? FOR UPDATE",s.tenant,s.branch);}
    public record ReceiptAllocation(long number,boolean invoiceRequired,String outlet) {}
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public ReceiptAllocation allocateReceipt(Scope s){
        var branch=one("SELECT * FROM branches WHERE tenant_id=? AND id=? FOR UPDATE",s.tenant,s.branch);
        long number=((Number)branch.get("receipt_sequence")).longValue()+1;
        db.update("UPDATE branches SET receipt_sequence=? WHERE tenant_id=? AND id=?",number,s.tenant,s.branch);
        return new ReceiptAllocation(number,Boolean.TRUE.equals(branch.get("invoice_required")),(String)branch.get("name"));
    }
    public record StockIssue(long product,int quantity) {}
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void draftDocuments(Scope s,String id,List<StockIssue> items,ReceiptAllocation receipt,BigDecimal total,boolean invoice){
        for(var item:items)db.update("INSERT INTO inventory_ledger VALUES(?,?,?,?,?)",s.tenant,s.branch,item.product,id,-item.quantity);
        String seller=catalogue.vendor(s.tenant).name();
        db.update("UPDATE orders SET receipt_seller_name=?,receipt_outlet_name=? WHERE tenant_id=? AND branch_id=? AND id=? AND status='PAID' AND receipt_seller_name IS NULL AND receipt_outlet_name IS NULL",seller,receipt.outlet,s.tenant,s.branch,id);
        if(invoice)db.update("INSERT INTO vendor_invoices(tenant_id,order_id,invoice_number,seller_name,outlet_name,total) VALUES(?,?,?,?,?,?)",s.tenant,id,"INV-"+s.branch+"-"+receipt.number,seller,receipt.outlet,total);
        audit(s,"CASH_SALE",id);
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void draftAudit(Scope s,String action,String id){audit(s,action,id);}
    @Transactional
    public Map<String,Object> checkout(Scope s,String key,Checkout request) {
        if(key==null || !key.matches("[A-Za-z0-9_-]{8,80}")) throw new ResponseStatusException(BAD_REQUEST,"Valid Idempotency-Key required");
        // Outlet lock serializes receipt numbers and concurrent retries; stock rows are shared with any future online checkout.
        var branch=one("SELECT * FROM branches WHERE tenant_id=? AND id=? FOR UPDATE",s.tenant,s.branch);
        String fingerprint=hash(request);
        var previous=db.queryForList("SELECT id,request_hash,cashier FROM orders WHERE tenant_id=? AND idempotency_key=?",s.tenant,key);
        if(!previous.isEmpty()) {
            if(!fingerprint.equals(previous.getFirst().get("request_hash")) || !s.cashier.equals(previous.getFirst().get("cashier"))) fail("Idempotency key already used");
            return receipt(s,(String)previous.getFirst().get("id"));
        }
        session(s,request.sessionId);
        var lines=new ArrayList<Map<String,Object>>();
        var seen=new HashSet<Long>(); BigDecimal total=BigDecimal.ZERO;
        for(var item:request.items.stream().sorted(Comparator.comparingLong(Line::productId)).toList()) {
            if(!seen.add(item.productId)) fail("Duplicate product");
            var p=catalogue.lockForSale(s.tenant,item.productId);
            if(stock.atBranch(s.tenant,s.branch,item.productId)<item.quantity) fail("Insufficient stock");
            BigDecimal price=p.price(), rate=p.taxRate();
            var calculated=pricing.line(price,rate,item.quantity);
            BigDecimal tax=calculated.tax(),amount=calculated.total();total=total.add(amount);
            lines.add(Map.of("id",item.productId,"name",p.name(),"quantity",item.quantity,"price",price,"rate",rate,"tax",tax,"total",amount,"list_price",p.listPrice(),"discount",p.listPrice().subtract(price).multiply(BigDecimal.valueOf(item.quantity)),"discount_percent",p.discountPercent()));
        }
        if(request.cashReceived.compareTo(total)<0) fail("Cash received is below amount due");
        String id=UUID.randomUUID().toString(); long number=((Number)branch.get("receipt_sequence")).longValue()+1;
        db.update("UPDATE branches SET receipt_sequence=? WHERE tenant_id=? AND id=?",number,s.tenant,s.branch);
        db.update("INSERT INTO orders(id,tenant_id,branch_id,cashier,session_id,sales_channel,idempotency_key,request_hash,total,cash_received,change_given,receipt_number,tax_invoice) VALUES(?,?,?,?,?,'IN_STORE',?,?,?,?,?,?,?)",id,s.tenant,s.branch,s.cashier,request.sessionId,key,fingerprint,total,request.cashReceived,request.cashReceived.subtract(total),number,request.invoiceRequested || Boolean.TRUE.equals(branch.get("invoice_required")));
        for(var line:lines) {
            db.update("INSERT INTO order_items(tenant_id,order_id,product_id,name,quantity,price,tax_rate,tax,total,list_price,discount,discount_percent) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",s.tenant,id,line.get("id"),line.get("name"),line.get("quantity"),line.get("price"),line.get("rate"),line.get("tax"),line.get("total"),line.get("list_price"),line.get("discount"),line.get("discount_percent"));
            int q=(Integer)line.get("quantity");
            stock.issue(s.tenant,s.branch,((Number)line.get("id")).longValue(),q);
            db.update("INSERT INTO inventory_ledger VALUES(?,?,?,?,?)",s.tenant,s.branch,line.get("id"),id,-q);
        }
        String seller=catalogue.vendor(s.tenant).name();
        db.update("UPDATE orders SET receipt_seller_name=?,receipt_outlet_name=? WHERE tenant_id=? AND branch_id=? AND id=?",seller,branch.get("name"),s.tenant,s.branch,id);
        if(request.invoiceRequested || Boolean.TRUE.equals(branch.get("invoice_required"))) {
            db.update("INSERT INTO vendor_invoices(tenant_id,order_id,invoice_number,seller_name,outlet_name,total) VALUES(?,?,?,?,?,?)",s.tenant,id,"INV-"+s.branch+"-"+number,seller,branch.get("name"),total);
            audit(s,"INVOICE_ISSUE",id);
        }
        audit(s,"CASH_SALE",id);
        return receipt(s,id);
    }
    @Transactional
    public Map<String,Object> receipt(Scope s,String id) {
        var order=one("SELECT id,branch_id,sales_channel,customer_id,total,cash_received,change_given,receipt_number,tax_invoice,created_at,COALESCE(status_changed_at,created_at) AS issued_at,receipt_seller_name,receipt_outlet_name FROM orders WHERE tenant_id=? AND branch_id=? AND id=? AND status='PAID' FOR UPDATE",s.tenant,s.branch,id);
        order.put("items",db.queryForList("SELECT name,quantity,price,tax_rate,tax,total,list_price,discount,discount_percent FROM order_items WHERE tenant_id=? AND order_id=? ORDER BY product_id FOR UPDATE",s.tenant,id));
        order.put("invoices",db.queryForList("SELECT invoice_number,seller_name,outlet_name,total,issued_at FROM vendor_invoices WHERE tenant_id=? AND order_id=? FOR UPDATE",s.tenant,id));
        return order;
    }
    public record CloseResult(String sessionId,long registerId,String cashier,String state,String requestId,
        BigDecimal expectedCash,BigDecimal countedCash,BigDecimal variance,String varianceReason,
        String reviewedBy,String reviewReason) {}
    private CloseResult closeResult(java.sql.ResultSet r,int row) throws java.sql.SQLException {
        return new CloseResult(r.getString("id"),r.getLong("register_id"),r.getString("cashier"),r.getString("state"),
            r.getString("close_request_id"),r.getBigDecimal("expected_cash"),r.getBigDecimal("counted_cash"),
            r.getBigDecimal("cash_variance"),r.getString("variance_reason"),r.getString("reviewed_by"),r.getString("review_reason"));
    }
    public CloseResult closeStatus(Scope s,String id) {
        return db.query("SELECT * FROM pos_sessions WHERE tenant_id=? AND branch_id=? AND cashier=? AND id=?",
            this::closeResult,s.tenant,s.branch,s.cashier,id).stream().findFirst().orElseThrow(()->new ResponseStatusException(NOT_FOUND));
    }
    @Transactional public CloseResult close(Scope s,String id,BigDecimal counted) { return close(s,id,counted,null); }
    @Transactional public CloseResult close(Scope s,String id,BigDecimal counted,String reason) {
        return close(s,id,counted,reason,null);
    }
    @Transactional public CloseResult close(Scope s,String id,BigDecimal counted,String reason,String requestId) {
        var shift=one("SELECT * FROM pos_sessions WHERE tenant_id=? AND branch_id=? AND cashier=? AND id=? FOR UPDATE",s.tenant,s.branch,s.cashier,id);
        String normalized=reason==null||reason.isBlank()?null:reason.strip();
        if(requestId!=null) {
            var reviewed=db.queryForList("SELECT counted_cash,variance_reason FROM pos_shift_reviews WHERE tenant_id=? AND session_id=? AND close_request_id=?",s.tenant,id,requestId);
            if(!reviewed.isEmpty()) {
                if(counted.compareTo((BigDecimal)reviewed.getFirst().get("counted_cash"))!=0||!Objects.equals(normalized,reviewed.getFirst().get("variance_reason")))
                    fail("Close request ID already used with different count or reason");
                return closeStatus(s,id);
            }
        }
        if(!"OPEN".equals(shift.get("state"))) {
            if((requestId==null||requestId.equals(shift.get("close_request_id")))&&counted.compareTo((BigDecimal)shift.get("counted_cash"))==0&&Objects.equals(normalized,shift.get("variance_reason")))
                return closeStatus(s,id);
            fail("Close count is already frozen; supervisor review or a new corrected count is required");
        }
        BigDecimal expected=reconciliation(s,id).expectedCash(),variance=counted.subtract(expected);
        if(!db.queryForList("SELECT id FROM orders WHERE tenant_id=? AND session_id=? AND status='DRAFT'",s.tenant,id).isEmpty())fail("Resolve reserved draft sales before closing the session");
        if(variance.signum()!=0&&normalized==null)throw new ResponseStatusException(BAD_REQUEST,"A variance reason is required");
        String state=variance.signum()==0?"CLOSED":"CLOSING";
        db.update("UPDATE pos_sessions SET state=?,expected_cash=?,counted_cash=?,cash_variance=?,variance_reason=?,close_request_id=?,close_requested_at=CURRENT_TIMESTAMP,reviewed_by=NULL,review_reason=NULL,reviewed_at=NULL,closed_at=CASE WHEN ?='CLOSED' THEN CURRENT_TIMESTAMP ELSE NULL END WHERE tenant_id=? AND id=?",
            state,expected,counted,variance,normalized,requestId==null?UUID.randomUUID().toString():requestId,state,s.tenant,id);
        audit(s,state.equals("CLOSED")?"SESSION_CLOSE":"SHIFT_VARIANCE_SUBMIT",id);
        return closeStatus(s,id);
    }
    @Transactional public List<CloseResult> pendingVariances(Scope s) {
        var rows=db.query("SELECT * FROM pos_sessions WHERE tenant_id=? AND branch_id=? AND cashier<>? AND state='CLOSING' ORDER BY close_requested_at,id",
            this::closeResult,s.tenant,s.branch,s.cashier);
        audit(s,"SHIFT_REVIEW_LIST",Long.toString(s.branch));
        return rows;
    }
    public enum ReviewAction { APPROVE, RETURN_FOR_CORRECTION }
    @Transactional public Map<String,Object> reviewVariance(Scope s,String id,String requestId,String reason,ReviewAction action) {
        var shift=one("SELECT * FROM pos_sessions WHERE tenant_id=? AND branch_id=? AND id=? FOR UPDATE",s.tenant,s.branch,id);
        if(s.cashier.equals(shift.get("cashier")))throw new ResponseStatusException(FORBIDDEN,"A different supervisor must review the count");
        var previous=db.queryForList("SELECT * FROM pos_shift_reviews WHERE tenant_id=? AND session_id=? AND close_request_id=?",s.tenant,id,requestId);
        String normalized=reason.strip();
        if(!previous.isEmpty()) {
            var decision=previous.getFirst();
            if(!s.cashier.equals(decision.get("reviewer"))||!action.name().equals(decision.get("decision"))||!normalized.equals(decision.get("reason")))
                fail("This close request already has a different review decision");
            return decision;
        }
        if(!"CLOSING".equals(shift.get("state"))||!requestId.equals(shift.get("close_request_id")))fail("Close request is no longer awaiting this review");
        db.update("INSERT INTO pos_shift_reviews(id,tenant_id,branch_id,cashier,session_id,close_request_id,reviewer,decision,reason,variance_reason,expected_cash,counted_cash,cash_variance) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
            UUID.randomUUID().toString(),s.tenant,s.branch,shift.get("cashier"),id,requestId,s.cashier,action.name(),normalized,
            shift.get("variance_reason"),shift.get("expected_cash"),shift.get("counted_cash"),shift.get("cash_variance"));
        if(action==ReviewAction.APPROVE) {
            db.update("UPDATE pos_sessions SET state='CLOSED',reviewed_by=?,review_reason=?,reviewed_at=CURRENT_TIMESTAMP,closed_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=?",
                s.cashier,normalized,s.tenant,id);
        }else{
            db.update("UPDATE pos_sessions SET state='OPEN',expected_cash=NULL,counted_cash=NULL,cash_variance=NULL,variance_reason=NULL,close_request_id=NULL,close_requested_at=NULL,reviewed_by=?,review_reason=?,reviewed_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=?",s.cashier,normalized,s.tenant,id);
        }
        audit(s,action==ReviewAction.APPROVE?"SHIFT_VARIANCE_APPROVE":"SHIFT_VARIANCE_RETURN",id);
        return one("SELECT * FROM pos_shift_reviews WHERE tenant_id=? AND session_id=? AND close_request_id=?",s.tenant,id,requestId);
    }
    @Transactional
    public Map<String,Object> cashMovement(Scope s, String sessionId, String key, CashMovement request) {
        if(key==null || !key.matches("[A-Za-z0-9_-]{8,80}"))
            throw new ResponseStatusException(BAD_REQUEST,"Valid Idempotency-Key required");
        // The same session lock protects sales, closing and all drawer movements.
        var shift=one("SELECT state FROM pos_sessions WHERE tenant_id=? AND branch_id=? AND cashier=? AND id=? FOR UPDATE",
            s.tenant,s.branch,s.cashier,sessionId);
        String reason=request.reason().strip();
        var existing=db.queryForList("SELECT * FROM pos_cash_movements WHERE tenant_id=? AND session_id=? AND idempotency_key=?",
            s.tenant,sessionId,key);
        // An acknowledged retry is safe even after closing; no closed-session data is changed.
        if(!existing.isEmpty()) {
            var row=existing.getFirst();
            if(!request.type().name().equals(row.get("movement_type")) ||
                request.amount().compareTo((BigDecimal)row.get("amount"))!=0 || !reason.equals(row.get("reason")))
                fail("Idempotency key already used for a different cash movement");
            return movement(s,sessionId,(String)row.get("id"));
        }
        if(!"OPEN".equals(shift.get("state"))) fail("Session is closed");
        if(request.type()!=MovementType.CASH_IN &&
            request.amount().compareTo(reconciliation(s,sessionId).expectedCash())>0)
            fail("Cash removal exceeds expected drawer cash");
        String id=UUID.randomUUID().toString();
        db.update("INSERT INTO pos_cash_movements(id,tenant_id,branch_id,cashier,session_id,movement_type,amount,reason,idempotency_key) VALUES(?,?,?,?,?,?,?,?,?)",
            id,s.tenant,s.branch,s.cashier,sessionId,request.type().name(),request.amount(),reason,key);
        audit(s,request.type().name(),id);
        return movement(s,sessionId,id);
    }
    private Map<String,Object> movement(Scope s,String sessionId,String id) {
        return one("SELECT id,movement_type,amount,reason,cashier,created_at FROM pos_cash_movements WHERE tenant_id=? AND session_id=? AND id=?",
            s.tenant,sessionId,id);
    }
    @Transactional
    public Reconciliation reconciliation(Scope s,String id) {
        var shift=one("SELECT state,opening_float,counted_cash FROM pos_sessions WHERE tenant_id=? AND branch_id=? AND cashier=? AND id=? FOR UPDATE",
            s.tenant,s.branch,s.cashier,id);
        var sales=db.queryForList("SELECT cash_received,change_given FROM orders WHERE tenant_id=? AND session_id=? AND status='PAID' ORDER BY id FOR UPDATE",s.tenant,id);
        var refunds=db.queryForList("SELECT amount FROM pos_cash_refunds WHERE tenant_id=? AND session_id=? ORDER BY id FOR UPDATE",s.tenant,id);
        var movements=db.queryForList("SELECT id,movement_type,amount,reason,cashier,created_at FROM pos_cash_movements WHERE tenant_id=? AND session_id=? ORDER BY created_at,id FOR UPDATE",
            s.tenant,id);
        BigDecimal cashIn=BigDecimal.ZERO,cashOut=BigDecimal.ZERO,drops=BigDecimal.ZERO;
        for(var row:movements) {
            BigDecimal amount=(BigDecimal)row.get("amount");
            switch(MovementType.valueOf((String)row.get("movement_type"))) {
                case CASH_IN -> cashIn=cashIn.add(amount);
                case CASH_OUT -> cashOut=cashOut.add(amount);
                case SAFE_DROP -> drops=drops.add(amount);
            }
        }
        BigDecimal opening=(BigDecimal)shift.get("opening_float"),received=BigDecimal.ZERO,change=BigDecimal.ZERO,cashRefunds=BigDecimal.ZERO;
        for(var sale:sales){received=received.add((BigDecimal)sale.get("cash_received"));change=change.add((BigDecimal)sale.get("change_given"));}
        for(var refund:refunds)cashRefunds=cashRefunds.add((BigDecimal)refund.get("amount"));
        BigDecimal expected=opening.add(received).subtract(change).add(cashIn).subtract(cashOut).subtract(drops).subtract(cashRefunds);
        BigDecimal counted=(BigDecimal)shift.get("counted_cash");
        return new Reconciliation(id,(String)shift.get("state"),opening,received,change,cashIn,cashOut,drops,expected,counted,
            counted==null?null:counted.subtract(expected),movements,cashRefunds);
    }
    @Transactional public Map<String,Object> reprint(Scope s,String id) {
        var result=receipt(s,id); audit(s,"RECEIPT_REPRINT",id);result.put("copy",true);return result;
    }
    private String hash(Checkout r) {
        try {
            String value=r.sessionId+"|"+r.items.stream().sorted(Comparator.comparingLong(Line::productId)).toList()+"|"+r.cashReceived.setScale(2)+"|"+r.invoiceRequested;
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private Map<String,Object> one(String sql,Object...args) {
        return db.queryForList(sql,args).stream().findFirst().orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Resource unavailable"));
    }
    private void audit(Scope s,String action,String id) { db.update("INSERT INTO pos_audit(id,tenant_id,actor,action,resource_id) VALUES(?,?,?,?,?)",UUID.randomUUID().toString(),s.tenant,s.cashier,action,id); }
    private void fail(String message) { throw new ResponseStatusException(CONFLICT,message); }
}
