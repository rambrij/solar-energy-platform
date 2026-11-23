-- Local development only. Seed a database immediately after V1 is applied.
START TRANSACTION;

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

INSERT INTO tenants(id,name,lifecycle_status,legal_name,slug) SELECT d.id,d.name,'ACTIVE',d.name,d.slug FROM demo_vendors d WHERE NOT EXISTS(SELECT 1 FROM tenants t WHERE t.id=d.id);

INSERT INTO branches(id,tenant_id,name,invoice_required,pos_return_window_days,cash_refunds_enabled,split_tender_enabled) SELECT d.id,d.id,d.branch_name,FALSE,30,TRUE,TRUE FROM demo_vendors d WHERE NOT EXISTS(SELECT 1 FROM branches b WHERE b.id=d.id);

UPDATE tenants SET online_branch_id=id WHERE id IN (SELECT id FROM demo_vendors) AND online_branch_id IS NULL AND NOT EXISTS(SELECT 1 FROM products p WHERE p.tenant_id=tenants.id);

INSERT INTO catalog_discount_policy(tenant_id,maximum_percent) SELECT d.id,10.00 FROM demo_vendors d WHERE NOT EXISTS(SELECT 1 FROM catalog_discount_policy p WHERE p.tenant_id=d.id);

INSERT INTO pos_stock_locations(tenant_id,branch_id,stock_location_id,location_code,display_name,active)
SELECT d.id,d.id,d.id,'POS_DEFAULT',CONCAT(d.branch_name,' POS stock room'),TRUE FROM demo_vendors d;

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

