-- Automatically applied by Flyway V39 only when the Spring 'local' profile is active.
-- LOCAL DEMO FIXTURES ONLY. Never activate the local profile in production.
-- Tenant/catalogue/inventory fixtures only. Local user and role provisioning is handled by V51.
DELIMITER $$
DROP PROCEDURE IF EXISTS setup_multi_vendor_demo$$
CREATE PROCEDURE setup_multi_vendor_demo()
BEGIN
 DECLARE seed_lock INT DEFAULT 0;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION
 BEGIN
  ROLLBACK;
  DO RELEASE_LOCK('solar_multi_vendor_demo');
  RESIGNAL;
 END;
 -- Check schema before referencing any fixture or application tables.
 IF DATABASE() IS NULL OR
    NOT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='platform_user_permissions') OR
    NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='products' AND column_name='brand') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Demo seed requires Flyway V37 and V38. Rebuild/start updated backend on this database, then stop it and rerun seed.';
 END IF;
 SELECT GET_LOCK('solar_multi_vendor_demo',10) INTO seed_lock;
 IF seed_lock IS NULL OR seed_lock<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Demo seed is already running';
 END IF;
 START TRANSACTION;
-- BEGIN DEMO DATA
DROP TEMPORARY TABLE IF EXISTS demo_check;

DROP TEMPORARY TABLE IF EXISTS demo_vendors;



DROP TEMPORARY TABLE IF EXISTS demo_categories;

DROP TEMPORARY TABLE IF EXISTS demo_products;

DROP TEMPORARY TABLE IF EXISTS demo_registers;


CREATE TEMPORARY TABLE demo_check (valid BOOLEAN NOT NULL CHECK(valid=TRUE));

CREATE TEMPORARY TABLE demo_vendors (id BIGINT PRIMARY KEY,name VARCHAR(160),slug VARCHAR(100),branch_name VARCHAR(160));

INSERT INTO demo_vendors VALUES (910001,'Saumya Solar Rooftops','saumya-rooftops','Jaipur Rooftop Store'),
 (920001,'Helio Rural Energy','helio-rural','Nashik Farm Energy Store'),
 (930001,'Lumina Solar Lighting','lumina-lighting','Pune Lighting Store');

CREATE TEMPORARY TABLE demo_categories (id BIGINT PRIMARY KEY,tenant_id BIGINT,name VARCHAR(120),slug VARCHAR(100));

INSERT INTO demo_categories VALUES (910201,910001,'Solar Panels','solar-panels'),
 (910202,910001,'Rooftop Inverters','rooftop-inverters'),
 (910203,910001,'Mounting Kits','mounting-kits'),
 (920201,920001,'Solar Pumps','solar-pumps'),
 (920202,920001,'Farm Storage','farm-storage'),
 (920203,920001,'Pump Controllers','pump-controllers'),
 (930201,930001,'Street Lights','street-lights'),
 (930202,930001,'Garden Lights','garden-lights'),
 (930203,930001,'DC Accessories','dc-accessories');

CREATE TEMPORARY TABLE demo_products (id BIGINT PRIMARY KEY,tenant_id BIGINT,sku VARCHAR(80),barcode VARCHAR(80),name VARCHAR(160),brand VARCHAR(120),price DECIMAL(19,2),tax_rate DECIMAL(5,2),published BOOLEAN,quantity INT,category_id BIGINT);

