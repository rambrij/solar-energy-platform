package com.saumya.pos.catalog.internal;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.*;
public interface CatalogSearchRepository extends Repository<Product,Long> {
 @Query(value="""
 select p.id as id,p.sku as sku,p.name as name,round(p.price*(100-coalesce(offer.percent,0))/100,2) as price,p.tax_rate as taxRate,
 p.price as listPrice,coalesce(offer.percent,0) as discountPercent,pc.category_id as categoryId,
 coalesce(category.name,'Other') as categoryName,coalesce(stock.quantity-stock.reserved,0) as available
 from products p join tenants t on t.id=p.tenant_id and t.active=TRUE
 left join catalog_promotions offer on offer.tenant_id=p.tenant_id and offer.product_id=p.id
 left join product_categories pc on pc.tenant_id=p.tenant_id and pc.product_id=p.id
 left join catalog_categories category on category.tenant_id=pc.tenant_id and category.id=pc.category_id
 left join inventory stock on stock.tenant_id=p.tenant_id and stock.branch_id=t.online_branch_id and stock.product_id=p.id
 where p.tenant_id=:tenant and p.published=TRUE
 and (lower(p.name) like :pattern escape '!' or lower(p.sku) like :pattern escape '!')
 and (:categoryId=0 or pc.category_id=:categoryId) and (:other=FALSE or pc.category_id is null)
 and (:stockOnly=FALSE or coalesce(stock.quantity-stock.reserved,0)>0)
 order by
 case when :ordering='price-low' then round(p.price*(100-coalesce(offer.percent,0))/100,2) end asc,
 case when :ordering='price-high' then round(p.price*(100-coalesce(offer.percent,0))/100,2) end desc,
 case when :ordering='name' then lower(p.name) end asc,p.id asc
 """,countQuery="""
 select count(*)
 from products p join tenants t on t.id=p.tenant_id and t.active=TRUE
 left join catalog_promotions offer on offer.tenant_id=p.tenant_id and offer.product_id=p.id
 left join product_categories pc on pc.tenant_id=p.tenant_id and pc.product_id=p.id
 left join catalog_categories category on category.tenant_id=pc.tenant_id and category.id=pc.category_id
 left join inventory stock on stock.tenant_id=p.tenant_id and stock.branch_id=t.online_branch_id and stock.product_id=p.id
 where p.tenant_id=:tenant and p.published=TRUE
 and (lower(p.name) like :pattern escape '!' or lower(p.sku) like :pattern escape '!')
 and (:categoryId=0 or pc.category_id=:categoryId) and (:other=FALSE or pc.category_id is null)
 and (:stockOnly=FALSE or coalesce(stock.quantity-stock.reserved,0)>0)
 """,nativeQuery=true)
 Page<CatalogSearchRow> search(long tenant,String pattern,long categoryId,boolean other,boolean stockOnly,String ordering,Pageable pageable);
 long countByTenantIdAndPublishedTrue(long tenant);
}
