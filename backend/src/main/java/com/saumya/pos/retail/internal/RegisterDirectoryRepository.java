package com.saumya.pos.retail.internal;
import com.saumya.pos.retail.RegisterDirectoryService.Register;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
public interface RegisterDirectoryRepository extends Repository<RegisterRecord,Long> {
 @Query(value="select new com.saumya.pos.retail.RegisterDirectoryService$Register(r.id,case when s.id is null then true else false end) from RegisterRecord r left join ClosedShift s on s.tenant=r.tenant and s.branch=r.branch and s.register=r.id and s.state in ('OPEN','CLOSING') where r.tenant=:tenant and r.branch=:branch order by r.id",
 countQuery="select count(r) from RegisterRecord r where r.tenant=:tenant and r.branch=:branch")
 Page<Register> directory(long tenant,long branch,Pageable page);
}