CREATE TEMPORARY TABLE demo_v51_accounts (
 id BIGINT PRIMARY KEY, username VARCHAR(100) NOT NULL, password_hash VARCHAR(200) NOT NULL,
 tenant_id BIGINT NULL, branch_id BIGINT NULL, role_name VARCHAR(40) NOT NULL
);
INSERT INTO demo_v51_accounts VALUES
(941001,'platform.superadmin@somya.local','$2a$10$QultlCCf5KV.7j6TMwt2PuTHFqVjetwnyVoSPNGq3CP4DnDP3XF.W',NULL,NULL,'PLATFORM_SUPER_ADMIN'),
(911101,'saumya.admin@somya.local','$2a$10$6d2PfzezJINOR3aAFdD3C.H3WcnDPZRtsvBD8ZNq6mv6dT/lcjKye',910001,910001,'VENDOR_ADMIN'),
(911102,'saumya.sales.manager@somya.local','$2a$10$7Ga7ah7nYdCa6PR/3BRt/uDzUJ2ptTZeD.93JlbNcZ22mrywql8NG',910001,910001,'SALES_MANAGER'),
(911103,'saumya.sales.executive@somya.local','$2a$10$YT67Z42AJVMB5PJVgsS6nevsMsCQIMbcVh7U30aSx5xT4z/abS9Nm',910001,910001,'SALES_EXECUTIVE'),
(911104,'saumya.inventory.manager@somya.local','$2a$10$c0uvffwJ.foyHmss.UIuBuyj97PgwPRlMrqQnwZxSzW85BUJlzEaK',910001,910001,'INVENTORY_MANAGER'),
(911105,'saumya.pos.cashier@somya.local','$2a$10$buJ9x7nunghKUkG3uyC0TuLrhFrnosEG496CtE4nz21u3c9Yh6qJi',910001,910001,'POS_CASHIER'),
(911106,'saumya.accountant@somya.local','$2a$10$r254GIECUfE4OJHpWP.GpeJDe1lTAPIztryk7.N0aRj/zifjxIUXC',910001,910001,'ACCOUNTANT'),
(911107,'saumya.operations.manager@somya.local','$2a$10$fJz77PBX2NPY/FudwSKoTeZGOiRShS8M5RV1qoMH1iHVR6Qc6OLvO',910001,910001,'OPERATIONS_MANAGER'),
(911108,'saumya.service.manager@somya.local','$2a$10$oeZMuAzIZ/iyBneJggnoUu.eLyj0Ttudh.uMwWGDXctF4e5mGrTou',910001,910001,'SERVICE_MANAGER'),
(911109,'saumya.service.engineer@somya.local','$2a$10$HnyzPAxhKKVzRCSbuhZKj.z6rsyYbEKRibKI7LThzsPBn04LCKp0O',910001,910001,'SERVICE_ENGINEER'),
(911110,'saumya.content.manager@somya.local','$2a$10$.G1ptKWqNoom4ouUVPOEf.SeylLhN6I2DoBC5JYhmDYKynKEAM2wm',910001,910001,'CONTENT_MANAGER'),
(921101,'helio.admin@somya.local','$2a$10$dHaYgALtuQfYaUK2dRB5..6TU13SLOmrlxpH604m1XEBywPYxw9L.',920001,920001,'VENDOR_ADMIN'),
(921102,'helio.sales.manager@somya.local','$2a$10$frXsIfsa9RYemHxbc1oKW.FCT9L0f53efLZixkHc2YMu9NfuA/p8W',920001,920001,'SALES_MANAGER'),
(921103,'helio.sales.executive@somya.local','$2a$10$OA6PEtXZ9ng8OSdRuBTjw.KZGWw/CVW7SXiM4vgfMXadLqwk..3jy',920001,920001,'SALES_EXECUTIVE'),
(921104,'helio.inventory.manager@somya.local','$2a$10$iFjKfAubfaoF609hq5KXxeOlf4qOgyJqpj7RNJIWw8awE2xG1qp/G',920001,920001,'INVENTORY_MANAGER'),
(921105,'helio.pos.cashier@somya.local','$2a$10$sDhEuiBBFx0RQrE7svILqulLoRUhlvqMFz0b8fZ7WoAXReLuPuGsC',920001,920001,'POS_CASHIER'),
(921106,'helio.accountant@somya.local','$2a$10$o8aOjUjS5TEg7EmQRHDI6OqO3AxK81OLBlJMlFm3bpDNqVpnbH2/O',920001,920001,'ACCOUNTANT'),
(921107,'helio.operations.manager@somya.local','$2a$10$FGz/NZ1//ztBudC5S7EV/e6fRTvmIuZTwMXtXajO4WvVLfNWycrUW',920001,920001,'OPERATIONS_MANAGER'),
(921108,'helio.service.manager@somya.local','$2a$10$ChB0CY029djW9FsDjk1ltu9r2Nr.LZTtASwECeSPBVC.3fhG/BMh.',920001,920001,'SERVICE_MANAGER'),
(921109,'helio.service.engineer@somya.local','$2a$10$q1G2OBfC.htaoCw7Tf96keV7UQ26dtWsACl7K1PoDCDVcdPpTiwd.',920001,920001,'SERVICE_ENGINEER'),
(921110,'helio.content.manager@somya.local','$2a$10$5bhxpNonLfyF7hbRDBav3ui7aaL7xNJUEXn9u2DzQ1a91U0uppn.u',920001,920001,'CONTENT_MANAGER'),
(931101,'lumina.admin@somya.local','$2a$10$P9zFfQ/A0e2N8DTNy678IuwVv/4amdb3SaNPbNN136xpbeENzqPh.',930001,930001,'VENDOR_ADMIN'),
(931102,'lumina.sales.manager@somya.local','$2a$10$azxMOr99hlFUuoZXkZX8nec5svgzUEC8bkXlGn.izpji/X8q6Rcme',930001,930001,'SALES_MANAGER'),
(931103,'lumina.sales.executive@somya.local','$2a$10$0pJMzZxqSz1x7Fp2Rj5IAe.JY7g/UnloNGe4GS0uJ5kzH6TzQhWp6',930001,930001,'SALES_EXECUTIVE'),
(931104,'lumina.inventory.manager@somya.local','$2a$10$NnKhfl6ii9yWxE.PeHzrQOVwvK6tv.RQeFzjX9SXc2f.UW1VHqU9e',930001,930001,'INVENTORY_MANAGER'),
(931105,'lumina.pos.cashier@somya.local','$2a$10$XNflWVYh9an8eyD4mSqEPexLIXur6jgYXAwcWwyGlV6qcsPi36gUe',930001,930001,'POS_CASHIER'),
(931106,'lumina.accountant@somya.local','$2a$10$hF6ky6MEYuVz3xXjvgEBDOsYe7/1Y6vYzSu.ckhHR0S03mqSHFc8W',930001,930001,'ACCOUNTANT'),
(931107,'lumina.operations.manager@somya.local','$2a$10$aCbc444GZ4rS1mQAy0ciXOh0TQ1H2KPppuVdradHaUXfl7H/MAKn6',930001,930001,'OPERATIONS_MANAGER'),
(931108,'lumina.service.manager@somya.local','$2a$10$L6C6K6izMtvz/xnicSSV/ORZGvtxSHxYRcWonks2bRygvED9W5/iC',930001,930001,'SERVICE_MANAGER'),
(931109,'lumina.service.engineer@somya.local','$2a$10$8m5zJZNzYrOlN.CcN.zYMOm2UQFetXuU93pAKS5P2XCXnUhSo0vrG',930001,930001,'SERVICE_ENGINEER'),
(931110,'lumina.content.manager@somya.local','$2a$10$x8wrBTuZbFOoSv7u22GjNOdLaIBD2.swLIR5qwJlgapH5/IP4hiom',930001,930001,'CONTENT_MANAGER'),
(949011,'customer.asha@somya.local','$2a$10$a7.5udx1.q0JaqoGimvdTOPlt56WkI4NbpodtkuQoSTIjH9fFqwCq',NULL,NULL,'CUSTOMER'),
(949012,'customer.rahul@somya.local','$2a$10$TC8Vd0L9/yKYXHVY4vPPGeLyLcNGc/bOzK6YJmmhlWRC6JJYpkWs6',NULL,NULL,'CUSTOMER');

