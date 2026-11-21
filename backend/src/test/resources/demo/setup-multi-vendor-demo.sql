-- OPT-IN LOCAL/TEST DATA ONLY. Apply Flyway V50+ first so POS stock-location ownership is enforced. Never run on production.
-- Select the local database in Workbench first. no database name is hardcoded here.
-- Public demo credentials and exact permission presets: docs/demo/multi-vendor-testing.md.
-- Run while the application is stopped. this script does not reset operational data.
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
    NOT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='pos_stock_locations') OR
    NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='products' AND column_name='brand') THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Demo seed requires Flyway V50 (POS stock-location ownership). Rebuild/start updated backend on this database, then stop it and rerun seed.';
 END IF;
 SELECT GET_LOCK('solar_multi_vendor_demo',10) INTO seed_lock;
 IF seed_lock IS NULL OR seed_lock<>1 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Demo seed is already running';
 END IF;
 START TRANSACTION;
-- BEGIN DEMO DATA
DROP TEMPORARY TABLE IF EXISTS demo_check;

DROP TEMPORARY TABLE IF EXISTS demo_vendors;

DROP TEMPORARY TABLE IF EXISTS demo_accounts;

DROP TEMPORARY TABLE IF EXISTS demo_new_accounts;

DROP TEMPORARY TABLE IF EXISTS demo_categories;

DROP TEMPORARY TABLE IF EXISTS demo_products;

DROP TEMPORARY TABLE IF EXISTS demo_registers;

DROP TEMPORARY TABLE IF EXISTS demo_grants;

CREATE TEMPORARY TABLE demo_check (valid BOOLEAN NOT NULL CHECK(valid=TRUE));

CREATE TEMPORARY TABLE demo_vendors (id BIGINT PRIMARY KEY,name VARCHAR(160),slug VARCHAR(100),branch_name VARCHAR(160));

INSERT INTO demo_vendors VALUES (910001,'Saumya Solar Rooftops','saumya-rooftops','Jaipur Rooftop Store'),
 (920001,'Helio Rural Energy','helio-rural','Nashik Farm Energy Store'),
 (930001,'Lumina Solar Lighting','lumina-lighting','Pune Lighting Store');

CREATE TEMPORARY TABLE demo_accounts (id BIGINT PRIMARY KEY,username VARCHAR(100),password_hash VARCHAR(200),tenant_id BIGINT,role_name VARCHAR(40));

