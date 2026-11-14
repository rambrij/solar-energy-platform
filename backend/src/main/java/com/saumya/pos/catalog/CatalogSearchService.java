package com.saumya.pos.catalog;
import com.saumya.pos.catalog.internal.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class CatalogSearchService {
 private final CatalogSearchRepository products;private final CatalogService catalogue;
 public CatalogSearchService(CatalogSearchRepository products,CatalogService catalogue){this.products=products;this.catalogue=catalogue;}
 public record Item(long id,String sku,String name,BigDecimal price,BigDecimal taxRate,boolean inStock,BigDecimal listPrice,BigDecimal discountPercent,Long categoryId,String categoryName){}
 public record Category(String id,String name,long count){}
 public record Result(List<Item> items,int page,int size,long totalElements,int totalPages,long catalogueTotal,List<Category> categories,String vendorSlug,String vendorName){}
 public Result search(String slug,String requestedQuery,String category,String ordering,boolean stockOnly,int page,int size){
  String query=requestedQuery==null?"":requestedQuery.strip();category=category==null?"all":category;ordering=ordering==null?"name":ordering;
  if(query.length()>120||page<0||page>10000||size<1||size>48||!Set.of("name","price-low","price-high").contains(ordering))throw invalid();
  long categoryId=0;boolean other=category.equals("other");
  if(!other&&!category.equals("all")){try{if(!category.matches("[1-9][0-9]{0,18}"))throw invalid();categoryId=Long.parseLong(category);}catch(NumberFormatException e){throw invalid();}}
  var vendor=catalogue.activeVendor(slug);long tenant=vendor.id();
  String pattern="%"+query.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
  var result=products.search(tenant,pattern,categoryId,other,stockOnly,ordering,PageRequest.of(page,size));
  long all=products.countByTenantIdAndPublishedTrue(tenant);var categories=new ArrayList<Category>();long categorized=0;
  for(var c:catalogue.categories(slug)){categories.add(new Category(Long.toString(c.id()),c.name(),c.products()));categorized+=c.products();}
  if(all>categorized)categories.add(new Category("other","Other",all-categorized));
  return new Result(result.stream().map(p->new Item(p.getId(),p.getSku(),p.getName(),p.getPrice(),p.getTaxRate(),p.getAvailable()>0,p.getListPrice(),p.getDiscountPercent(),p.getCategoryId(),p.getCategoryName())).toList(),page,size,result.getTotalElements(),result.getTotalPages(),all,List.copyOf(categories),vendor.slug(),vendor.name());
 }
 private ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a query up to 120 characters, a valid category/sort, page 0-10000 and size 1-48");}
}
