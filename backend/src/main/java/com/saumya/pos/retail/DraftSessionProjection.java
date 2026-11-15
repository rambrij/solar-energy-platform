package com.saumya.pos.retail;
import com.saumya.pos.shared.PosDraftStateChanged;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
@Component
public class DraftSessionProjection {
 private final JdbcTemplate db;
 public DraftSessionProjection(JdbcTemplate db){this.db=db;}
 @EventListener @Transactional(propagation=Propagation.MANDATORY)
 public void changed(PosDraftStateChanged event){if("DRAFT".equals(event.state()))db.update("INSERT INTO pos_active_drafts(tenant_id,branch_id,cashier,session_id,order_reference) VALUES(?,?,?,?,?)",event.tenant(),event.branch(),event.cashier(),event.session(),event.order());else db.update("DELETE FROM pos_active_drafts WHERE tenant_id=? AND branch_id=? AND cashier=? AND session_id=? AND order_reference=?",event.tenant(),event.branch(),event.cashier(),event.session(),event.order());}
}
