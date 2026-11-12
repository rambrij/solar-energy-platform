package com.saumya.pos.catalog;
import com.saumya.pos.catalog.internal.*;
import com.saumya.pos.inventory.InventoryService;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service @Transactional(readOnly=true)
public class CatalogService {
    private final VendorRepository vendors; private final ProductRepository products; private final InventoryService stock;
    public CatalogService(VendorRepository vendors,ProductRepository products,InventoryService stock){this.vendors=vendors;this.products=products;this.stock=stock;}
    public record VendorInfo(long id,String slug,String name,boolean active) {}
    public record ProductInfo(long id,String sku,String barcode,String name,BigDecimal price,BigDecimal taxRate,boolean published,boolean inStock) {}
    private VendorInfo info(Vendor v){return new VendorInfo(v.id(),v.slug(),v.name(),v.active());}
    private ProductInfo info(long tenant,Product p){return new ProductInfo(p.id(),p.sku(),p.barcode(),p.name(),p.price(),p.taxRate(),p.published(),stock.available(tenant,p.id())>0);}
    private ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Catalogue resource unavailable");}
    public List<VendorInfo> vendors(){return vendors.findByActiveTrueAndSlugIsNotNullOrderByNameAscIdAsc().stream().map(this::info).toList();}
    public VendorInfo activeVendor(String slug){return info(vendors.findBySlugAndActiveTrue(slug).orElseThrow(this::missing));}
    public VendorInfo vendor(long tenant){return info(vendors.findById(tenant).orElseThrow(this::missing));}
    public List<ProductInfo> products(String slug){long tenant=activeVendor(slug).id();return products.findByTenantIdAndPublishedTrueOrderById(tenant).stream().map(p->info(tenant,p)).toList();}
    public ProductInfo product(long tenant,long id){return info(tenant,products.findByTenantIdAndId(tenant,id).orElseThrow(this::missing));}
    public ProductInfo published(long tenant,long id){var p=product(tenant,id);if(!p.published())throw missing();return p;}
    @Transactional public ProductInfo lockForSale(long tenant,long id){return info(tenant,products.lockPublished(tenant,id).orElseThrow(this::missing));}
    public List<Map<String,Object>> posProducts(long tenant,long branch){return products.findByTenantIdAndPublishedTrueOrderById(tenant).stream().map(p->{Map<String,Object> row=new LinkedHashMap<>();row.put("id",p.id());row.put("sku",p.sku());row.put("barcode",p.barcode());row.put("name",p.name());row.put("price",p.price());row.put("tax_rate",p.taxRate());row.put("quantity",stock.atBranch(tenant,branch,p.id()));return row;}).toList();}
}
