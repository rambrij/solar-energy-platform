package com.saumya.pos.catalog;
import com.saumya.pos.catalog.internal.*;
import com.saumya.pos.shared.DomainProblem;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
@Service
public class ProductAdminService {
 private final ProductRepository products;private final CategoryRepository categories;private final ProductCategoryRepository assignments;private final CatalogSequenceRepository sequences;private final CatalogChangeEventRepository events;private final Clock clock;
 @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
 @org.springframework.beans.factory.annotation.Autowired private com.saumya.pos.security.AuthorizationContext authorization;
 private com.saumya.pos.security.AuthorizationContext.Context authorize(long tenant,String actor,String permission){
  if(authorization==null)throw new ResponseStatusException(UNAUTHORIZED,"Authorization context unavailable");
  var context=authorization.current();context.requireVendorMembership();context.requireTenant(tenant);
  if(actor!=null&&!context.username().equals(actor))throw new ResponseStatusException(FORBIDDEN,"Product actor must match authenticated identity");
  context.requirePermission(permission);return context;
 }
 public ProductAdminService(ProductRepository products,CategoryRepository categories,ProductCategoryRepository assignments,CatalogSequenceRepository sequences,CatalogChangeEventRepository events,Clock clock){this.products=products;this.categories=categories;this.assignments=assignments;this.sequences=sequences;this.events=events;this.clock=clock;}
 public record Input(@NotBlank @Size(max=80) String sku,@Size(max=80) String barcode,@NotBlank @Size(max=160) String name,@DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal price,@DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer=3,fraction=2) BigDecimal taxRate,boolean published,@Positive Long categoryId,@Min(0) long expectedVersion,@NotBlank @Size(max=250) String reason){}
 public record View(long id,String sku,String barcode,String name,BigDecimal price,BigDecimal taxRate,boolean published,Long categoryId,long version){}
 public record Page(List<View> items,int page,int size,long totalElements,int totalPages){}
 private void validate(Input q,boolean requirePrice){if(q==null||q.sku()==null||q.sku().isBlank()||q.sku().length()>80||q.barcode()!=null&&q.barcode().length()>80||q.name()==null||q.name().isBlank()||q.name().length()>160||requirePrice&&(q.price()==null||q.taxRate()==null)||q.price()!=null&&(q.price().signum()<0||q.price().scale()>2||q.price().precision()-q.price().scale()>12)||q.taxRate()!=null&&(q.taxRate().signum()<0||q.taxRate().compareTo(new BigDecimal("100"))>0||q.taxRate().scale()>2)||q.expectedVersion()<0||q.categoryId()!=null&&q.categoryId()<1||q.reason()==null||q.reason().isBlank()||q.reason().length()>250)throw new ResponseStatusException(BAD_REQUEST,"Valid product identifiers, price, tax, version and reason required");}
 private boolean hasAuthority(String permission){var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();return auth!=null&&auth.getAuthorities().stream().anyMatch(grant->permission.equals(grant.getAuthority()));}
 private String barcode(Input q){return q.barcode()==null||q.barcode().isBlank()?null:q.barcode().strip();}
 private void unique(long tenant,long id,Input q){if(products.existsByTenantIdAndSkuAndIdNot(tenant,q.sku().strip(),id)||barcode(q)!=null&&products.existsByTenantIdAndBarcodeAndIdNot(tenant,barcode(q),id))throw new DomainProblem("PRODUCT_IDENTIFIER_CONFLICT","SKU or barcode is already used by this vendor");}
 private void category(long tenant,Input q){if(q.categoryId()!=null){var c=categories.locked(tenant,q.categoryId()).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Category unavailable"));em.refresh(c,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);if(!c.active())throw new ResponseStatusException(CONFLICT,"Category is archived");}}
 private View snapshot(long tenant,Product p){var a=assignments.findById(new ProductCategory.Key(tenant,p.id()));return new View(p.id(),p.sku(),p.barcode(),p.name(),p.price(),p.taxRate(),p.published(),a.map(ProductCategory::category).orElse(null),p.version());}
 private View view(long tenant,Product p){var value=snapshot(tenant,p);return hasAuthority("PRICE_VIEW")?value:new View(value.id(),value.sku(),value.barcode(),value.name(),null,null,value.published(),value.categoryId(),value.version());}
 private void save(long tenant,String actor,Product p,Input q,String before){p.edit(q.sku().strip(),barcode(q),q.name().strip(),q.price(),q.taxRate(),q.published());em.flush();var key=new ProductCategory.Key(tenant,p.id());if(q.categoryId()==null)assignments.findById(key).ifPresent(assignments::delete);else assignments.saveAndFlush(new ProductCategory(tenant,p.id(),q.categoryId()));assignments.flush();events.saveAndFlush(new CatalogChangeEvent(tenant,actor,before.isEmpty()?"PRODUCT_CREATE":"PRODUCT_UPDATE",p.id(),q.reason().strip(),before,snapshot(tenant,p).toString(),clock.instant()));}
 @Transactional(readOnly=true) @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
 public Page list(long tenant,int page,int size){authorize(tenant,null,"PRODUCT_VIEW");if(page<0||page>10000||size<1||size>100)throw new ResponseStatusException(BAD_REQUEST,"Bounded product pagination required");var rows=products.findByTenantIdOrderByNameAscIdAsc(tenant,org.springframework.data.domain.PageRequest.of(page,size));return new Page(rows.stream().map(p->view(tenant,p)).toList(),page,size,rows.getTotalElements(),rows.getTotalPages());}
 @Transactional @PreAuthorize("hasAuthority('PRODUCT_CREATE')")
 public View create(long tenant,String actor,Input q){var context=authorize(tenant,actor,"PRODUCT_CREATE");validate(q,true);long id=sequences.locked("PRODUCT").orElseThrow().allocate();unique(tenant,id,q);category(tenant,q);var p=new Product(id,tenant);p.edit(q.sku().strip(),barcode(q),q.name().strip(),q.price(),q.taxRate(),q.published());em.persist(p);save(tenant,actor,p,q,"");return view(tenant,p);}
 @Transactional @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
 public View update(long tenant,String actor,long id,Input q){var context=authorize(tenant,actor,"PRODUCT_UPDATE");validate(q,false);var p=products.locked(tenant,id).orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Product unavailable"));em.refresh(p,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);if(p.version()!=q.expectedVersion())throw new ResponseStatusException(CONFLICT,"Product changed; reload before saving");var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();var grants=auth.getAuthorities().stream().map(org.springframework.security.core.GrantedAuthority::getAuthority).toList();if(p.published()&&!q.published())context.requirePermission("PRODUCT_ARCHIVE");boolean canChangePrice=context.hasPermission("PRICE_UPDATE");if(canChangePrice&&(q.price()==null||q.taxRate()==null))throw new ResponseStatusException(BAD_REQUEST,"Price and tax values are required when updating price data");if(!canChangePrice&&((q.price()!=null&&p.price().compareTo(q.price())!=0)||(q.taxRate()!=null&&p.taxRate().compareTo(q.taxRate())!=0)))throw new ResponseStatusException(FORBIDDEN,"Price update permission required");unique(tenant,id,q);category(tenant,q);String before=snapshot(tenant,p).toString();var saved=new Input(q.sku(),q.barcode(),q.name(),q.price()==null?p.price():q.price(),q.taxRate()==null?p.taxRate():q.taxRate(),q.published(),q.categoryId(),q.expectedVersion(),q.reason());em.lock(p,jakarta.persistence.LockModeType.PESSIMISTIC_FORCE_INCREMENT);save(tenant,actor,p,saved,before);return view(tenant,p);}
}
