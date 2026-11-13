-- Optional LOCAL demo catalogue, after Flyway V10 and pos-local-demo-setup.sql.
-- Select the existing solar schema in Workbench and run the entire script.
-- Prices are illustrative amounts; zero tax is demonstration data, not statutory tax advice.
-- Adds 18 products only to tenant/branch 900001. Safe to repeat after successful completion.
-- Existing products, prices, stock/reservations, category assignments, promotions and sales are never updated.
-- Requires an active demo tenant and its branch; otherwise inserts nothing.
-- If a statement fails, ROLLBACK and reconnect before retrying (temporary staging tables are session-owned).
USE solar;
CREATE TEMPORARY TABLE saumya_demo_solar_categories (id BIGINT PRIMARY KEY,name VARCHAR(120),slug VARCHAR(100));
CREATE TEMPORARY TABLE saumya_demo_solar_products (id BIGINT PRIMARY KEY,sku VARCHAR(80),barcode VARCHAR(80),name VARCHAR(160),category_slug VARCHAR(100),price DECIMAL(19,2),quantity INT);
INSERT INTO saumya_demo_solar_categories(id,name,slug) VALUES
(900011,'Solar panels','solar-panels'),
(900022,'Mounting structures','mounting-structures'),
(900023,'Solar lights','solar-lights'),
(900024,'Inverters','inverters'),
(900025,'Batteries','batteries'),
(900026,'Accessories','accessories');
INSERT INTO saumya_demo_solar_products(id,sku,barcode,name,category_slug,price,quantity) VALUES
(900101,'DEMO-PV550','900101','550W Mono PERC Solar Panel','solar-panels',12500.00,24),
(900102,'DEMO-PV450','900102','450W Monocrystalline Solar Panel','solar-panels',10000.00,18),
(900103,'DEMO-PV580','900103','580W Bifacial TOPCon Solar Panel','solar-panels',14500.00,16),
(900104,'DEMO-ROOF3','900104','3kW Galvanized Rooftop Mounting Structure','mounting-structures',18000.00,12),
(900105,'DEMO-GROUND5','900105','5kW Ground Mount Solar Structure','mounting-structures',35000.00,6),
(900106,'DEMO-BRACKET','900106','Adjustable Solar Panel Mounting Bracket Set','mounting-structures',2500.00,30),
(900107,'DEMO-STREET60','900107','60W Solar LED Street Light','solar-lights',6500.00,20),
(900108,'DEMO-GARDEN12','900108','12W Solar LED Garden Light','solar-lights',1250.00,40),
(900109,'DEMO-FLOOD100','900109','100W Solar LED Flood Light','solar-lights',4200.00,15),
(900110,'DEMO-INV3','900110','3kW On-Grid Solar Inverter','inverters',28000.00,10),
(900111,'DEMO-HYBRID5','900111','5kW Hybrid Solar Inverter','inverters',58000.00,8),
(900112,'DEMO-INV10','900112','10kW On-Grid Solar Inverter','inverters',78000.00,4),
(900113,'DEMO-TUB150','900113','150Ah Tall Tubular Solar Battery','batteries',15500.00,15),
(900114,'DEMO-LITH5','900114','5kWh Lithium Solar Battery','batteries',95000.00,5),
(900115,'DEMO-MPPT40','900115','40A MPPT Solar Charge Controller','accessories',3500.00,16),
(900116,'DEMO-DC6','900116','6mm2 Solar DC Cable - 10m','accessories',950.00,50),
(900117,'DEMO-MC4','900117','MC4 Solar Connector Pair','accessories',180.00,100),
(900118,'DEMO-COMBINER','900118','Solar AC/DC Combiner Protection Box','accessories',6500.00,12);

START TRANSACTION;
INSERT INTO catalog_categories(id,tenant_id,name,slug)
SELECT s.id,t.id,s.name,s.slug FROM saumya_demo_solar_categories s
JOIN tenants t ON t.id=900001 AND t.active=TRUE
JOIN branches b ON b.tenant_id=t.id AND b.id=900001
WHERE NOT EXISTS(SELECT 1 FROM catalog_categories c WHERE c.id=s.id OR (c.tenant_id=t.id AND c.slug=s.slug));

INSERT INTO products(id,tenant_id,sku,barcode,name,price,tax_rate,published)
SELECT s.id,t.id,s.sku,s.barcode,s.name,s.price,0.00,TRUE FROM saumya_demo_solar_products s
JOIN tenants t ON t.id=900001 AND t.active=TRUE
JOIN branches b ON b.tenant_id=t.id AND b.id=900001
WHERE NOT EXISTS(SELECT 1 FROM products p WHERE p.id=s.id OR (p.tenant_id=t.id AND (p.sku=s.sku OR p.barcode=s.barcode)));

INSERT INTO inventory(tenant_id,branch_id,product_id,quantity)
SELECT p.tenant_id,b.id,p.id,s.quantity FROM saumya_demo_solar_products s
JOIN products p ON p.id=s.id AND p.sku=s.sku AND p.tenant_id=900001
JOIN tenants t ON t.id=p.tenant_id AND t.active=TRUE
JOIN branches b ON b.tenant_id=t.id AND b.id=900001
WHERE NOT EXISTS(SELECT 1 FROM inventory i WHERE i.tenant_id=p.tenant_id AND i.branch_id=b.id AND i.product_id=p.id);

INSERT INTO product_categories(tenant_id,product_id,category_id)
SELECT p.tenant_id,p.id,c.id FROM saumya_demo_solar_products s
JOIN products p ON p.id=s.id AND p.sku=s.sku AND p.tenant_id=900001
JOIN tenants t ON t.id=p.tenant_id AND t.active=TRUE
JOIN branches b ON b.tenant_id=t.id AND b.id=900001
JOIN catalog_categories c ON c.tenant_id=p.tenant_id AND c.slug=s.category_slug
WHERE NOT EXISTS(SELECT 1 FROM product_categories a WHERE a.tenant_id=p.tenant_id AND a.product_id=p.id);
COMMIT;

SELECT p.sku,p.barcode,p.name,c.name AS category,p.price,p.tax_rate,i.quantity,i.reserved
FROM saumya_demo_solar_products s
JOIN products p ON p.id=s.id AND p.sku=s.sku AND p.tenant_id=900001
LEFT JOIN product_categories a ON a.tenant_id=p.tenant_id AND a.product_id=p.id
LEFT JOIN catalog_categories c ON c.tenant_id=a.tenant_id AND c.id=a.category_id
LEFT JOIN inventory i ON i.tenant_id=p.tenant_id AND i.product_id=p.id AND i.branch_id=900001
ORDER BY p.id;
DROP TABLE saumya_demo_solar_products;
DROP TABLE saumya_demo_solar_categories;
