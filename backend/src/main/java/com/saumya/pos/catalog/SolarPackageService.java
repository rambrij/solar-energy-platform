package com.saumya.pos.catalog;

import com.saumya.pos.catalog.internal.*;
import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.tenant.TenantLifecycleService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
public class SolarPackageService {
    private final SolarPackageRepository packages;
    private final SolarPackageItemRepository items;
    private final ProductRepository products;
    private final CatalogSequenceRepository sequences;
    private final CatalogChangeEventRepository events;
    private final AuthorizationContext authorization;
    private final CatalogService catalog;
    private final Clock clock;

    public SolarPackageService(SolarPackageRepository packages,SolarPackageItemRepository items,
            ProductRepository products,CatalogSequenceRepository sequences,CatalogChangeEventRepository events,
            AuthorizationContext authorization,CatalogService catalog,Clock clock) {
        this.packages=packages;this.items=items;this.products=products;this.sequences=sequences;
        this.events=events;this.authorization=authorization;this.catalog=catalog;this.clock=clock;
    }

    public record ItemInput(long productId,int quantity) {}
    public record Input(String sku,String name,String description,BigDecimal systemCapacityKw,
            BigDecimal packagePrice,BigDecimal taxRate,boolean published,long expectedVersion,
            String reason,List<ItemInput> items) {}
    public record ItemView(long productId,String sku,String name,int quantity,BigDecimal unitPrice,
            BigDecimal taxRate) {}
    public record View(long id,String sku,String name,String description,BigDecimal systemCapacityKw,
            BigDecimal packagePrice,BigDecimal taxRate,boolean published,long version,List<ItemView> items) {}
    public record Page(List<View> items,int page,int size,long totalElements,int totalPages) {}

    private AuthorizationContext.Context actor(String permission) {
        var context=authorization.current();
        context.requireVendorMembership();
        context.requirePermission(permission);
        return context;
    }

    private void validate(Input q) {
        if(q==null||q.sku()==null||q.sku().isBlank()||q.sku().strip().length()>80||
           q.name()==null||q.name().isBlank()||q.name().strip().length()>160||
           q.description()!=null&&q.description().length()>2000||
           q.systemCapacityKw()==null||q.systemCapacityKw().signum()<=0||
           q.systemCapacityKw().scale()>3||q.systemCapacityKw().precision()-q.systemCapacityKw().scale()>5||
           q.packagePrice()==null||q.packagePrice().signum()<0||q.packagePrice().scale()>2||
           q.packagePrice().precision()-q.packagePrice().scale()>17||
           q.taxRate()==null||q.taxRate().signum()<0||q.taxRate().compareTo(new BigDecimal("100"))>0||
           q.taxRate().scale()>2||q.expectedVersion()<0||q.reason()==null||q.reason().isBlank()||
           q.reason().strip().length()>250||q.items()==null||q.items().isEmpty()||q.items().size()>40)
            throw new ResponseStatusException(BAD_REQUEST,"Valid solar package details, price, tax, version, reason and 1-40 component products are required");
        Set<Long> ids=new HashSet<>();
        for(ItemInput item:q.items()) {
            if(item==null||item.productId()<1||item.quantity()<1||item.quantity()>10000||!ids.add(item.productId()))
                throw new ResponseStatusException(BAD_REQUEST,"Package component products must be unique with quantities from 1 to 10000");
        }
    }

