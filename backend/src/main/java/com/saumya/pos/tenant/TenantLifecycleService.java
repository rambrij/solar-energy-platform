package com.saumya.pos.tenant;

import com.saumya.pos.tenant.internal.TenantEntity;
import com.saumya.pos.tenant.internal.TenantRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public application interface of the tenant module. The platform module drives onboarding and lifecycle through
 * this service; nothing outside the module touches its repository or entity.
 */
@Service @Transactional
public class TenantLifecycleService {
    public record NewTenant(String slug,String name,String legalName,String contactEmail,String contactPhone,String address,String taxId) {}
    public record TenantProfile(String name,String legalName,String contactEmail,String contactPhone,String address,String taxId) {}
    public record TenantView(long id,String slug,String name,String legalName,String contactEmail,String contactPhone,String address,String taxId,TenantStatus status,String statusReason,Instant statusChangedAt,String statusChangedBy,Instant createdAt) {}
    public record Page(List<TenantView> items,int page,int size,long total) {}

    private static final Pattern SLUG=Pattern.compile("^[a-z0-9](?:[a-z0-9-]{1,78}[a-z0-9])$");
    private static final Set<String> RESERVED=Set.of("admin","api","platform","marketplace","vendors","login","static","assets","www");
    private static final Pattern EMAIL=Pattern.compile("^[^@\\s]{1,100}@[^@\\s]{1,100}\\.[^@\\s]{2,}$");
    private final TenantRepository tenants;private final Clock clock;
    public TenantLifecycleService(TenantRepository tenants,Clock clock){this.tenants=tenants;this.clock=clock;}

    private static TenantException invalid(String m){return new TenantException(TenantException.Kind.INVALID,m);}
    private static TenantException missing(){return new TenantException(TenantException.Kind.NOT_FOUND,"Tenant not found");}
    private static String clean(String v,int max,String field,boolean required){
        String t=v==null?"":v.strip();
        if(t.isEmpty()){if(required)throw invalid(field+" is required");return null;}
        if(t.length()>max)throw invalid(field+" must be at most "+max+" characters");
        if(t.chars().anyMatch(Character::isISOControl))throw invalid(field+" contains control characters");
        return t;
    }
    private static TenantView view(TenantEntity t){
        return new TenantView(t.id(),t.slug(),t.name(),t.legalName(),t.contactEmail(),t.contactPhone(),t.address(),t.taxId(),TenantStatus.valueOf(t.status()),t.statusReason(),t.statusChangedAt(),t.statusChangedBy(),t.createdAt());
    }
    private static String email(String v){String e=clean(v,200,"Contact email",true);if(!EMAIL.matcher(e).matches())throw invalid("Contact email is not valid");return e;}

    /** Creates a tenant in PENDING. The slug is unique and used only for common-domain public routes (TEN-007). */
    public TenantView create(NewTenant request){
        String slug=clean(request.slug(),80,"Slug",true);
        if(!SLUG.matcher(slug).matches()||RESERVED.contains(slug))throw invalid("Slug must be 3-80 lower-case letters, digits or hyphens and not reserved");
        if(tenants.findBySlug(slug).isPresent())throw new TenantException(TenantException.Kind.CONFLICT,"Slug is already in use");
        var entity=new TenantEntity(tenants.nextId(),slug,clean(request.name(),160,"Display name",true),clean(request.legalName(),200,"Legal name",true),email(request.contactEmail()),clean(request.contactPhone(),40,"Contact phone",false),clean(request.address(),400,"Address",false),clean(request.taxId(),40,"Tax identifier",false),TenantStatus.PENDING.name(),clock.instant());
        try{return view(tenants.saveAndFlush(entity));}
        catch(DataIntegrityViolationException e){throw new TenantException(TenantException.Kind.CONFLICT,"Tenant could not be created because the slug or identifier is already in use; retry");}
    }
    /** Moves a tenant along the lifecycle under a row lock; every change records reason, actor and time. */
    public TenantView transition(long id,TenantStatus target,String reason,String actor){
        var entity=tenants.lockById(id).orElseThrow(TenantLifecycleService::missing);
        TenantLifecycle.require(TenantStatus.valueOf(entity.status()),target,reason);
        entity.status(target.name(),reason.strip(),actor,clock.instant());
        return view(tenants.saveAndFlush(entity));
    }
    public TenantView updateProfile(long id,TenantProfile profile){
        var entity=tenants.lockById(id).orElseThrow(TenantLifecycleService::missing);
        if(TenantStatus.valueOf(entity.status())==TenantStatus.CLOSED)throw new TenantException(TenantException.Kind.CONFLICT,"A closed tenant cannot be edited");
        entity.profile(clean(profile.name(),160,"Display name",true),clean(profile.legalName(),200,"Legal name",true),email(profile.contactEmail()),clean(profile.contactPhone(),40,"Contact phone",false),clean(profile.address(),400,"Address",false),clean(profile.taxId(),40,"Tax identifier",false));
        return view(tenants.saveAndFlush(entity));
    }
    @Transactional(readOnly=true) public Optional<TenantView> find(long id){return tenants.findById(id).map(TenantLifecycleService::view);}
    @Transactional(readOnly=true) public Optional<TenantView> findBySlug(String slug){return slug==null?Optional.empty():tenants.findBySlug(slug).map(TenantLifecycleService::view);}
    @Transactional(readOnly=true) public Page list(TenantStatus status,String query,int page,int size){
        if(page<0||size<1||size>100)throw invalid("Page must be >= 0 and size 1-100");
        String q=(query==null?"":query.strip().toLowerCase()).replace("!","!!").replace("%","!%").replace("_","!_");
        var result=tenants.search(status==null?"":status.name(),"%"+q+"%",PageRequest.of(page,size));
        return new Page(result.getContent().stream().map(TenantLifecycleService::view).toList(),page,size,result.getTotalElements());
    }
    /** Aggregate counts only; no tenant-private data. */
    @Transactional(readOnly=true) public Map<TenantStatus,Long> countByStatus(){
        var counts=new EnumMap<TenantStatus,Long>(TenantStatus.class);for(var s:TenantStatus.values())counts.put(s,0L);
        for(Object[] row:tenants.countByStatus())counts.put(TenantStatus.valueOf((String)row[0]),((Number)row[1]).longValue());
        return counts;
    }
}
