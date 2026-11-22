package com.saumya.pos.catalog;

import com.saumya.pos.tenant.TenantLifecycleService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true)
public class VendorDirectoryService {
    private final TenantLifecycleService tenants;
    public VendorDirectoryService(TenantLifecycleService tenants){this.tenants=tenants;}
    public record VendorCard(long id,String slug,String name){}
    public record Directory(String query,int page,int size,long totalElements,int totalPages,List<VendorCard> items){}
    public Directory search(String requested,int page,int size){
        var result=tenants.searchActiveVendors(requested,page,size);
        return new Directory(result.query(),result.page(),result.size(),result.totalElements(),result.totalPages(),
            result.items().stream().map(v->new VendorCard(v.id(),v.slug(),v.name())).toList());
    }
}