    private boolean hasPriceView() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        return auth!=null&&auth.getAuthorities().stream().anyMatch(a->"PRICE_VIEW".equals(a.getAuthority()));
    }
    private boolean hasPriceUpdate() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        return auth!=null&&auth.getAuthorities().stream().anyMatch(a->"PRICE_UPDATE".equals(a.getAuthority()));
    }

    private List<SolarPackageItem> snapshotItems(long tenant,long packageId,Input q) {
        List<SolarPackageItem> result=new ArrayList<>();
        for(ItemInput line:q.items()) {
            var product=products.locked(tenant,line.productId()).orElseThrow(
                ()->new ResponseStatusException(NOT_FOUND,"A package component product is unavailable in this vendor catalogue"));
            if(q.published()&&!product.published())
                throw new ResponseStatusException(CONFLICT,"A published solar package can contain only published products");
            result.add(new SolarPackageItem(tenant,packageId,line.productId(),line.quantity(),
                product.sku(),product.name(),product.price(),product.taxRate()));
        }
        return result;
    }

    private View view(SolarPackage p,List<SolarPackageItem> lines,boolean exposePrice) {
        return new View(p.id(),p.sku(),p.name(),p.description(),p.systemCapacityKw(),
            exposePrice?p.packagePrice():null,exposePrice?p.taxRate():null,p.published(),p.version(),
            lines.stream().map(i->new ItemView(i.id().productId(),i.productSkuSnapshot(),
                i.productNameSnapshot(),i.quantity(),exposePrice?i.unitPriceSnapshot():null,
                exposePrice?i.taxRateSnapshot():null)).toList());
    }
    private View vendorView(long tenant,SolarPackage p) {
        return view(p,items.findByIdTenantIdAndIdPackageIdOrderByIdProductId(tenant,p.id()),hasPriceView());
    }
    private View publicView(long tenant,SolarPackage p) {
        return view(p,items.findByIdTenantIdAndIdPackageIdOrderByIdProductId(tenant,p.id()),true);
    }
    private String snapshot(SolarPackage p,List<SolarPackageItem> lines) {
        return view(p,lines,true).toString();
    }

    @Transactional(readOnly=true)
    public Page list(int page,int size) {
        var context=actor("PRODUCT_VIEW");
        if(page<0||page>10000||size<1||size>100)
            throw new ResponseStatusException(BAD_REQUEST,"Bounded solar package pagination required");
        var result=packages.findByTenantIdOrderByNameAscIdAsc(context.tenantId(),PageRequest.of(page,size));
        return new Page(result.getContent().stream().map(p->vendorView(context.tenantId(),p)).toList(),page,size,
            result.getTotalElements(),result.getTotalPages());
    }

    @Transactional(readOnly=true)
    public List<View> publicList(String vendorSlug) {
        var vendor=catalog.activeVendor(vendorSlug);
        return packages.findByTenantIdAndPublishedTrueOrderByNameAsc(vendor.id()).stream()
            .map(p->publicView(vendor.id(),p)).toList();
    }

    @Transactional
    public View create(Input request) {
        var context=actor("PRODUCT_CREATE");
        validate(request);
        if(!hasPriceUpdate())throw new ResponseStatusException(FORBIDDEN,"PRICE_UPDATE permission is required to create a priced solar package");
        long id=sequences.locked("SOLAR_PACKAGE").orElseThrow(
            ()->new IllegalStateException("Solar package sequence is not initialized")).allocate();
        String sku=request.sku().strip();
        if(packages.existsByTenantIdAndSkuAndIdNot(context.tenantId(),sku,id))
            throw new com.saumya.pos.shared.DomainProblem("SOLAR_PACKAGE_SKU_CONFLICT","Solar package SKU is already used by this vendor");
        var lines=snapshotItems(context.tenantId(),id,request);
        var now=clock.instant();
        var entity=new SolarPackage(id,context.tenantId(),sku,request.name().strip(),
            normalize(request.description()),request.systemCapacityKw(),request.packagePrice(),
            request.taxRate(),request.published(),now);
        packages.saveAndFlush(entity);
        items.saveAll(lines);items.flush();
        var after=snapshot(entity,lines);
        events.saveAndFlush(new CatalogChangeEvent(context.tenantId(),context.username(),
            "SOLAR_PACKAGE_CREATE",id,request.reason().strip(),"",after,now));
        return vendorView(context.tenantId(),entity);
    }

    @Transactional
    public View update(long id,Input request) {
        var context=actor("PRODUCT_UPDATE");
        validate(request);
        var entity=packages.locked(context.tenantId(),id).orElseThrow(
            ()->new ResponseStatusException(NOT_FOUND,"Solar package unavailable"));
        if(entity.version()!=request.expectedVersion())
            throw new ResponseStatusException(CONFLICT,"Solar package changed; reload before saving");
        if(!hasPriceUpdate()&&(!entity.packagePrice().equals(request.packagePrice())||
                !entity.taxRate().equals(request.taxRate())))
            throw new ResponseStatusException(FORBIDDEN,"PRICE_UPDATE permission is required to change package pricing");
        String sku=request.sku().strip();
        if(packages.existsByTenantIdAndSkuAndIdNot(context.tenantId(),sku,id))
            throw new com.saumya.pos.shared.DomainProblem("SOLAR_PACKAGE_SKU_CONFLICT","Solar package SKU is already used by this vendor");
        var oldLines=items.findByIdTenantIdAndIdPackageIdOrderByIdProductId(context.tenantId(),id);
        String before=snapshot(entity,oldLines);
        var newLines=snapshotItems(context.tenantId(),id,request);
        entity.edit(sku,request.name().strip(),normalize(request.description()),request.systemCapacityKw(),
            request.packagePrice(),request.taxRate(),request.published(),clock.instant());
        packages.saveAndFlush(entity);
        items.deleteForPackage(context.tenantId(),id);
        items.flush();
        items.saveAll(newLines);items.flush();
        events.saveAndFlush(new CatalogChangeEvent(context.tenantId(),context.username(),
            "SOLAR_PACKAGE_UPDATE",id,request.reason().strip(),before,snapshot(entity,newLines),clock.instant()));
        return vendorView(context.tenantId(),entity);
    }

    private static String normalize(String value) {
        if(value==null||value.isBlank())return null;
        return value.strip();
    }
}
