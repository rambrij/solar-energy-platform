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
 public Optional<Access> access(String username){return memberships.findByUsernameIgnoreCase(username).filter(EmployeeMembership::enabled).filter(m->catalog.vendor(m.tenant()).active()).map(m->new Access(m.tenant(),m.branch(),m.username(),grants.findByIdUsernameAndTenant(m.username(),m.tenant()).stream().map(PermissionGrant::permission).filter(p->!p.startsWith("PLATFORM_")).toList()));}
 public record Membership(String username,long tenant,long branch,boolean enabled){}
 public Optional<Membership> membershipOf(String username){return memberships.findByUsernameIgnoreCase(username).map(m->new Membership(m.username(),m.tenant(),m.branch(),m.enabled()));}
 /** Creates a vendor membership with tenant-owned grants. Platform permissions can never be granted to a membership. */
 @Transactional public Membership provisionAdmin(long tenant,long branch,String username,Collection<String> permissions){
  if(permissions.stream().anyMatch(p->p.startsWith("PLATFORM_")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vendor memberships cannot hold platform permissions");
  var saved=memberships.saveAndFlush(new EmployeeMembership(username,tenant,branch,true));
  grants.saveAll(permissions.stream().distinct().map(p->new PermissionGrant(tenant,username,p)).toList());grants.flush();
  return new Membership(saved.username(),saved.tenant(),saved.branch(),saved.enabled());
 }
 public boolean hasMembership(String username){return memberships.findByUsernameIgnoreCase(username).isPresent();}
 public Access require(String username){return access(username).orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"Active vendor membership required"));}
}
