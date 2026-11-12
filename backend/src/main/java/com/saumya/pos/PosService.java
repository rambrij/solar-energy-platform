package com.saumya.pos;

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
    public PosService(JdbcTemplate db) { this.db=db; }
    public record Scope(long tenant, long branch, String cashier) {}
    public record Line(@Positive long productId, @Min(1) @Max(10000) int quantity) {}
    public record Checkout(@NotBlank String sessionId, @NotEmpty @Size(max=100) List<@Valid Line> items,
                           @NotNull @DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal cashReceived,
                           boolean invoiceRequested) {}
    public record OpenSession(@Positive long registerId, @NotNull @DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal openingFloat) {}
    public Scope scope(String user) {
        return db.query("SELECT e.* FROM employees e JOIN tenants t ON t.id=e.tenant_id WHERE e.username=? AND e.enabled=TRUE AND t.active=TRUE",
            (r,n)->new Scope(r.getLong("tenant_id"),r.getLong("branch_id"),user),user)
            .stream().findFirst().orElseThrow(()->new ResponseStatusException(FORBIDDEN));
    }
    public List<Map<String,Object>> products(Scope s) {
        return db.queryForList("SELECT p.id,p.sku,p.barcode,p.name,p.price,p.tax_rate,i.quantity FROM products p JOIN inventory i ON i.tenant_id=p.tenant_id AND i.product_id=p.id WHERE p.tenant_id=? AND i.branch_id=? AND p.published=TRUE ORDER BY p.id",s.tenant,s.branch);
    }
    @Transactional
    public Map<String,Object> open(Scope s, OpenSession request) {
        // Lock the register before checking occupancy, including on MySQL.
        one("SELECT id FROM registers WHERE tenant_id=? AND branch_id=? AND id=? FOR UPDATE",s.tenant,s.branch,request.registerId);
        if (!db.queryForList("SELECT id FROM pos_sessions WHERE tenant_id=? AND register_id=? AND state='OPEN'",s.tenant,request.registerId).isEmpty()) fail("Register already occupied");
        String id=UUID.randomUUID().toString();
        db.update("INSERT INTO pos_sessions(id,tenant_id,branch_id,cashier,register_id,state,opening_float) VALUES(?,?,?,?,?,'OPEN',?)",id,s.tenant,s.branch,s.cashier,request.registerId,request.openingFloat);
        audit(s,"SESSION_OPEN",id);
        return Map.of("id",id);
    }
    private void session(Scope s,String id) {
        var row=one("SELECT state FROM pos_sessions WHERE tenant_id=? AND branch_id=? AND cashier=? AND id=? FOR UPDATE",s.tenant,s.branch,s.cashier,id);
        if (!"OPEN".equals(row.get("state"))) fail("Session is closed");
    }
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
            var p=one("SELECT p.name,p.price,p.tax_rate,i.quantity FROM products p JOIN inventory i ON i.tenant_id=p.tenant_id AND i.product_id=p.id WHERE p.tenant_id=? AND i.branch_id=? AND p.id=? AND p.published=TRUE FOR UPDATE",s.tenant,s.branch,item.productId);
            if(((Number)p.get("quantity")).intValue()<item.quantity) fail("Insufficient stock");
            BigDecimal price=(BigDecimal)p.get("price"), rate=(BigDecimal)p.get("tax_rate");
            BigDecimal net=price.multiply(BigDecimal.valueOf(item.quantity));
            BigDecimal tax=net.multiply(rate).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);
            BigDecimal amount=net.add(tax); total=total.add(amount);
            lines.add(Map.of("id",item.productId,"name",p.get("name"),"quantity",item.quantity,"price",price,"rate",rate,"tax",tax,"total",amount));
        }
        if(request.cashReceived.compareTo(total)<0) fail("Cash received is below amount due");
        String id=UUID.randomUUID().toString(); long number=((Number)branch.get("receipt_sequence")).longValue()+1;
        db.update("UPDATE branches SET receipt_sequence=? WHERE tenant_id=? AND id=?",number,s.tenant,s.branch);
        db.update("INSERT INTO orders(id,tenant_id,branch_id,cashier,session_id,sales_channel,idempotency_key,request_hash,total,cash_received,change_given,receipt_number,tax_invoice) VALUES(?,?,?,?,?,'IN_STORE',?,?,?,?,?,?,?)",id,s.tenant,s.branch,s.cashier,request.sessionId,key,fingerprint,total,request.cashReceived,request.cashReceived.subtract(total),number,request.invoiceRequested || Boolean.TRUE.equals(branch.get("invoice_required")));
        for(var line:lines) {
            db.update("INSERT INTO order_items VALUES(?,?,?,?,?,?,?,?,?)",s.tenant,id,line.get("id"),line.get("name"),line.get("quantity"),line.get("price"),line.get("rate"),line.get("tax"),line.get("total"));
            int q=(Integer)line.get("quantity");
            if(db.update("UPDATE inventory SET quantity=quantity-? WHERE tenant_id=? AND branch_id=? AND product_id=? AND quantity>=?",q,s.tenant,s.branch,line.get("id"),q)!=1) fail("Insufficient stock");
            db.update("INSERT INTO inventory_ledger VALUES(?,?,?,?,?)",s.tenant,s.branch,line.get("id"),id,-q);
        }
        if(request.invoiceRequested || Boolean.TRUE.equals(branch.get("invoice_required"))) {
            String seller=db.queryForObject("SELECT name FROM tenants WHERE id=?",String.class,s.tenant);
            db.update("INSERT INTO vendor_invoices(tenant_id,order_id,invoice_number,seller_name,outlet_name,total) VALUES(?,?,?,?,?,?)",s.tenant,id,"INV-"+s.branch+"-"+number,seller,branch.get("name"),total);
            audit(s,"INVOICE_ISSUE",id);
        }
        audit(s,"CASH_SALE",id);
        return receipt(s,id);
    }
    public Map<String,Object> receipt(Scope s,String id) {
        var order=one("SELECT id,branch_id,sales_channel,customer_id,total,cash_received,change_given,receipt_number,tax_invoice,created_at FROM orders WHERE tenant_id=? AND branch_id=? AND id=?",s.tenant,s.branch,id);
        order.put("items",db.queryForList("SELECT name,quantity,price,tax_rate,tax,total FROM order_items WHERE tenant_id=? AND order_id=? ORDER BY product_id",s.tenant,id));
        order.put("invoices",db.queryForList("SELECT invoice_number,seller_name,outlet_name,total,issued_at FROM vendor_invoices WHERE tenant_id=? AND order_id=?",s.tenant,id));
        return order;
    }
    @Transactional public Map<String,Object> close(Scope s,String id,BigDecimal counted) {
        session(s,id);
        var shift=one("SELECT opening_float FROM pos_sessions WHERE tenant_id=? AND id=?",s.tenant,id);
        BigDecimal sales=db.queryForObject("SELECT COALESCE(SUM(cash_received-change_given),0) FROM orders WHERE tenant_id=? AND session_id=?",BigDecimal.class,s.tenant,id);
        BigDecimal expected=((BigDecimal)shift.get("opening_float")).add(sales);
        if(counted.compareTo(expected)!=0) fail("Cash variance requires supervisor workflow; session remains open");
        db.update("UPDATE pos_sessions SET state='CLOSED',expected_cash=?,counted_cash=?,closed_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=?",expected,counted,s.tenant,id);
        audit(s,"SESSION_CLOSE",id);
        return Map.of("expectedCash",expected,"countedCash",counted,"variance",BigDecimal.ZERO);
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