INSERT INTO demo_accounts VALUES (910101,'admin@saumya.demo.test','$2a$10$ZW/gDxU1bYq5BtemgJIPN.p0p5P67Jd4ip/hYDVznhVnFPq58JOxa',910001,'VENDOR_ADMIN'),
 (910102,'sales@saumya.demo.test','$2a$10$bBJJBfEd4qyxleRLNAHV0.lrlCxIKNJeDbJZ51JdzXed9Nm9YpxYi',910001,'SALES_MANAGER'),
 (910103,'inventory@saumya.demo.test','$2a$10$jxTW2GE87c8SAuN5pbjafuaoXZlel5xa9itWduwhXxWu039vj0GVm',910001,'INVENTORY_MANAGER'),
 (910104,'cashier@saumya.demo.test','$2a$10$mYHMHE0zqVFxGdz0HS/P8.KgbUHHYQLrbuqiXLpZHZ73nyZ3s9Cn6',910001,'POS_CASHIER'),
 (910105,'finance@saumya.demo.test','$2a$10$as7IxoUT48Tv3jhZ6/3G5ODfPFpTJ4GQjsyE9SEDYkc4G7VCxJiZi',910001,'ACCOUNTANT'),
 (920101,'admin@helio.demo.test','$2a$10$51d8VApLp/rpmCw7.IplZecOlZLP2dWSwmkC2EoO/GWEgUOY4a9f.',920001,'VENDOR_ADMIN'),
 (920102,'sales@helio.demo.test','$2a$10$kHFViA7yOCnbNk.dfiQ7ye5FfIUCFuVKmakWfJfitMtnyQ7.iBRMy',920001,'SALES_MANAGER'),
 (920103,'inventory@helio.demo.test','$2a$10$U9xE8F4SbLZaHJ6izLo0junRrHVC1O6zzJhJWuFpS3LyTRVtOYgr6',920001,'INVENTORY_MANAGER'),
 (920104,'cashier@helio.demo.test','$2a$10$Z0Rbjt6R7Z4o3uGoAZ75UOI5A4uzC9s76LcwJq5aPfb5jp3GLzpkG',920001,'POS_CASHIER'),
 (920105,'finance@helio.demo.test','$2a$10$W084uZNVYh/Lx9qL1vqFOunpBZcash10czuxRIgn1Rn8DNtAZiO22',920001,'ACCOUNTANT'),
 (930101,'admin@lumina.demo.test','$2a$10$j3xa5vm70gZLG0/zvB2FGOG8Jnc6qxAGD3xOR8q3vzvV80zTf82qi',930001,'VENDOR_ADMIN'),
 (930102,'sales@lumina.demo.test','$2a$10$Va3Lu3ue2ngBy93g8JfyhOfiuH4Y7PrNbqSNeCDEIE.wSkBikbJH6',930001,'SALES_MANAGER'),
 (930103,'inventory@lumina.demo.test','$2a$10$NFNh48JOFzvicxebmOkM5.AqkHpGNixNOCvnI0T72pag8t2Gp9fEm',930001,'INVENTORY_MANAGER'),
 (930104,'cashier@lumina.demo.test','$2a$10$Zz8I9pma7GUWQ2P.ETHnDueKoZRFrOXcDIIV.wZU.Cm9i2gQXaDqG',930001,'POS_CASHIER'),
 (930105,'finance@lumina.demo.test','$2a$10$1CxsBJp1JHyGeXLL/BP9MOXdE/ssF.dLn7Cw9ZYLVfmop3/K7mzcG',930001,'ACCOUNTANT'),
 (940001,'superadmin@platform.demo.test','$2a$10$F40TEv3oN0s232CLki6Qt.rEIWzKAiF.jUHFydSR21PkINrqHd0S6',NULL,'PLATFORM_SUPER_ADMIN'),
 (949001,'asha@customer.demo.test','$2a$10$XnFTU62LfAmsjJHfIucnM.4/kfZYSumxHqC8YwbhYkw0PQq7nxPDO',NULL,'CUSTOMER'),
 (949002,'rahul@customer.demo.test','$2a$10$Jqoe3d.y.Bc5HEDxZGuHnu1kh/z3XfFubpFsb0VFkbtZ.yQoul7jG',NULL,'CUSTOMER');

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

INSERT INTO demo_check SELECT NOT EXISTS(SELECT 1 FROM demo_accounts d JOIN users u ON (u.id=d.id OR u.username=d.username) WHERE u.id<>d.id OR u.username<>d.username);

INSERT INTO demo_check SELECT NOT EXISTS(SELECT 1 FROM demo_accounts d JOIN employees e ON e.username=d.username WHERE d.tenant_id IS NULL OR e.tenant_id<>d.tenant_id OR e.branch_id<>d.tenant_id);

INSERT INTO demo_check SELECT NOT EXISTS(SELECT 1 FROM demo_accounts d JOIN users u ON u.id=d.id WHERE d.tenant_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM employees e WHERE e.username=d.username AND e.tenant_id=d.tenant_id));

INSERT INTO demo_check SELECT NOT EXISTS(SELECT 1 FROM demo_accounts d JOIN customer_profiles c ON c.user_id=d.id WHERE d.role_name<>'CUSTOMER' OR c.id<>d.id);

INSERT INTO demo_check SELECT NOT EXISTS(SELECT 1 FROM demo_accounts d JOIN platform_user_permissions g ON g.username=d.username WHERE d.role_name<>'PLATFORM_SUPER_ADMIN');

INSERT INTO demo_check SELECT NOT EXISTS(SELECT 1 FROM customer_profiles c JOIN demo_accounts d ON c.id=d.id WHERE c.user_id<>d.id);

