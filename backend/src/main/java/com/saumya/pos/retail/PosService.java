package com.saumya.pos.retail;
import com.saumya.pos.catalog.CatalogService;

import java.math.*;
import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
public class PosService {
    private final JdbcTemplate db;
    private final CatalogService catalogue;
    private final com.saumya.pos.security.AuthorizationContext authContext;
    
    public PosService(
            JdbcTemplate db,
            CatalogService catalogue,
            com.saumya.pos.security.AuthorizationContext authContext
    ) {
        this.db = db;
        this.catalogue = catalogue;
        this.authContext = authContext;
    }
    public record Scope(long tenant, long branch, String cashier) {}
    public record OpenSession(@Positive long registerId, @NotNull @DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal openingFloat) {}
    public enum MovementType { CASH_IN, CASH_OUT, SAFE_DROP }
    public record CashMovement(
        @NotNull MovementType type,
        @NotNull @DecimalMin(value="0.00", inclusive=false) @Digits(integer=12,fraction=2) BigDecimal amount,
        @NotBlank @Size(max=250) String reason) {}
    
    public record Reconciliation(
            String sessionId, String state, BigDecimal openingFloat,
            BigDecimal cashReceived, BigDecimal changeGiven, BigDecimal cashIn,
            BigDecimal cashOut, BigDecimal safeDrops, BigDecimal expectedCash,
            BigDecimal countedCash, BigDecimal variance, List<Map<String, Object>> movements, BigDecimal cashRefunds
    ) {
    }
    
