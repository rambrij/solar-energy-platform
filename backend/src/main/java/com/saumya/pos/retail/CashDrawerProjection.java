package com.saumya.pos.retail;
import com.saumya.pos.shared.CashTenderRecorded;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
/** Retail owns the drawer; checkout publishes facts without accessing retail tables. */
@Component
public class CashDrawerProjection {
 private final JdbcTemplate db;
 public CashDrawerProjection(JdbcTemplate db){this.db=db;}
 @EventListener @Transactional(propagation=Propagation.MANDATORY)
 public void record(CashTenderRecorded event){
  if(event==null||event.reference()==null||event.reference().isBlank()||event.session()==null||event.received()==null||event.change()==null||event.received().signum()<0||event.change().signum()<0||event.change().compareTo(event.received())>0)throw new IllegalArgumentException("Invalid cash drawer fact");
  db.update("INSERT INTO pos_drawer_tenders(id,tenant_id,branch_id,session_id,order_reference,cash_received,change_given) VALUES(?,?,?,?,?,?,?)",event.reference(),event.tenant(),event.branch(),event.session(),event.order(),event.received(),event.change());
 }
}