INSERT INTO demo_check SELECT NOT EXISTS(SELECT 1 FROM demo_products d JOIN products p ON (p.id=d.id OR (p.tenant_id=d.tenant_id AND (p.sku=d.sku OR p.barcode=d.barcode))) WHERE p.id<>d.id OR p.tenant_id<>d.tenant_id OR p.sku<>d.sku);

INSERT INTO demo_check SELECT NOT EXISTS(SELECT 1 FROM demo_categories d JOIN catalog_categories c ON (c.id=d.id OR (c.tenant_id=d.tenant_id AND c.slug=d.slug)) WHERE c.id<>d.id OR c.tenant_id<>d.tenant_id OR c.slug<>d.slug);

INSERT INTO demo_check SELECT NOT EXISTS(SELECT 1 FROM demo_registers d JOIN registers r ON r.id=d.id WHERE r.tenant_id<>d.tenant_id OR r.branch_id<>d.branch_id);

INSERT INTO demo_check SELECT NOT EXISTS(SELECT 1 FROM tenant_customers c WHERE c.id IN ('demo-asha-saumya','demo-asha-helio','demo-asha-lumina','demo-rahul-helio') AND NOT ((c.id='demo-asha-saumya' AND c.tenant_id=910001 AND c.customer_id=949001) OR (c.id='demo-asha-helio' AND c.tenant_id=920001 AND c.customer_id=949001) OR (c.id='demo-asha-lumina' AND c.tenant_id=930001 AND c.customer_id=949001) OR (c.id='demo-rahul-helio' AND c.tenant_id=920001 AND c.customer_id=949002)));

CREATE TEMPORARY TABLE demo_new_accounts AS SELECT d.* FROM demo_accounts d WHERE NOT EXISTS(SELECT 1 FROM users u WHERE u.id=d.id);

INSERT INTO tenants(id,name,legal_name,lifecycle_status,slug) SELECT d.id,d.name,d.name,'ACTIVE',d.slug FROM demo_vendors d WHERE NOT EXISTS(SELECT 1 FROM tenants t WHERE t.id=d.id);

INSERT INTO branches(id,tenant_id,name,invoice_required,pos_return_window_days,cash_refunds_enabled,split_tender_enabled) SELECT d.id,d.id,d.branch_name,FALSE,30,TRUE,TRUE FROM demo_vendors d WHERE NOT EXISTS(SELECT 1 FROM branches b WHERE b.id=d.id);

-- Branches created after the V50 backfill need their server-designated POS stock room in this seed transaction.
INSERT INTO pos_stock_locations(tenant_id,branch_id,stock_location_id,location_code,display_name,active)
SELECT d.id,d.id,d.id,'POS_DEFAULT',CONCAT(d.branch_name,' POS stock room'),TRUE
FROM demo_vendors d
WHERE NOT EXISTS(SELECT 1 FROM pos_stock_locations p WHERE p.tenant_id=d.id AND p.branch_id=d.id);

UPDATE tenants SET online_branch_id=id WHERE id IN (SELECT id FROM demo_vendors) AND online_branch_id IS NULL AND NOT EXISTS(SELECT 1 FROM products p WHERE p.tenant_id=tenants.id);

INSERT INTO users(id,username,password_hash,enabled) SELECT id,username,password_hash,TRUE FROM demo_new_accounts;

INSERT INTO employees(username,tenant_id,branch_id,enabled) SELECT username,tenant_id,tenant_id,TRUE FROM demo_new_accounts WHERE tenant_id IS NOT NULL;
-- Local demo tenants use a persisted subscription. Local and test profiles never bypass entitlements.
INSERT INTO subscription_plans(plan_key,display_name,active,current_version)
SELECT 'local-demo-unlimited','Local Demo Unlimited',TRUE,1
WHERE NOT EXISTS(SELECT 1 FROM subscription_plans WHERE plan_key='local-demo-unlimited');

INSERT INTO subscription_plan_versions(plan_id,version,price,currency,billing_interval,
 max_users,max_storage_mb,max_branches,max_warehouses,entitlements,grace_days,tax_metadata,
 invoice_description,created_by)
