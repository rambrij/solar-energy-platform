package com.saumya.pos.catalog;
import com.saumya.pos.catalog.internal.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class PosCatalogService {
 private final PosCatalogRepository products;private final CatalogService catalogue;
 public PosCatalogService(PosCatalogRepository products,CatalogService catalogue){this.products=products;this.catalogue=catalogue;}
 public record Item(long id,String name,String sku,String barcode,BigDecimal price,BigDecimal tax_rate,long quantity,BigDecimal list_price,BigDecimal discount_percent,Long category_id,String category_name){}
 public record Result(List<Item> items,int page,int size,long totalElements,int totalPages,long catalogueTotal,List<CatalogSearchService.Category> categories){}
 private Item item(PosCatalogRow row){return new Item(row.getId(),row.getName(),row.getSku(),row.getBarcode(),row.getPrice(),row.getTaxRate(),Math.max(0,row.getAvailable()),row.getListPrice(),row.getDiscountPercent(),row.getCategoryId(),row.getCategoryName());}
 public Result search(long tenant,long branch,String requestedQuery,String category,String ordering,boolean stockOnly,int page,int size){
  String query=requestedQuery==null?"":requestedQuery.strip();category=category==null?"all":category;ordering=ordering==null?"name":ordering;
  if(tenant<1||branch<1||query.length()>120||page<0||page>10000||size<1||size>48||!Set.of("name","price-low","price-high").contains(ordering))throw invalid();
  long categoryId=0;boolean other=category.equals("other");
  if(!other&&!category.equals("all")){try{if(!category.matches("[1-9][0-9]{0,18}"))throw invalid();categoryId=Long.parseLong(category);}catch(NumberFormatException e){throw invalid();}}
  String pattern="%"+query.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
  var result=products.search(tenant,branch,pattern,"",false,categoryId,other,stockOnly,ordering,PageRequest.of(page,size));
  long all=products.countByTenantIdAndPublishedTrue(tenant),categorized=0;var categories=new ArrayList<CatalogSearchService.Category>();
  for(var c:catalogue.publishedCategories(tenant)){categories.add(new CatalogSearchService.Category(Long.toString(c.id()),c.name(),c.products()));categorized+=c.products();}
  if(all>categorized)categories.add(new CatalogSearchService.Category("other","Other",all-categorized));
  return new Result(result.stream().map(this::item).toList(),page,size,result.getTotalElements(),result.getTotalPages(),all,List.copyOf(categories));
 }
 public Item scan(long tenant,long branch,String requestedCode){
  if(tenant<1||branch<1||requestedCode==null||requestedCode.isBlank()||requestedCode.strip().length()>80)throw invalid();
  var result=products.search(tenant,branch,"",requestedCode.strip().toLowerCase(Locale.ROOT),true,0,false,false,"name",PageRequest.of(0,2));
  if(result.getTotalElements()==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"No exact SKU or barcode match");
  if(result.getTotalElements()!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Ambiguous SKU/barcode; search and select the correct item");
  return item(result.getContent().getFirst());
 }
 private ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Valid assigned scope, query/code, category, sort and bounded page required");}
}
