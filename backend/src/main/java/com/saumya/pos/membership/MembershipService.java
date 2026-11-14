package com.saumya.pos.membership;
import com.saumya.pos.membership.internal.*;
import com.saumya.pos.catalog.CatalogService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service @Transactional(readOnly=true)
public class MembershipService {
 private final EmployeeMembershipRepository memberships;private final PermissionGrantRepository grants;private final CatalogService catalog;
 public MembershipService(EmployeeMembershipRepository memberships,PermissionGrantRepository grants,CatalogService catalog){this.memberships=memberships;this.grants=grants;this.catalog=catalog;}
 public record Access(long tenant,long branch,String username,List<String> permissions){}
 public Optional<Access> access(String username){return memberships.findById(username).filter(EmployeeMembership::enabled).filter(m->catalog.vendor(m.tenant()).active()).map(m->new Access(m.tenant(),m.branch(),username,grants.findByIdUsernameAndTenant(username,m.tenant()).stream().map(PermissionGrant::permission).filter(p->!p.startsWith("PLATFORM_")).toList()));}
 public Access require(String username){return access(username).orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"Active vendor membership required"));}
}
