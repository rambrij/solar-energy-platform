package com.saumya.pos;
import com.saumya.pos.catalog.*;
import com.saumya.pos.shared.Pricing;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
class ProductDetailServiceTest {
    @Test void detailUsesActiveVendorPublicationAndSharedPerLineRounding(){
        var catalog=new CatalogService(null,null,null){
            @Override public VendorInfo activeVendor(String slug){assertThat(slug).isEqualTo("solar");return new VendorInfo(7,"solar","Solar vendor",true);}
            @Override public ProductInfo published(long tenant,long id){assertThat(tenant).isEqualTo(7);assertThat(id).isEqualTo(9);return new ProductInfo(9,"SKU","private-barcode","Panel",new BigDecimal("0.05"),BigDecimal.TEN,true,true);}
        };
        var detail=new ProductDetailService(catalog,new Pricing()).detail("solar",9);
        assertThat(detail.unitTax()).isEqualByComparingTo("0.01");assertThat(detail.unitTotal()).isEqualByComparingTo("0.06");assertThat(detail.vendorName()).isEqualTo("Solar vendor");assertThat(detail.inStock()).isTrue();
    }
    @Test void invalidReferenceStopsBeforeCatalogueLookup(){
        var catalog=new CatalogService(null,null,null){@Override public VendorInfo activeVendor(String slug){throw new AssertionError("Must not look up an invalid ID");}};
        assertThatThrownBy(()->new ProductDetailService(catalog,new Pricing()).detail("solar",0)).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(404));
    }
    @Test void inactiveVendorCannotReachProductLookup(){
        var catalog=new CatalogService(null,null,null){
            @Override public VendorInfo activeVendor(String slug){throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);}
            @Override public ProductInfo published(long tenant,long id){throw new AssertionError("Product must stay private");}
        };
        assertThatThrownBy(()->new ProductDetailService(catalog,new Pricing()).detail("closed",9)).isInstanceOf(ResponseStatusException.class);
    }
}