SELECT p.id,1,0,'INR','MONTHLY',1000,100000,100,100,
 '{"features":{"POS":true,"CATALOG":true,"INVENTORY":true,"ADMINISTRATION":true,"CRM":true,"LEADS":true,"QUOTATIONS":true,"ORDERS":true,"RETURNS":true,"FINANCE":true,"SHIPMENTS":true,"SERVICE":true,"REPORTING":true}}',
 30,'{}','Local demo subscription','superadmin@platform.demo.test'
FROM subscription_plans p
WHERE p.plan_key='local-demo-unlimited'
 AND NOT EXISTS(SELECT 1 FROM subscription_plan_versions v WHERE v.plan_id=p.id AND v.version=1);

INSERT INTO tenant_subscriptions(tenant_id,plan_id,plan_version,status,
 billing_period_start,billing_period_end,terms_snapshot)
SELECT d.id,p.id,1,'ACTIVE',DATE '2026-01-01',DATE '2099-12-31','{"demo":true}'
FROM demo_vendors d CROSS JOIN subscription_plans p
WHERE p.plan_key='local-demo-unlimited'
 AND NOT EXISTS(SELECT 1 FROM tenant_subscriptions s WHERE s.tenant_id=d.id);

INSERT INTO customer_profiles(id,user_id) SELECT id,id FROM demo_new_accounts WHERE role_name='CUSTOMER';

CREATE TEMPORARY TABLE demo_grants (role_name VARCHAR(40),permission VARCHAR(60),PRIMARY KEY(role_name,permission));

INSERT INTO demo_grants VALUES
 ('VENDOR_ADMIN','CATALOG_PROMOTION_MANAGE'),
 ('VENDOR_ADMIN','INVENTORY_RECEIVE'),
 ('VENDOR_ADMIN','INVENTORY_VIEW'),
 ('VENDOR_ADMIN','POS_CASHIER_REPORT_VIEW'),
 ('VENDOR_ADMIN','POS_CASH_IN'),
 ('VENDOR_ADMIN','POS_CASH_OUT'),
 ('VENDOR_ADMIN','POS_DISCOUNT_APPLY'),
 ('VENDOR_ADMIN','POS_DISCOUNT_APPROVE'),
 ('VENDOR_ADMIN','POS_DISCOUNT_OVERRIDE'),
 ('VENDOR_ADMIN','POS_RECEIPT_REPRINT'),
 ('VENDOR_ADMIN','POS_RECEIPT_VIEW'),
 ('VENDOR_ADMIN','POS_REFUND_APPROVE'),
 ('VENDOR_ADMIN','POS_REFUND_CASH_RECORD'),
 ('VENDOR_ADMIN','POS_REFUND_PRINT'),
 ('VENDOR_ADMIN','POS_REFUND_VIEW'),
 ('VENDOR_ADMIN','POS_REPORT_EXPORT'),
 ('VENDOR_ADMIN','POS_REPORT_VIEW'),
 ('VENDOR_ADMIN','POS_RETURN_APPROVE'),
 ('VENDOR_ADMIN','POS_RETURN_AUDIT_VIEW'),
 ('VENDOR_ADMIN','POS_RETURN_CREATE'),
 ('VENDOR_ADMIN','POS_RETURN_INSPECT'),
 ('VENDOR_ADMIN','POS_RETURN_VIEW'),
 ('VENDOR_ADMIN','POS_SAFE_DROP'),
 ('VENDOR_ADMIN','POS_SALE_CANCEL'),
 ('VENDOR_ADMIN','POS_SALE_CREATE'),
 ('VENDOR_ADMIN','POS_SESSION_CLOSE'),
 ('VENDOR_ADMIN','POS_SESSION_OPEN'),
 ('VENDOR_ADMIN','POS_SESSION_REOPEN'),
 ('VENDOR_ADMIN','POS_SETTINGS_MANAGE'),
 ('VENDOR_ADMIN','POS_SHIFT_VARIANCE_APPROVE'),
 ('VENDOR_ADMIN','POS_TENDER_CASH_RECORD'),
 ('VENDOR_ADMIN','POS_TENDER_EXTERNAL_RECORD'),
 ('VENDOR_ADMIN','POS_TENDER_MANUAL_VERIFY'),
 ('VENDOR_ADMIN','POS_VIEW'),
 ('VENDOR_ADMIN','PRICE_UPDATE'),
 ('VENDOR_ADMIN','PRICE_VIEW'),
 ('VENDOR_ADMIN','PRODUCT_ARCHIVE'),
 ('VENDOR_ADMIN','PRODUCT_CREATE'),
 ('VENDOR_ADMIN','PRODUCT_UPDATE'),
 ('VENDOR_ADMIN','PRODUCT_VIEW'),
 ('VENDOR_ADMIN','ROLE_ASSIGN'),
 ('VENDOR_ADMIN','ROLE_CREATE'),
 ('VENDOR_ADMIN','ROLE_UPDATE'),
 ('VENDOR_ADMIN','ROLE_VIEW'),
 ('VENDOR_ADMIN','USER_CREATE'),
 ('VENDOR_ADMIN','USER_DISABLE'),
 ('VENDOR_ADMIN','USER_UPDATE'),
 ('VENDOR_ADMIN','USER_VIEW'),
 ('POS_CASHIER','POS_VIEW'),
 ('POS_CASHIER','POS_SESSION_OPEN'),
 ('POS_CASHIER','POS_SESSION_CLOSE'),
 ('POS_CASHIER','POS_SALE_CREATE'),
 ('POS_CASHIER','POS_TENDER_CASH_RECORD'),
 ('POS_CASHIER','POS_TENDER_EXTERNAL_RECORD'),
 ('POS_CASHIER','POS_RECEIPT_VIEW'),
 ('SALES_MANAGER','POS_VIEW'),
 ('SALES_MANAGER','POS_REPORT_VIEW'),
 ('INVENTORY_MANAGER','PRODUCT_VIEW'),
 ('INVENTORY_MANAGER','INVENTORY_VIEW'),
 ('INVENTORY_MANAGER','INVENTORY_RECEIVE'),
 ('ACCOUNTANT','POS_VIEW'),
 ('ACCOUNTANT','POS_REPORT_VIEW'),
 ('ACCOUNTANT','POS_REFUND_VIEW');

