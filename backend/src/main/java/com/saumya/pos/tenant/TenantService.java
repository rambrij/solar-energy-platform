package com.saumya.pos.tenant;

import com.saumya.pos.tenant.internal.*;
import com.saumya.pos.security.AuthorizationContext;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Clock;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service @Transactional
public class TenantService implements TenantLifecycleService {
    private static final Pattern SLUG=Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private final TenantRepository tenants;private final TenantSequenceRepository sequences;private final Clock clock;private final AuthorizationContext authorization;
    public TenantService(TenantRepository tenants,TenantSequenceRepository sequences,Clock clock){this(tenants,sequences,clock,null);}
    @Autowired public TenantService(TenantRepository tenants,TenantSequenceRepository sequences,Clock clock,AuthorizationContext authorization){this.tenants=tenants;this.sequences=sequences;this.clock=clock;this.authorization=authorization;}
    private void requirePlatform(String permission){if(authorization==null)throw new ResponseStatusException(UNAUTHORIZED,"Platform authorization context unavailable");var actor=authorization.current();if(!actor.isPlatformUser())throw new ResponseStatusException(FORBIDDEN,"Platform identity required");actor.requirePermission(permission);}
    @Override public TenantSummary create(Registration r){requirePlatform("PLATFORM_TENANT_CREATE");String slug=slug(r.slug());if(tenants.findBySlug(slug).isPresent())throw new ResponseStatusException(CONFLICT,"Store slug already exists");String name=required(r.name(),160,"Display name"),legal=required(r.legalName(),180,"Legal name"),admin=email(r.initialVendorAdminUsername());long id=sequences.locked().orElseThrow(()->new IllegalStateException("Tenant sequence unavailable")).allocate();var now=clock.instant();return view(tenants.saveAndFlush(new Tenant(id,slug,name,legal,emailOptional(r.contactEmail()),optional(r.contactPhone(),30),optional(r.taxIdentifier(),40),optional(r.address(),500),admin,null,now)));}
    @Override public TenantSummary updateProfile(long id,ProfileUpdate r){requirePlatform("PLATFORM_TENANT_UPDATE");var t=requiredTenant(id);try{t.updateProfile(required(r.name(),160,"Display name"),required(r.legalName(),180,"Legal name"),emailOptional(r.contactEmail()),optional(r.contactPhone(),30),optional(r.taxIdentifier(),40),optional(r.address(),500),clock.instant());}catch(IllegalStateException e){throw new ResponseStatusException(CONFLICT,e.getMessage());}return view(t);}
    @Override public TenantSummary transition(long id,TenantStatus target){requirePlatform(switch(target){case SUSPENDED->"PLATFORM_TENANT_SUSPEND";case CLOSED->"PLATFORM_TENANT_CLOSE";case ACTIVE->"PLATFORM_TENANT_REACTIVATE";default->"PLATFORM_TENANT_UPDATE";});var t=requiredTenant(id);try{t.transitionTo(target,clock.instant());}catch(IllegalStateException e){throw new ResponseStatusException(CONFLICT,e.getMessage());}return view(t);}
    @Override public TenantSummary assignOnlineBranch(long id,long branch){requirePlatform("PLATFORM_TENANT_UPDATE");var t=requiredTenant(id);t.assignOnlineBranch(branch,clock.instant());return view(t);}
    @Override @Transactional(readOnly=true) public TenantSummary get(long id){return view(requiredTenant(id));}
    @Override @Transactional(readOnly=true) public Optional<TenantSummary> findBySlug(String s){return tenants.findBySlug(slug(s)).map(TenantService::view);}
    @Override @Transactional(readOnly=true) public TenantSummary activeVendor(String s){return view(tenants.findBySlugAndLifecycleStatus(slug(s),TenantStatus.ACTIVE).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Vendor unavailable")));}
    @Override @Transactional(readOnly=true) public List<TenantSummary> activeVendors(){return tenants.findByLifecycleStatusAndSlugIsNotNullOrderByNameAscIdAsc(TenantStatus.ACTIVE).stream().filter(t->!t.slug().isBlank()).map(TenantService::view).toList();}
    @Override @Transactional(readOnly=true) public boolean isActive(long id){return tenants.findById(id).map(Tenant::active).orElse(false);}
    @Override @Transactional(readOnly=true) public long onlineBranch(long id){var t=requiredTenant(id);if(!t.active()||t.onlineBranchId()==null)throw new ResponseStatusException(CONFLICT,"Vendor online stock location is not configured");return t.onlineBranchId();}
    @Override @Transactional(readOnly=true) public List<StatusCount> statusCounts(){return Arrays.stream(TenantStatus.values()).map(s->new StatusCount(s.name(),tenants.countByLifecycleStatus(s))).toList();}
    @Override @Transactional(readOnly=true) public DirectoryPage search(String q,TenantStatus status,int page,int size){checkPage(page,size,100);String term=safeQuery(q);var p=tenants.search(term,status,PageRequest.of(page,size));return new DirectoryPage(q==null?"":q.strip(),page,size,p.getTotalElements(),p.getTotalPages(),p.getContent().stream().map(TenantService::view).toList());}
    @Override @Transactional(readOnly=true) public VendorDirectoryPage searchActiveVendors(String q,int page,int size){checkPage(page,size,48);String term=safeQuery(q);var p=tenants.searchActiveVendors(term,TenantStatus.ACTIVE,PageRequest.of(page,size));return new VendorDirectoryPage(q==null?"":q.strip(),page,size,p.getTotalElements(),p.getTotalPages(),p.getContent().stream().map(t->new VendorCard(t.id(),t.slug(),t.name())).toList());}
    private Tenant requiredTenant(long id){return tenants.findById(id).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Tenant unavailable"));}
    private static TenantSummary view(Tenant t){return new TenantSummary(t.id(),t.slug(),t.name(),t.legalName(),t.contactEmail(),t.contactPhone(),t.taxIdentifier(),t.address(),t.lifecycleStatus().name(),t.onlineBranchId(),t.initialVendorAdminUsername(),t.createdAt(),t.updatedAt(),t.version());}
    private static String slug(String v){String s=v==null?"":v.strip().toLowerCase(Locale.ROOT);if(s.length()<2||s.length()>100||!SLUG.matcher(s).matches())throw new ResponseStatusException(BAD_REQUEST,"Store slug must use lowercase letters, numbers and single hyphens");return s;}
    private static String email(String v){String s=v==null?"":v.strip().toLowerCase(Locale.ROOT);if(s.length()>100||!s.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw new ResponseStatusException(BAD_REQUEST,"Valid administrator email required");return s;}
    private static String emailOptional(String v){return v==null||v.isBlank()?null:email(v);}
    private static String required(String v,int max,String l){if(v==null||v.isBlank()||v.strip().length()>max)throw new ResponseStatusException(BAD_REQUEST,l+" is required and must be at most "+max+" characters");return v.strip();}
    private static String optional(String v,int max){if(v==null||v.isBlank())return null;if(v.strip().length()>max)throw new ResponseStatusException(BAD_REQUEST,"Optional tenant field is too long");return v.strip();}
    private static String safeQuery(String v){String s=v==null?"":v.strip();if(s.length()>120||s.codePoints().anyMatch(Character::isISOControl))throw new ResponseStatusException(BAD_REQUEST,"Search must be at most 120 printable characters");return s.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_");}
    private static void checkPage(int p,int s,int max){if(p<0||p>10000||s<1||s>max)throw new ResponseStatusException(BAD_REQUEST,"Pagination outside supported bounds");}
}
