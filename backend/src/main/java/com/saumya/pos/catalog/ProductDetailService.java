package com.saumya.pos.catalog;

import com.saumya.pos.shared.Pricing;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
@Transactional(readOnly=true)
public class ProductDetailService {
    private final CatalogService catalog;
    private final Pricing pricing;
    public ProductDetailService(CatalogService catalog,Pricing pricing){this.catalog=catalog;this.pricing=pricing;}
    public record Detail(long id,String vendorSlug,String vendorName,String sku,String name,BigDecimal unitPrice,
                         BigDecimal taxRate,BigDecimal unitTax,BigDecimal unitTotal,boolean inStock,BigDecimal listPrice,BigDecimal discountPercent,String categoryName){}
    public Detail detail(String slug,long id){
        if(id<1)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Catalogue resource unavailable");
        var vendor=catalog.activeVendor(slug);
        var product=catalog.published(vendor.id(),id);
        var amount=pricing.line(product.price(),product.taxRate(),1);
        return new Detail(product.id(),vendor.slug(),vendor.name(),product.sku(),product.name(),product.price(),product.taxRate(),amount.tax(),amount.total(),catalog.onlineInStock(vendor.id(),product.id()),product.listPrice(),product.discountPercent(),product.categoryName());
    }
}