INSERT INTO users(id,username,password_hash,enabled)
SELECT id,username,password_hash,TRUE FROM demo_v51_accounts;
INSERT INTO employees(username,tenant_id,branch_id,enabled)
SELECT username,tenant_id,branch_id,TRUE FROM demo_v51_accounts WHERE tenant_id IS NOT NULL;
INSERT INTO customer_profiles(id,user_id)
SELECT id,id FROM demo_v51_accounts WHERE role_name='CUSTOMER';

-- Materialize only supported v1 standard templates as tenant-owned roles.
-- Deferred role definitions receive no grants or assignments until their APIs exist.
CREATE TEMPORARY TABLE demo_v51_standard_permissions (
 role_name VARCHAR(40) NOT NULL, permission VARCHAR(80) NOT NULL,
 PRIMARY KEY(role_name,permission)
);
INSERT INTO demo_v51_standard_permissions VALUES
('VENDOR_ADMIN','CATALOG_PROMOTION_MANAGE'),('VENDOR_ADMIN','INVENTORY_RECEIVE'),('VENDOR_ADMIN','INVENTORY_VIEW'),
('VENDOR_ADMIN','POS_CASHIER_REPORT_VIEW'),('VENDOR_ADMIN','POS_CASH_IN'),('VENDOR_ADMIN','POS_CASH_OUT'),
('VENDOR_ADMIN','POS_DISCOUNT_APPLY'),('VENDOR_ADMIN','POS_DISCOUNT_APPROVE'),('VENDOR_ADMIN','POS_DISCOUNT_OVERRIDE'),
('VENDOR_ADMIN','POS_RECEIPT_REPRINT'),('VENDOR_ADMIN','POS_RECEIPT_VIEW'),('VENDOR_ADMIN','POS_REFUND_APPROVE'),
('VENDOR_ADMIN','POS_REFUND_CASH_RECORD'),('VENDOR_ADMIN','POS_REFUND_PRINT'),('VENDOR_ADMIN','POS_REFUND_VIEW'),
('VENDOR_ADMIN','POS_REPORT_EXPORT'),('VENDOR_ADMIN','POS_REPORT_VIEW'),('VENDOR_ADMIN','POS_RETURN_AUDIT_VIEW'),
('VENDOR_ADMIN','POS_RETURN_APPROVE'),('VENDOR_ADMIN','POS_RETURN_CREATE'),('VENDOR_ADMIN','POS_RETURN_INSPECT'),
('VENDOR_ADMIN','POS_RETURN_VIEW'),('VENDOR_ADMIN','POS_SAFE_DROP'),('VENDOR_ADMIN','POS_SALE_CANCEL'),
('VENDOR_ADMIN','POS_SALE_CREATE'),('VENDOR_ADMIN','POS_SESSION_CLOSE'),('VENDOR_ADMIN','POS_SESSION_OPEN'),
('VENDOR_ADMIN','POS_SESSION_REOPEN'),('VENDOR_ADMIN','POS_SETTINGS_MANAGE'),('VENDOR_ADMIN','POS_SHIFT_VARIANCE_APPROVE'),
('VENDOR_ADMIN','POS_TENDER_CASH_RECORD'),('VENDOR_ADMIN','POS_TENDER_EXTERNAL_RECORD'),('VENDOR_ADMIN','POS_TENDER_MANUAL_VERIFY'),
('VENDOR_ADMIN','POS_VIEW'),('VENDOR_ADMIN','PRICE_UPDATE'),('VENDOR_ADMIN','PRICE_VIEW'),('VENDOR_ADMIN','PRODUCT_ARCHIVE'),
('VENDOR_ADMIN','PRODUCT_CREATE'),('VENDOR_ADMIN','PRODUCT_UPDATE'),('VENDOR_ADMIN','PRODUCT_VIEW'),
('VENDOR_ADMIN','ROLE_ASSIGN'),('VENDOR_ADMIN','ROLE_CREATE'),('VENDOR_ADMIN','ROLE_UPDATE'),('VENDOR_ADMIN','ROLE_VIEW'),
('VENDOR_ADMIN','USER_CREATE'),('VENDOR_ADMIN','USER_DISABLE'),('VENDOR_ADMIN','USER_UPDATE'),('VENDOR_ADMIN','USER_VIEW'),
('POS_CASHIER','POS_VIEW'),('POS_CASHIER','POS_SESSION_OPEN'),('POS_CASHIER','POS_SESSION_CLOSE'),
('POS_CASHIER','POS_SALE_CREATE'),('POS_CASHIER','POS_DISCOUNT_APPLY'),('POS_CASHIER','POS_TENDER_CASH_RECORD'),('POS_CASHIER','POS_TENDER_EXTERNAL_RECORD'),('POS_CASHIER','POS_RECEIPT_VIEW'),
('SALES_MANAGER','POS_VIEW'),('SALES_MANAGER','POS_REPORT_VIEW'),
('INVENTORY_MANAGER','PRODUCT_VIEW'),('INVENTORY_MANAGER','INVENTORY_VIEW'),('INVENTORY_MANAGER','INVENTORY_RECEIVE'),
('ACCOUNTANT','POS_VIEW'),('ACCOUNTANT','POS_REPORT_VIEW'),('ACCOUNTANT','POS_REFUND_VIEW');

