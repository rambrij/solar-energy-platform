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
INSERT INTO employee_permissions(username,permission)
SELECT e.username,p.permission FROM employees e
CROSS JOIN(SELECT 'CATALOG_PROMOTION_MANAGE' AS permission UNION ALL SELECT 'POS_DISCOUNT_APPROVE') p
WHERE e.username='local-supervisor' AND e.tenant_id=900001 AND e.branch_id=900001
AND NOT EXISTS(SELECT 1 FROM employee_permissions g WHERE g.username=e.username AND g.permission=p.permission);
