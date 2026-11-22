package com.saumya.pos.returns.internal;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
public interface ReturnEventRepository extends JpaRepository<ReturnEvent,String>{
 @Query(value="""
  select new com.saumya.pos.returns.internal.ReturnAuditRow(e.id,e.actor,e.action,e.created)
  from ReturnEvent e join ReturnCase r on r.tenant=e.tenant and r.id=e.returnId
  where r.tenant=:tenant and r.branch=:branch and r.id=:returned
  order by e.created desc,e.id desc
  """,countQuery="""
  select count(e) from ReturnEvent e join ReturnCase r on r.tenant=e.tenant and r.id=e.returnId
  where r.tenant=:tenant and r.branch=:branch and r.id=:returned
  """)
 Page<ReturnAuditRow> history(long tenant,long branch,String returned,Pageable pageable);
}