INSERT INTO vendor_roles(tenant_id,role_key,display_name,active,created_by,updated_by,template_key,template_version)
SELECT DISTINCT e.tenant_id,CONCAT('standard:',p.role_name),p.role_name,TRUE,admin.username,admin.username,p.role_name,1
FROM employees e
JOIN (SELECT DISTINCT role_name FROM demo_v51_standard_permissions) p
JOIN employees admin ON admin.tenant_id=e.tenant_id AND admin.username LIKE '%.admin@somya.local'
WHERE NOT EXISTS(SELECT 1 FROM vendor_roles r WHERE r.tenant_id=e.tenant_id AND r.role_key=CONCAT('standard:',p.role_name));

INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by)
SELECT r.tenant_id,r.id,p.permission,admin.username
FROM vendor_roles r JOIN demo_v51_standard_permissions p ON r.role_key=CONCAT('standard:',p.role_name)
JOIN employees admin ON admin.tenant_id=r.tenant_id AND admin.username LIKE '%.admin@somya.local'
WHERE NOT EXISTS(SELECT 1 FROM vendor_role_permissions rp WHERE rp.tenant_id=r.tenant_id AND rp.role_id=r.id AND rp.permission=p.permission);

INSERT INTO employee_role_assignments(tenant_id,branch_id,username,role_id,active,assigned_by)
SELECT a.tenant_id,a.branch_id,a.username,r.id,TRUE,admin.username
FROM demo_v51_accounts a
JOIN vendor_roles r ON r.tenant_id=a.tenant_id AND r.role_key=CONCAT('standard:',a.role_name)
JOIN employees admin ON admin.tenant_id=a.tenant_id AND admin.username LIKE '%.admin@somya.local'
WHERE a.tenant_id IS NOT NULL
  AND a.role_name IN ('VENDOR_ADMIN','POS_CASHIER','SALES_MANAGER','INVENTORY_MANAGER','ACCOUNTANT');

