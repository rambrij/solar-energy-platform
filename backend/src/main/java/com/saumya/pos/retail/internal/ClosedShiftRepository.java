package com.saumya.pos.retail.internal;
import com.saumya.pos.retail.ShiftHistoryService.Row;
import java.time.Instant;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
public interface ClosedShiftRepository extends Repository<ClosedShift,String> {
 @Query(value="select new com.saumya.pos.retail.ShiftHistoryService$Row(s.id,s.register,s.opening,s.expected,s.counted,s.variance,s.closed) from ClosedShift s where s.tenant=:tenant and s.branch=:branch and s.cashier=:cashier and s.state='CLOSED' and s.closed>=:start and s.closed<:until and lower(s.id) like :prefix order by s.closed desc,s.id desc",
 countQuery="select count(s) from ClosedShift s where s.tenant=:tenant and s.branch=:branch and s.cashier=:cashier and s.state='CLOSED' and s.closed>=:start and s.closed<:until and lower(s.id) like :prefix")
 Page<Row> history(long tenant,long branch,String cashier,Instant start,Instant until,String prefix,Pageable page);
}