INSERT INTO vendor_roles(tenant_id,role_key,display_name,active,created_by,updated_by)
SELECT a.tenant_id,CONCAT('demo-',REPLACE(a.username,'@','-')),a.role_name,TRUE,a.username,a.username
FROM demo_new_accounts a WHERE a.tenant_id IS NOT NULL;

INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by)
SELECT a.tenant_id,r.id,g.permission,a.username
FROM demo_new_accounts a JOIN demo_grants g ON g.role_name=a.role_name
JOIN vendor_roles r ON r.tenant_id=a.tenant_id AND r.role_key=CONCAT('demo-',REPLACE(a.username,'@','-'));

INSERT INTO employee_role_assignments(tenant_id,branch_id,username,role_id,active,assigned_by)
SELECT a.tenant_id,a.tenant_id,a.username,r.id,TRUE,a.username
FROM demo_new_accounts a JOIN vendor_roles r
ON r.tenant_id=a.tenant_id AND r.role_key=CONCAT('demo-',REPLACE(a.username,'@','-'));

INSERT INTO platform_user_permissions(username,permission) SELECT username,'PLATFORM_TENANT_CREATE' FROM demo_new_accounts WHERE role_name='PLATFORM_SUPER_ADMIN';
INSERT INTO platform_user_permissions(username,permission) SELECT username,'PLATFORM_TENANT_VIEW' FROM demo_new_accounts WHERE role_name='PLATFORM_SUPER_ADMIN';
INSERT INTO platform_user_permissions(username,permission) SELECT username,'PLATFORM_TENANT_REACTIVATE' FROM demo_new_accounts WHERE role_name='PLATFORM_SUPER_ADMIN';
INSERT INTO platform_user_permissions(username,permission) SELECT username,'PLATFORM_IDENTITY_MANAGE' FROM demo_new_accounts WHERE role_name='PLATFORM_SUPER_ADMIN';

