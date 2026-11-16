package com.saumya.pos.catalog;
import com.saumya.pos.catalog.internal.*;
import java.time.Clock;
import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
@Service
public class CategoryAdminService {
 private final CategoryRepository categories;private final ProductCategoryRepository assignments;private final CatalogSequenceRepository sequence;private final CatalogChangeEventRepository events;private final Clock clock;
 @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
 @org.springframework.beans.factory.annotation.Autowired private com.saumya.pos.security.AuthorizationContext authorization;
 private com.saumya.pos.security.AuthorizationContext.Context authorize(long tenant,String actor,String permission){
  if(authorization==null)throw new ResponseStatusException(UNAUTHORIZED,"Authorization context unavailable");
  var context=authorization.current();context.requireVendorMembership();context.requireTenant(tenant);
  if(actor!=null&&!context.username().equals(actor))throw new ResponseStatusException(FORBIDDEN,"Category actor must match authenticated identity");
  context.requirePermission(permission);return context;
 }
 public CategoryAdminService(CategoryRepository categories,ProductCategoryRepository assignments,CatalogSequenceRepository sequence,CatalogChangeEventRepository events,Clock clock){this.categories=categories;this.assignments=assignments;this.sequence=sequence;this.events=events;this.clock=clock;}
 public record Input(@NotBlank @Size(max=120) String name,@NotBlank @Size(max=100) @Pattern(regexp="[a-z0-9]+(?:-[a-z0-9]+)*") String slug,@Min(0) long expectedVersion,boolean active,@NotBlank @Size(max=250) String reason){}
 public record View(long id,String name,String slug,boolean active,long version){}
 public record Page(List<View> items,int page,int size,long totalElements,int totalPages){}
 private View view(Category category){return new View(category.id(),category.name(),category.slug(),category.active(),category.version());}
 private void validate(Input request){if(request==null||request.name()==null||request.name().isBlank()||request.name().length()>120||request.slug()==null||request.slug().length()>100||!request.slug().matches("[a-z0-9]+(?:-[a-z0-9]+)*")||request.reason()==null||request.reason().isBlank()||request.reason().length()>250||request.expectedVersion()<0)throw new ResponseStatusException(BAD_REQUEST,"Valid category name, slug, version and reason required");}
 @Transactional(readOnly=true) @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
 public Page list(long tenant,int page,int size){authorize(tenant,null,"PRODUCT_VIEW");if(page<0||page>10000||size<1||size>100)throw new ResponseStatusException(BAD_REQUEST,"Bounded category pagination required");var result=categories.findByTenantIdOrderByNameAscIdAsc(tenant,org.springframework.data.domain.PageRequest.of(page,size));return new Page(result.map(this::view).toList(),page,size,result.getTotalElements(),result.getTotalPages());}
 @Transactional @PreAuthorize("hasAuthority('PRODUCT_CREATE')")
 public View create(long tenant,String actor,Input request){authorize(tenant,actor,"PRODUCT_CREATE");validate(request);long id=sequence.locked("CATEGORY").orElseThrow().allocate();if(categories.existsByTenantIdAndSlugAndIdNot(tenant,request.slug(),id))throw new com.saumya.pos.shared.DomainProblem("CATEGORY_SLUG_CONFLICT","Category slug is already used by this vendor");var category=new Category(id,tenant,request.name().strip(),request.slug());category.edit(request.name().strip(),request.slug(),request.active());em.persist(category);em.flush();events.saveAndFlush(new CatalogChangeEvent(tenant,actor,"CATEGORY_CREATE",id,request.reason().strip(),"",view(category).toString(),clock.instant()));return view(category);}
 @Transactional @PreAuthorize("hasAuthority('PRODUCT_UPDATE') and (#request.active() or hasAuthority('PRODUCT_ARCHIVE'))")
 public View update(long tenant,String actor,long id,Input request){var context=authorize(tenant,actor,"PRODUCT_UPDATE");validate(request);if(!request.active())context.requirePermission("PRODUCT_ARCHIVE");var category=categories.locked(tenant,id).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Category unavailable"));em.refresh(category,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);if(category.version()!=request.expectedVersion())throw new ResponseStatusException(CONFLICT,"Category changed; reload before saving");if(categories.existsByTenantIdAndSlugAndIdNot(tenant,request.slug(),id))throw new com.saumya.pos.shared.DomainProblem("CATEGORY_SLUG_CONFLICT","Category slug is already used by this vendor");String before=view(category).toString();category.edit(request.name().strip(),request.slug(),request.active());if(!request.active())assignments.unassign(tenant,id);categories.flush();events.saveAndFlush(new CatalogChangeEvent(tenant,actor,"CATEGORY_UPDATE",id,request.reason().strip(),before,view(category).toString(),clock.instant()));return view(category);}
}