INSERT INTO demo_products VALUES (910301,910001,'SS-PANEL-550','9100010000','SunArc 550W Mono PERC Panel','SunArc',11250,0,TRUE,40,910201),
 (910302,910001,'SS-PANEL-440','9100010001','SunArc 440W Compact Rooftop Panel','SunArc',9600,0,TRUE,24,910201),
 (910303,910001,'SS-INV-5K','9100010002','RoofVolt 5kW On-grid Inverter','RoofVolt',48500,0,TRUE,8,910202),
 (910304,910001,'SS-RAIL-4','9100010003','MountSure 4-panel Roof Rail Kit','MountSure',7250,0,TRUE,18,910203),
 (910305,910001,'SS-INV-10K','9100010004','RoofVolt 10kW Inverter - Out of Stock','RoofVolt',82500,0,TRUE,0,910202),
 (910306,910001,'SS-DRAFT-3K','9100010005','RoofVolt Prototype 3kW - Unpublished','RoofVolt',30000,0,FALSE,2,910202),
 (920301,920001,'HR-PUMP-3','9200010000','AgriRay 3HP Solar Submersible Pump','AgriRay',69500,0,TRUE,12,920201),
 (920302,920001,'HR-PUMP-5','9200010001','AgriRay 5HP Irrigation Pump','AgriRay',98500,0,TRUE,6,920201),
 (920303,920001,'HR-BAT-100','9200010002','FieldCell 100Ah LiFePO4 Farm Battery','FieldCell',42000,0,TRUE,15,920202),
 (920304,920001,'HR-CTRL-5','9200010003','PumpWise 5HP MPPT Pump Controller','PumpWise',18750,0,TRUE,20,920203),
 (920305,920001,'HR-BAT-200','9200010004','FieldCell 200Ah Battery - Out of Stock','FieldCell',79000,0,TRUE,0,920202),
 (920306,920001,'HR-DRAFT-7','9200010005','AgriRay Prototype 7HP - Unpublished','AgriRay',125000,0,FALSE,1,920201),
 (930301,930001,'LS-STREET-40','9300010000','NightSun 40W Integrated Street Light','NightSun',8500,0,TRUE,35,930201),
 (930302,930001,'LS-STREET-60','9300010001','NightSun 60W Motion-sensing Street Light','NightSun',12750,0,TRUE,22,930201),
 (930303,930001,'LS-GARDEN-6','9300010002','GlowLeaf Solar Garden Light Set of 6','GlowLeaf',3200,0,TRUE,50,930202),
 (930304,930001,'LS-CABLE-20','9300010003','BrightWire 20m Outdoor DC Cable Kit','BrightWire',1450,0,TRUE,60,930203),
 (930305,930001,'LS-GARDEN-12','9300010004','GlowLeaf Pathway Set - Out of Stock','GlowLeaf',5900,0,TRUE,0,930202),
 (930306,930001,'LS-DRAFT-80','9300010005','NightSun Prototype 80W - Unpublished','NightSun',15900,0,FALSE,3,930201);

CREATE TEMPORARY TABLE demo_registers (id BIGINT PRIMARY KEY,tenant_id BIGINT,branch_id BIGINT);

INSERT INTO demo_registers VALUES (910011,910001,910001),
 (910012,910001,910001),
 (910013,910001,910001),
 (920011,920001,920001),
 (920012,920001,920001),
 (920013,920001,920001),
 (930011,930001,930001),
 (930012,930001,930001),
 (930013,930001,930001);

INSERT INTO demo_check SELECT NOT EXISTS(SELECT 1 FROM demo_vendors d JOIN tenants t ON (t.id=d.id OR t.slug=d.slug) WHERE t.id<>d.id OR t.slug IS NULL OR t.slug<>d.slug);

INSERT INTO demo_check SELECT NOT EXISTS(SELECT 1 FROM demo_vendors d JOIN branches b ON b.id=d.id WHERE b.tenant_id<>d.id);







INSERT INTO demo_check SELECT NOT EXISTS(SELECT 1 FROM demo_products d JOIN products p ON (p.id=d.id OR (p.tenant_id=d.tenant_id AND (p.sku=d.sku OR p.barcode=d.barcode))) WHERE p.id<>d.id OR p.tenant_id<>d.tenant_id OR p.sku<>d.sku);

INSERT INTO demo_check SELECT NOT EXISTS(SELECT 1 FROM demo_categories d JOIN catalog_categories c ON (c.id=d.id OR (c.tenant_id=d.tenant_id AND c.slug=d.slug)) WHERE c.id<>d.id OR c.tenant_id<>d.tenant_id OR c.slug<>d.slug);