INSERT INTO platform_user_permissions(username,permission) SELECT username,'PLATFORM_TENANT_UPDATE' FROM demo_new_accounts WHERE role_name='PLATFORM_SUPER_ADMIN';

INSERT INTO platform_user_permissions(username,permission) SELECT username,'PLATFORM_TENANT_SUSPEND' FROM demo_new_accounts WHERE role_name='PLATFORM_SUPER_ADMIN';

INSERT INTO platform_user_permissions(username,permission) SELECT username,'PLATFORM_TENANT_CLOSE' FROM demo_new_accounts WHERE role_name='PLATFORM_SUPER_ADMIN';

INSERT INTO platform_user_permissions(username,permission) SELECT username,'PLATFORM_PLAN_MANAGE' FROM demo_new_accounts WHERE role_name='PLATFORM_SUPER_ADMIN';

INSERT INTO platform_user_permissions(username,permission) SELECT username,'PLATFORM_SUBSCRIPTION_VIEW' FROM demo_new_accounts WHERE role_name='PLATFORM_SUPER_ADMIN';

INSERT INTO platform_user_permissions(username,permission) SELECT username,'PLATFORM_SUBSCRIPTION_ADJUST' FROM demo_new_accounts WHERE role_name='PLATFORM_SUPER_ADMIN';

INSERT INTO platform_user_permissions(username,permission) SELECT username,'PLATFORM_ANALYTICS_VIEW' FROM demo_new_accounts WHERE role_name='PLATFORM_SUPER_ADMIN';

INSERT INTO platform_user_permissions(username,permission) SELECT username,'PLATFORM_AUDIT_VIEW' FROM demo_new_accounts WHERE role_name='PLATFORM_SUPER_ADMIN';

INSERT INTO platform_user_permissions(username,permission) SELECT username,'PLATFORM_SUPPORT_ACCESS' FROM demo_new_accounts WHERE role_name='PLATFORM_SUPER_ADMIN';
INSERT INTO platform_user_permissions(username,permission) SELECT username,'PLATFORM_CONFIG_MANAGE' FROM demo_new_accounts WHERE role_name='PLATFORM_SUPER_ADMIN';

INSERT INTO catalog_discount_policy(tenant_id,maximum_percent) SELECT d.id,10.00 FROM demo_vendors d WHERE NOT EXISTS(SELECT 1 FROM catalog_discount_policy p WHERE p.tenant_id=d.id);

INSERT INTO registers(id,tenant_id,branch_id) SELECT d.id,d.tenant_id,d.branch_id FROM demo_registers d WHERE NOT EXISTS(SELECT 1 FROM registers r WHERE r.id=d.id);
-- Named POS cashiers receive the first configured till in their own tenant branch.
INSERT INTO employee_register_assignments(tenant_id,branch_id,username,register_id,active,assigned_by)
SELECT e.tenant_id,e.branch_id,e.username,MIN(r.id),TRUE,e.username
FROM employees e
JOIN employee_role_assignments a ON a.tenant_id=e.tenant_id AND a.username=e.username AND a.active=TRUE
JOIN vendor_roles vr ON vr.tenant_id=a.tenant_id AND vr.id=a.role_id AND vr.active=TRUE
JOIN vendor_role_permissions p ON p.tenant_id=a.tenant_id AND p.role_id=a.role_id AND p.permission='POS_SESSION_OPEN'
JOIN registers r ON r.tenant_id=e.tenant_id AND r.branch_id=e.branch_id
WHERE e.username LIKE 'cashier@%'
GROUP BY e.tenant_id,e.branch_id,e.username
ON DUPLICATE KEY UPDATE active=TRUE,assigned_by=VALUES(assigned_by),assigned_at=CURRENT_TIMESTAMP,revoked_by=NULL,revoked_at=NULL;



INSERT INTO catalog_categories(id,tenant_id,name,slug) SELECT d.id,d.tenant_id,d.name,d.slug FROM demo_categories d WHERE NOT EXISTS(SELECT 1 FROM catalog_categories c WHERE c.id=d.id);

