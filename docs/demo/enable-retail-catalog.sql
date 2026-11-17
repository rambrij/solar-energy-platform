-- Optional LOCAL demo upgrade after Flyway V10 and existing demo setup.
-- Does not create stock, reset sales/passwords, overwrite category assignments or activate discounts.
USE solar;
INSERT INTO catalog_categories(id,tenant_id,name,slug)
SELECT 900011,900001,'Solar panels','solar-panels'
WHERE EXISTS(SELECT 1 FROM tenants WHERE id=900001)
AND NOT EXISTS(SELECT 1 FROM catalog_categories WHERE id=900011 OR (tenant_id=900001 AND slug='solar-panels'));
INSERT INTO product_categories(tenant_id,product_id,category_id)
SELECT p.tenant_id,p.id,c.id FROM products p JOIN catalog_categories c ON c.tenant_id=p.tenant_id AND c.slug='solar-panels'
WHERE p.tenant_id=900001 AND p.sku='DEMO-PANEL'
AND NOT EXISTS(SELECT 1 FROM product_categories a WHERE a.tenant_id=p.tenant_id AND a.product_id=p.id);
INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by)
SELECT 900001,r.id,p.permission,'local-supervisor'
FROM vendor_roles r
CROSS JOIN (SELECT 'CATALOG_PROMOTION_MANAGE' permission UNION ALL SELECT 'POS_DISCOUNT_APPROVE') p
WHERE r.tenant_id=900001 AND r.role_key='local-supervisor'
AND NOT EXISTS(SELECT 1 FROM vendor_role_permissions x WHERE x.tenant_id=r.tenant_id AND x.role_id=r.id AND x.permission=p.permission);
