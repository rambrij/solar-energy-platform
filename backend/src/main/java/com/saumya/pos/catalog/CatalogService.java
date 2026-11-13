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
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    private final VendorRepository vendors; private final ProductRepository products; private final InventoryService stock;
    private final CategoryRepository categories; private final PromotionService promotions;
    @org.springframework.beans.factory.annotation.Autowired
    public CatalogService(VendorRepository vendors,ProductRepository products,InventoryService stock,CategoryRepository categories,PromotionService promotions){this.vendors=vendors;this.products=products;this.stock=stock;this.categories=categories;this.promotions=promotions;}
    public CatalogService(VendorRepository vendors,ProductRepository products,InventoryService stock){this(vendors,products,stock,null,null);}
    public record VendorInfo(long id,String slug,String name,boolean active) {}
    public record CategoryInfo(long id,String name,String slug,long products){}
    public record ProductInfo(long id,String sku,String barcode,String name,BigDecimal price,BigDecimal taxRate,boolean published,boolean inStock,BigDecimal listPrice,BigDecimal discountPercent,Long categoryId,String categoryName) {
        public ProductInfo(long id,String sku,String barcode,String name,BigDecimal price,BigDecimal taxRate,boolean published,boolean inStock){this(id,sku,barcode,name,price,taxRate,published,inStock,price,BigDecimal.ZERO,null,"Other");}
    }
    private VendorInfo info(Vendor v){return new VendorInfo(v.id(),v.slug(),v.name(),v.active());}
    private ProductInfo info(long tenant,Product p){return info(tenant,p,false);}
    private ProductInfo info(long tenant,Product p,boolean locked){
        var quote=promotions.price(tenant,p.id(),p.price(),locked);var category=categories.forProduct(tenant,p.id());
        return new ProductInfo(p.id(),p.sku(),p.barcode(),p.name(),quote.price(),p.taxRate(),p.published(),stock.available(tenant,p.id())>0,p.price(),quote.percent(),category.map(CategoryRow::id).orElse(null),category.map(CategoryRow::name).orElse("Other"));
    }
    public List<CategoryInfo> categories(String slug){return categories.published(activeVendor(slug).id()).stream().map(c->new CategoryInfo(c.id(),c.name(),c.slug(),c.products())).toList();}
    private ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Catalogue resource unavailable");}
    public List<VendorInfo> vendors(){return vendors.findByActiveTrueAndSlugIsNotNullOrderByNameAscIdAsc().stream().map(this::info).toList();}
    public VendorInfo activeVendor(String slug){return info(vendors.findBySlugAndActiveTrue(slug).orElseThrow(this::missing));}
    public VendorInfo vendor(long tenant){return info(vendors.findById(tenant).orElseThrow(this::missing));}
    public long onlineBranch(long tenant){var v=vendors.findById(tenant).orElseThrow(this::missing);if(!v.active()||v.onlineBranchId()==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Vendor online stock location is not configured");return v.onlineBranchId();}
    public boolean onlineInStock(long tenant,long product){var vendor=vendors.findById(tenant).orElseThrow(this::missing);return vendor.active()&&vendor.onlineBranchId()!=null&&stock.atBranch(tenant,vendor.onlineBranchId(),product)>0;}
    public List<ProductInfo> products(String slug){long tenant=activeVendor(slug).id();return products.findByTenantIdAndPublishedTrueOrderById(tenant).stream().map(p->info(tenant,p)).toList();}
    public ProductInfo product(long tenant,long id){return info(tenant,products.findByTenantIdAndId(tenant,id).orElseThrow(this::missing));}
    public ProductInfo published(long tenant,long id){var p=product(tenant,id);if(!p.published())throw missing();return p;}
    @Transactional public ProductInfo lockForSale(long tenant,long id){var p=products.lockPublished(tenant,id).orElseThrow(this::missing);entityManager.refresh(p,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);if(!p.published())throw missing();return info(tenant,p,true);}
    public List<Map<String,Object>> posProducts(long tenant,long branch){return products.findByTenantIdAndPublishedTrueOrderById(tenant).stream().map(p->{Map<String,Object> row=new LinkedHashMap<>();row.put("id",p.id());row.put("sku",p.sku());row.put("barcode",p.barcode());row.put("name",p.name());var quoted=info(tenant,p);row.put("price",quoted.price());row.put("list_price",quoted.listPrice());row.put("discount_percent",quoted.discountPercent());row.put("category_id",quoted.categoryId());row.put("category_name",quoted.categoryName());row.put("tax_rate",p.taxRate());row.put("quantity",stock.atBranch(tenant,branch,p.id()));return row;}).toList();}
}