INSERT INTO products(id,tenant_id,sku,barcode,name,brand,price,tax_rate,published) SELECT d.id,d.tenant_id,d.sku,d.barcode,d.name,d.brand,d.price,d.tax_rate,d.published FROM demo_products d WHERE NOT EXISTS(SELECT 1 FROM products p WHERE p.id=d.id);

INSERT INTO product_categories(tenant_id,product_id,category_id) SELECT d.tenant_id,d.id,d.category_id FROM demo_products d WHERE NOT EXISTS(SELECT 1 FROM product_categories c WHERE c.tenant_id=d.tenant_id AND c.product_id=d.id);

INSERT INTO inventory(tenant_id,branch_id,product_id,quantity,reserved) SELECT d.tenant_id,d.tenant_id,d.id,d.quantity,0 FROM demo_products d WHERE NOT EXISTS(SELECT 1 FROM inventory i WHERE i.tenant_id=d.tenant_id AND i.branch_id=d.tenant_id AND i.product_id=d.id);

INSERT INTO catalog_stock(tenant_id,branch_id,product_id,available) SELECT i.tenant_id,i.branch_id,i.product_id,i.quantity-i.reserved FROM inventory i JOIN demo_products d ON d.id=i.product_id AND d.tenant_id=i.tenant_id WHERE NOT EXISTS(SELECT 1 FROM catalog_stock s WHERE s.tenant_id=i.tenant_id AND s.branch_id=i.branch_id AND s.product_id=i.product_id);

INSERT INTO tenant_customers(id,tenant_id,customer_id,consent_at,provenance) SELECT 'demo-asha-saumya',910001,949001,CURRENT_TIMESTAMP,'DEMO_EXPLICIT_CONSENT' WHERE NOT EXISTS(SELECT 1 FROM tenant_customers WHERE tenant_id=910001 AND customer_id=949001);

INSERT INTO tenant_customers(id,tenant_id,customer_id,consent_at,provenance) SELECT 'demo-asha-helio',920001,949001,CURRENT_TIMESTAMP,'DEMO_EXPLICIT_CONSENT' WHERE NOT EXISTS(SELECT 1 FROM tenant_customers WHERE tenant_id=920001 AND customer_id=949001);

INSERT INTO tenant_customers(id,tenant_id,customer_id,consent_at,provenance) SELECT 'demo-asha-lumina',930001,949001,CURRENT_TIMESTAMP,'DEMO_EXPLICIT_CONSENT' WHERE NOT EXISTS(SELECT 1 FROM tenant_customers WHERE tenant_id=930001 AND customer_id=949001);

INSERT INTO tenant_customers(id,tenant_id,customer_id,consent_at,provenance) SELECT 'demo-rahul-helio',920001,949002,CURRENT_TIMESTAMP,'DEMO_EXPLICIT_CONSENT' WHERE NOT EXISTS(SELECT 1 FROM tenant_customers WHERE tenant_id=920001 AND customer_id=949002);

UPDATE identity_sequence SET next_id=GREATEST(next_id,950000) WHERE id=1;

UPDATE retail_sequences SET next_id=GREATEST(next_id,950000) WHERE name='REGISTER';

UPDATE catalog_sequences SET next_id=GREATEST(next_id,950000) WHERE name IN ('CATEGORY','PRODUCT');

DROP TEMPORARY TABLE demo_grants;

DROP TEMPORARY TABLE demo_registers;

DROP TEMPORARY TABLE demo_products;

DROP TEMPORARY TABLE demo_categories;

DROP TEMPORARY TABLE demo_new_accounts;

DROP TEMPORARY TABLE demo_accounts;

DROP TEMPORARY TABLE demo_vendors;

DROP TEMPORARY TABLE demo_check;
-- END DEMO DATA
 COMMIT;
 DO RELEASE_LOCK('solar_multi_vendor_demo');
END$$
DELIMITER ;
CALL setup_multi_vendor_demo();
DROP PROCEDURE setup_multi_vendor_demo;