INSERT INTO vendor_access_audit_events(tenant_id,actor_username,resource_type,resource_id,action,reason)
SELECT r.tenant_id,r.created_by,'ROLE',CAST(r.id AS CHAR),'ROLE_TEMPLATE_APPLIED',
       'Initial local demo standard-role template materialization'
FROM vendor_roles r
WHERE r.role_key LIKE 'standard:%';

INSERT INTO vendor_access_audit_events(tenant_id,actor_username,subject_username,resource_type,resource_id,action,reason)
SELECT a.tenant_id,a.assigned_by,a.username,'ROLE_ASSIGNMENT',CAST(a.role_id AS CHAR),'ROLE_ASSIGNED',
       'Initial local demo employee role assignment'
FROM employee_role_assignments a
JOIN demo_v51_accounts d ON d.username=a.username AND d.tenant_id=a.tenant_id
WHERE a.active=TRUE AND a.role_id IN (
 SELECT r.id FROM vendor_roles r WHERE r.tenant_id=a.tenant_id AND r.role_key LIKE 'standard:%'
);

-- Till/register assignment: POS cashier gets the sales till, accountant the finance till,
-- and Vendor Admin the manager till. Register IDs were seeded above for each tenant.
INSERT INTO employee_register_assignments(tenant_id,branch_id,username,register_id,active,assigned_by)
SELECT a.tenant_id,a.branch_id,a.username,
       CASE a.role_name WHEN 'POS_CASHIER' THEN a.tenant_id+10
                        WHEN 'ACCOUNTANT' THEN a.tenant_id+11
                        WHEN 'VENDOR_ADMIN' THEN a.tenant_id+12 END,
       TRUE,a.username
FROM demo_v51_accounts a
WHERE a.tenant_id IS NOT NULL
  AND a.role_name IN ('POS_CASHIER','ACCOUNTANT','VENDOR_ADMIN')
  AND EXISTS(
    SELECT 1 FROM registers reg
    WHERE reg.tenant_id=a.tenant_id AND reg.branch_id=a.branch_id
      AND reg.id=CASE a.role_name WHEN 'POS_CASHIER' THEN a.tenant_id+10
                                  WHEN 'ACCOUNTANT' THEN a.tenant_id+11
                                  WHEN 'VENDOR_ADMIN' THEN a.tenant_id+12 END
  );

INSERT INTO vendor_access_audit_events(tenant_id,actor_username,subject_username,resource_type,resource_id,action,reason)
SELECT a.tenant_id,a.assigned_by,a.username,'REGISTER_ASSIGNMENT',CAST(a.register_id AS CHAR),'REGISTER_ASSIGNED',
       'Initial local demo register assignment'
FROM employee_register_assignments a
JOIN demo_v51_accounts d ON d.username=a.username AND d.tenant_id=a.tenant_id
WHERE a.active=TRUE;