    public Scope scope() {
        var ctx = authContext.current();
        ctx.requireVendorMembership();
        return new Scope(ctx.tenantId(), ctx.branchId(), ctx.username());
    }
    /** Service-boundary guard: controller annotations are not the only authorization check. */
    public void authorize(Scope scope,String expectedActor,String... permissions) {
        var context=authContext.current();
        context.requireVendorMembership();
        if(!context.username().equals(expectedActor)||!context.username().equals(scope.cashier())
            ||!Objects.equals(context.tenantId(),scope.tenant())||!Objects.equals(context.branchId(),scope.branch()))
            throw new ResponseStatusException(FORBIDDEN,"Trusted vendor actor and scope are required");
        context.requirePermissions(permissions);
    }
    public void authorizeCustomer(String expectedActor) {
        var context=authContext.current();
        if(!context.isCustomer()||!context.username().equals(expectedActor))
            throw new ResponseStatusException(FORBIDDEN,"Authenticated customer ownership required");
    }
    public List<Map<String,Object>> products(Scope s) {
        authorize(s,s.cashier(),"POS_VIEW");
        return catalogue.posProducts(s.tenant,s.branch);
    }
    public record SessionSummary(String id,long registerId,BigDecimal openingFloat,String state) {}
    private SessionSummary sessionSummary(java.sql.ResultSet rs,int row) throws java.sql.SQLException {
        return new SessionSummary(rs.getString("id"),rs.getLong("register_id"),rs.getBigDecimal("opening_float"),rs.getString("state"));
    }
    public List<SessionSummary> activeSessions(Scope s) {
        authorize(s,s.cashier(),"POS_VIEW");
        return db.query("SELECT p.id,p.register_id,p.opening_float,p.state FROM pos_sessions p JOIN employee_register_assignments a ON a.tenant_id=p.tenant_id AND a.branch_id=p.branch_id AND a.username=p.cashier AND a.register_id=p.register_id AND a.active=TRUE WHERE p.tenant_id=? AND p.branch_id=? AND p.cashier=? AND p.state IN ('OPEN','CLOSING') ORDER BY p.register_id,p.id",
            this::sessionSummary,s.tenant,s.branch,s.cashier);
    }
    public SessionSummary sessionDetails(Scope s,String id) {
        authorize(s,s.cashier(),"POS_VIEW");
        return db.query("SELECT p.id,p.register_id,p.opening_float,p.state FROM pos_sessions p JOIN employee_register_assignments a ON a.tenant_id=p.tenant_id AND a.branch_id=p.branch_id AND a.username=p.cashier AND a.register_id=p.register_id AND a.active=TRUE WHERE p.tenant_id=? AND p.branch_id=? AND p.cashier=? AND p.id=?",
            this::sessionSummary,s.tenant,s.branch,s.cashier,id).stream().findFirst()
            .orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Resource unavailable"));
    }
    @Transactional
    public SessionSummary resume(Scope s,String id) {
        authorize(s,s.cashier(),"POS_VIEW","POS_SESSION_OPEN");
        var shift=one("SELECT p.state,p.register_id FROM pos_sessions p JOIN employee_register_assignments a ON a.tenant_id=p.tenant_id AND a.branch_id=p.branch_id AND a.username=p.cashier AND a.register_id=p.register_id AND a.active=TRUE WHERE p.tenant_id=? AND p.branch_id=? AND p.cashier=? AND p.id=? FOR UPDATE",s.tenant,s.branch,s.cashier,id);
        if(!Set.of("OPEN","CLOSING").contains(shift.get("state")))fail("Session is closed");
        audit(s,"SESSION_RESUME",id);
        return sessionDetails(s,id);
    }
    @Transactional
    public Map<String,Object> open(Scope s, OpenSession request) {
        authorize(s,s.cashier(),"POS_SESSION_OPEN");
        // Lock the register before checking occupancy, including on MySQL.
        one("SELECT id FROM registers WHERE tenant_id=? AND branch_id=? AND id=? FOR UPDATE",s.tenant,s.branch,request.registerId);
        if(db.queryForList("SELECT register_id FROM employee_register_assignments WHERE tenant_id=? AND branch_id=? AND username=? AND register_id=? AND active=TRUE",s.tenant,s.branch,s.cashier,request.registerId).isEmpty())
            throw new ResponseStatusException(FORBIDDEN,"Cashier is not assigned to this register");
        if (!db.queryForList("SELECT id FROM pos_sessions WHERE tenant_id=? AND register_id=? AND state IN ('OPEN','CLOSING')",s.tenant,request.registerId).isEmpty()) fail("Register already occupied");
        String id=UUID.randomUUID().toString();
        db.update("INSERT INTO pos_sessions(id,tenant_id,branch_id,cashier,register_id,state,opening_float) VALUES(?,?,?,?,?,'OPEN',?)",id,s.tenant,s.branch,s.cashier,request.registerId,request.openingFloat);
        audit(s,"SESSION_OPEN",id);
        return Map.of("id",id);
    }
    private void requireAssignedRegister(Scope s,long registerId) {
        if(db.queryForList("SELECT register_id FROM employee_register_assignments WHERE tenant_id=? AND branch_id=? AND username=? AND register_id=? AND active=TRUE",
            s.tenant,s.branch,s.cashier,registerId).isEmpty())
            throw new ResponseStatusException(FORBIDDEN,"Cashier is no longer assigned to this register");
    }
    private void session(Scope s,String id) {
        var row=one("SELECT p.register_id,p.state FROM pos_sessions p JOIN employee_register_assignments a ON a.tenant_id=p.tenant_id AND a.branch_id=p.branch_id AND a.username=p.cashier AND a.register_id=p.register_id AND a.active=TRUE WHERE p.tenant_id=? AND p.branch_id=? AND p.cashier=? AND p.id=? FOR UPDATE",s.tenant,s.branch,s.cashier,id);
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
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void draftAudit(Scope s,String action,String id){audit(s,action,id);}
    public record CloseResult(String sessionId,long registerId,String cashier,String state,String requestId,
        BigDecimal expectedCash,BigDecimal countedCash,BigDecimal variance,String varianceReason,
        String reviewedBy,String reviewReason) {}
    private CloseResult closeResult(java.sql.ResultSet r,int row) throws java.sql.SQLException {
        return new CloseResult(r.getString("id"),r.getLong("register_id"),r.getString("cashier"),r.getString("state"),
            r.getString("close_request_id"),r.getBigDecimal("expected_cash"),r.getBigDecimal("counted_cash"),
            r.getBigDecimal("cash_variance"),r.getString("variance_reason"),r.getString("reviewed_by"),r.getString("review_reason"));
    }
    private CloseResult closeStatusInternal(Scope s,String id) {
        return db.query("SELECT p.* FROM pos_sessions p JOIN employee_register_assignments a ON a.tenant_id=p.tenant_id AND a.branch_id=p.branch_id AND a.username=p.cashier AND a.register_id=p.register_id AND a.active=TRUE WHERE p.tenant_id=? AND p.branch_id=? AND p.cashier=? AND p.id=?",
            this::closeResult,s.tenant,s.branch,s.cashier,id).stream().findFirst().orElseThrow(()->new ResponseStatusException(NOT_FOUND));
    }
    public CloseResult closeStatus(Scope s,String id) {
        authorize(s,s.cashier(),"POS_VIEW");
        return closeStatusInternal(s,id);
    }
    @Transactional public CloseResult close(Scope s,String id,BigDecimal counted) { return close(s,id,counted,null); }
    @Transactional public CloseResult close(Scope s,String id,BigDecimal counted,String reason) {
        return close(s,id,counted,reason,null);
    }
    @Transactional public CloseResult close(Scope s,String id,BigDecimal counted,String reason,String requestId) {
        authorize(s,s.cashier(),"POS_SESSION_CLOSE");
        var shift=one("SELECT p.* FROM pos_sessions p JOIN employee_register_assignments a ON a.tenant_id=p.tenant_id AND a.branch_id=p.branch_id AND a.username=p.cashier AND a.register_id=p.register_id AND a.active=TRUE WHERE p.tenant_id=? AND p.branch_id=? AND p.cashier=? AND p.id=? FOR UPDATE",s.tenant,s.branch,s.cashier,id);
        String normalized=reason==null||reason.isBlank()?null:reason.strip();
        if(requestId!=null) {
            var reviewed=db.queryForList("SELECT counted_cash,variance_reason FROM pos_shift_reviews WHERE tenant_id=? AND session_id=? AND close_request_id=?",s.tenant,id,requestId);
            if(!reviewed.isEmpty()) {
                if(counted.compareTo((BigDecimal)reviewed.getFirst().get("counted_cash"))!=0||!Objects.equals(normalized,reviewed.getFirst().get("variance_reason")))
                    fail("Close request ID already used with different count or reason");
                return closeStatusInternal(s,id);
            }
        }
        if(!"OPEN".equals(shift.get("state"))) {
            if((requestId==null||requestId.equals(shift.get("close_request_id")))&&counted.compareTo((BigDecimal)shift.get("counted_cash"))==0&&Objects.equals(normalized,shift.get("variance_reason")))
                return closeStatusInternal(s,id);
            fail("Close count is already frozen; supervisor review or a new corrected count is required");
        }
        BigDecimal expected=reconciliationInternal(s,id).expectedCash(),variance=counted.subtract(expected);
        if(!db.queryForList("SELECT order_reference FROM pos_active_drafts WHERE tenant_id=? AND branch_id=? AND cashier=? AND session_id=? FOR UPDATE",s.tenant,s.branch,s.cashier,id).isEmpty())fail("Resolve reserved draft sales before closing the session");
        if(variance.signum()!=0&&normalized==null)throw new ResponseStatusException(BAD_REQUEST,"A variance reason is required");
        String state=variance.signum()==0?"CLOSED":"CLOSING";
        db.update("UPDATE pos_sessions SET state=?,expected_cash=?,counted_cash=?,cash_variance=?,variance_reason=?,close_request_id=?,close_requested_at=CURRENT_TIMESTAMP,reviewed_by=NULL,review_reason=NULL,reviewed_at=NULL,closed_at=CASE WHEN ?='CLOSED' THEN CURRENT_TIMESTAMP ELSE NULL END WHERE tenant_id=? AND id=?",
            state,expected,counted,variance,normalized,requestId==null?UUID.randomUUID().toString():requestId,state,s.tenant,id);
        audit(s,state.equals("CLOSED")?"SESSION_CLOSE":"SHIFT_VARIANCE_SUBMIT",id);
        return closeStatus(s,id);
    }
    @Transactional public List<CloseResult> pendingVariances(Scope s) {
        authorize(s,s.cashier(),"POS_SHIFT_VARIANCE_APPROVE");
        var rows=db.query("SELECT * FROM pos_sessions WHERE tenant_id=? AND branch_id=? AND cashier<>? AND state='CLOSING' ORDER BY close_requested_at,id",
            this::closeResult,s.tenant,s.branch,s.cashier);
        audit(s,"SHIFT_REVIEW_LIST",Long.toString(s.branch));
        return rows;
    }
    public enum ReviewAction { APPROVE, RETURN_FOR_CORRECTION }
    @Transactional public Map<String,Object> reviewVariance(Scope s,String id,String requestId,String reason,ReviewAction action) {
        authorize(s,s.cashier(),"POS_SHIFT_VARIANCE_APPROVE");
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
        authorize(s,s.cashier(),"POS_"+request.type().name());
        if(key==null || !key.matches("[A-Za-z0-9_-]{8,80}"))
            throw new ResponseStatusException(BAD_REQUEST,"Valid Idempotency-Key required");
        // The same session lock protects sales, closing and all drawer movements.
        var shift=one("SELECT p.state,p.register_id FROM pos_sessions p JOIN employee_register_assignments a ON a.tenant_id=p.tenant_id AND a.branch_id=p.branch_id AND a.username=p.cashier AND a.register_id=p.register_id AND a.active=TRUE WHERE p.tenant_id=? AND p.branch_id=? AND p.cashier=? AND p.id=? FOR UPDATE",
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
            request.amount().compareTo(reconciliationInternal(s,sessionId).expectedCash())>0)
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
        authorize(s,s.cashier(),"POS_VIEW");
        return reconciliationInternal(s,id);
    }
    private Reconciliation reconciliationInternal(Scope s,String id) {
        var shift=one("SELECT p.state,p.opening_float,p.counted_cash,p.register_id FROM pos_sessions p JOIN employee_register_assignments a ON a.tenant_id=p.tenant_id AND a.branch_id=p.branch_id AND a.username=p.cashier AND a.register_id=p.register_id AND a.active=TRUE WHERE p.tenant_id=? AND p.branch_id=? AND p.cashier=? AND p.id=? FOR UPDATE",
            s.tenant,s.branch,s.cashier,id);
        var sales=db.queryForList("SELECT cash_received,change_given FROM pos_drawer_tenders WHERE tenant_id=? AND session_id=? ORDER BY id FOR UPDATE",s.tenant,id);
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
    private Map<String,Object> one(String sql,Object...args) {
        return db.queryForList(sql,args).stream().findFirst().orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Resource unavailable"));
    }
    private void audit(Scope s,String action,String id) { db.update("INSERT INTO pos_audit(id,tenant_id,actor,action,resource_id) VALUES(?,?,?,?,?)",UUID.randomUUID().toString(),s.tenant,s.cashier,action,id); }
    private void fail(String message) { throw new ResponseStatusException(CONFLICT,message); }
}