INSERT INTO demo_check SELECT NOT EXISTS(SELECT 1 FROM demo_registers d JOIN registers r ON r.id=d.id WHERE r.tenant_id<>d.tenant_id OR r.branch_id<>d.branch_id);



INSERT INTO tenants(id,name,active,slug) SELECT d.id,d.name,TRUE,d.slug FROM demo_vendors d WHERE NOT EXISTS(SELECT 1 FROM tenants t WHERE t.id=d.id);

INSERT INTO branches(id,tenant_id,name,invoice_required,pos_return_window_days,cash_refunds_enabled,split_tender_enabled) SELECT d.id,d.id,d.branch_name,FALSE,30,TRUE,TRUE FROM demo_vendors d WHERE NOT EXISTS(SELECT 1 FROM branches b WHERE b.id=d.id);

UPDATE tenants SET online_branch_id=id WHERE id IN (SELECT id FROM demo_vendors) AND online_branch_id IS NULL AND NOT EXISTS(SELECT 1 FROM products p WHERE p.tenant_id=tenants.id);














INSERT INTO catalog_discount_policy(tenant_id,maximum_percent) SELECT d.id,10.00 FROM demo_vendors d WHERE NOT EXISTS(SELECT 1 FROM catalog_discount_policy p WHERE p.tenant_id=d.id);

INSERT INTO registers(id,tenant_id,branch_id) SELECT d.id,d.tenant_id,d.branch_id FROM demo_registers d WHERE NOT EXISTS(SELECT 1 FROM registers r WHERE r.id=d.id);

INSERT INTO catalog_categories(id,tenant_id,name,slug) SELECT d.id,d.tenant_id,d.name,d.slug FROM demo_categories d WHERE NOT EXISTS(SELECT 1 FROM catalog_categories c WHERE c.id=d.id);

INSERT INTO products(id,tenant_id,sku,barcode,name,brand,price,tax_rate,published) SELECT d.id,d.tenant_id,d.sku,d.barcode,d.name,d.brand,d.price,d.tax_rate,d.published FROM demo_products d WHERE NOT EXISTS(SELECT 1 FROM products p WHERE p.id=d.id);

INSERT INTO product_categories(tenant_id,product_id,category_id) SELECT d.tenant_id,d.id,d.category_id FROM demo_products d WHERE NOT EXISTS(SELECT 1 FROM product_categories c WHERE c.tenant_id=d.tenant_id AND c.product_id=d.id);

INSERT INTO inventory(tenant_id,branch_id,product_id,quantity,reserved) SELECT d.tenant_id,d.tenant_id,d.id,d.quantity,0 FROM demo_products d WHERE NOT EXISTS(SELECT 1 FROM inventory i WHERE i.tenant_id=d.tenant_id AND i.branch_id=d.tenant_id AND i.product_id=d.id);

INSERT INTO catalog_stock(tenant_id,branch_id,product_id,available) SELECT i.tenant_id,i.branch_id,i.product_id,i.quantity-i.reserved FROM inventory i JOIN demo_products d ON d.id=i.product_id AND d.tenant_id=i.tenant_id WHERE NOT EXISTS(SELECT 1 FROM catalog_stock s WHERE s.tenant_id=i.tenant_id AND s.branch_id=i.branch_id AND s.product_id=i.product_id);





UPDATE identity_sequence SET next_id=GREATEST(next_id,950000) WHERE id=1;

UPDATE retail_sequences SET next_id=GREATEST(next_id,950000) WHERE name='REGISTER';

UPDATE catalog_sequences SET next_id=GREATEST(next_id,950000) WHERE name IN ('CATEGORY','PRODUCT');


DROP TEMPORARY TABLE demo_registers;

DROP TEMPORARY TABLE demo_products;

DROP TEMPORARY TABLE demo_categories;



DROP TEMPORARY TABLE demo_vendors;

DROP TEMPORARY TABLE demo_check;
-- END DEMO DATA
 COMMIT;
 DO RELEASE_LOCK('solar_multi_vendor_demo');
END$$
DELIMITER ;
CALL setup_multi_vendor_demo();
DROP PROCEDURE setup_multi_vendor_demo;