INSERT INTO platform_user_permissions(username,permission)
SELECT a.username,p.permission
FROM demo_v51_accounts a
CROSS JOIN (
 SELECT 'PLATFORM_TENANT_CREATE' AS permission
 UNION ALL SELECT 'PLATFORM_TENANT_REACTIVATE' AS permission
 UNION ALL SELECT 'PLATFORM_TENANT_UPDATE' AS permission
 UNION ALL SELECT 'PLATFORM_TENANT_SUSPEND' AS permission
 UNION ALL SELECT 'PLATFORM_TENANT_CLOSE' AS permission
 UNION ALL SELECT 'PLATFORM_PLAN_MANAGE' AS permission
 UNION ALL SELECT 'PLATFORM_SUBSCRIPTION_VIEW' AS permission
 UNION ALL SELECT 'PLATFORM_SUBSCRIPTION_ADJUST' AS permission
 UNION ALL SELECT 'PLATFORM_ANALYTICS_VIEW' AS permission
 UNION ALL SELECT 'PLATFORM_AUDIT_VIEW' AS permission
 UNION ALL SELECT 'PLATFORM_SUPPORT_ACCESS' AS permission
) p
WHERE a.role_name='PLATFORM_SUPER_ADMIN';

-- Synthetic, explicit-consent relationships for the shared global customer identities.
INSERT INTO tenant_customers(id,tenant_id,customer_id,consent_at,provenance)
SELECT CONCAT('local-v51-asha-',v.tenant_id),v.tenant_id,949011,CURRENT_TIMESTAMP,'DEMO_EXPLICIT_CONSENT'
FROM (SELECT 910001 AS tenant_id UNION ALL SELECT 920001 UNION ALL SELECT 930001) v
WHERE NOT EXISTS(SELECT 1 FROM tenant_customers c WHERE c.tenant_id=v.tenant_id AND c.customer_id=949011);
INSERT INTO tenant_customers(id,tenant_id,customer_id,consent_at,provenance)
SELECT 'local-v51-rahul-920001',920001,949012,CURRENT_TIMESTAMP,'DEMO_EXPLICIT_CONSENT'
WHERE NOT EXISTS(SELECT 1 FROM tenant_customers c WHERE c.tenant_id=920001 AND c.customer_id=949012);

DROP TEMPORARY TABLE demo_v51_standard_permissions;
DROP TEMPORARY TABLE demo_v51_accounts;

INSERT INTO subscription_plans(plan_key,display_name,active,current_version)
SELECT 'local-demo-unlimited','Local Demo Unlimited',TRUE,1
WHERE NOT EXISTS (
    SELECT 1 FROM subscription_plans WHERE plan_key='local-demo-unlimited'
);

INSERT INTO subscription_plan_versions(
    plan_id,version,price,currency,billing_interval,max_users,max_storage_mb,
    max_branches,max_warehouses,entitlements,grace_days,tax_metadata,
    invoice_description,created_by
)
SELECT p.id,1,0,'INR','MONTHLY',1000,100000,100,100,
    '{"features":{"POS":true,"CATALOG":true,"INVENTORY":true,"ADMINISTRATION":true,"CRM":true,"LEADS":true,"QUOTATIONS":true,"ORDERS":true,"RETURNS":true,"FINANCE":true,"SHIPMENTS":true,"SERVICE":true,"REPORTING":true}}',
    30,'{}','Local development demo subscription','saumya.admin@somya.local'
FROM subscription_plans p
WHERE p.plan_key='local-demo-unlimited'
  AND NOT EXISTS (
      SELECT 1 FROM subscription_plan_versions v
      WHERE v.plan_id=p.id AND v.version=1
  );

INSERT INTO tenant_subscriptions(
    tenant_id,plan_id,plan_version,status,billing_period_start,
    billing_period_end,terms_snapshot
)
SELECT d.tenant_id,p.id,1,'ACTIVE',DATE '2026-01-01',DATE '2099-12-31',
       '{"local_demo":true,"entitlements":"all_demo_features"}'
FROM (
    SELECT 910001 AS tenant_id
    UNION ALL SELECT 920001
    UNION ALL SELECT 930001
) d
JOIN subscription_plans p ON p.plan_key='local-demo-unlimited'
WHERE EXISTS (
    SELECT 1 FROM tenants t WHERE t.id=d.tenant_id
)
AND NOT EXISTS (
    SELECT 1 FROM tenant_subscriptions s WHERE s.tenant_id=d.tenant_id
);

UPDATE tenant_sequences SET next_id=GREATEST(next_id,930002) WHERE id=1;
UPDATE retail_sequences SET next_id=GREATEST(next_id,930002) WHERE name='BRANCH';
COMMIT;
