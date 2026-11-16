package com.saumya.pos.retail.internal;
import com.saumya.pos.retail.RegisterDirectoryService.Register;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
public interface RegisterDirectoryRepository extends org.springframework.data.jpa.repository.JpaRepository<RegisterRecord,Long> {
 @Query(value="select new com.saumya.pos.retail.RegisterDirectoryService$Register(r.id,case when s.id is null then true else false end) from RegisterRecord r join EmployeeRegisterAssignment a on a.tenant=r.tenant and a.branch=r.branch and a.register=r.id and a.username=:username and a.active=true left join ClosedShift s on s.tenant=r.tenant and s.branch=r.branch and s.register=r.id and s.state in ('OPEN','CLOSING') where r.tenant=:tenant and r.branch=:branch order by r.id",
 countQuery="select count(r) from RegisterRecord r join EmployeeRegisterAssignment a on a.tenant=r.tenant and a.branch=r.branch and a.register=r.id and a.username=:username and a.active=true where r.tenant=:tenant and r.branch=:branch")
 Page<Register> directory(long tenant,long branch,String username,Pageable page);
}
