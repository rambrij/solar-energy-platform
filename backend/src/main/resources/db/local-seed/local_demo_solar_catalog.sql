-- Local-profile startup seed. Runs after Flyway and only against the three tenant-owned demo catalogs.
-- Idempotent: stable tenant/SKU/barcode IDs, no stock resets, and deterministic opening receipt keys.
-- Tax percentages and pricing are illustrative demo values, not HSN/GST compliance advice.
START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS demo_catalog_check;
DROP TEMPORARY TABLE IF EXISTS demo_catalog_categories;
DROP TEMPORARY TABLE IF EXISTS demo_catalog_products;
DROP TEMPORARY TABLE IF EXISTS demo_catalog_opening_receipts;

CREATE TEMPORARY TABLE demo_catalog_check (
  valid BOOLEAN NOT NULL CHECK (valid = TRUE)
);

CREATE TEMPORARY TABLE demo_catalog_categories (
  id BIGINT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  name VARCHAR(120) NOT NULL,
  slug VARCHAR(100) NOT NULL
);

INSERT INTO demo_catalog_categories VALUES
 (910201,910001,'Solar Panels','solar-panels'),
 (910202,910001,'Rooftop Inverters','rooftop-inverters'),
 (910203,910001,'Solar Mounting Structures','mounting-kits'),
 (910204,910001,'Solar Distribution Boards','solar-distribution-boards'),
 (910205,910001,'Solar Cables','solar-cables'),
 (910206,910001,'Solar Connectors & Protection','solar-connectors-protection'),
 (910207,910001,'Rooftop Solar Kits','rooftop-solar-kits'),
 (920201,920001,'Solar Pumps','solar-pumps'),
 (920202,920001,'Solar Batteries & Storage','farm-storage'),
 (920203,920001,'Solar Pump Controllers','pump-controllers'),
 (920204,920001,'Off-grid Solar Inverters','off-grid-inverters'),
 (920205,920001,'Solar Charge Controllers','solar-charge-controllers'),
 (920206,920001,'Solar Home Lighting','solar-home-lighting'),
 (920207,920001,'Off-grid Solar Kits','off-grid-solar-kits'),
 (930201,930001,'Solar Street Lights','street-lights'),
 (930202,930001,'Solar Garden & Pathway Lights','garden-lights'),
 (930203,930001,'Lighting Accessories','dc-accessories'),
 (930204,930001,'Solar Flood Lights','solar-flood-lights'),
 (930205,930001,'Lighting Batteries','lighting-batteries'),
 (930206,930001,'Solar Lighting Controllers','solar-lighting-controllers'),
 (930207,930001,'Poles & Mounting Accessories','poles-mounts');

CREATE TEMPORARY TABLE demo_catalog_products (
  id BIGINT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  sku VARCHAR(80) NOT NULL,
  barcode VARCHAR(80) NOT NULL,
  name VARCHAR(160) NOT NULL,
  brand VARCHAR(120) NOT NULL,
  price DECIMAL(19,2) NOT NULL,
  tax_rate DECIMAL(5,2) NOT NULL,
  published BOOLEAN NOT NULL,
  category_id BIGINT NOT NULL
);

INSERT INTO demo_catalog_products VALUES
 -- Saumya Solar Rooftops: on-grid panels, inverters, rooftop hardware and kits.
 (910301,910001,'SS-PANEL-550','9100010000','SunArc 550W Mono PERC Solar Panel','SunArc',11250.00,5.00,TRUE,910201),
 (910302,910001,'SS-PANEL-440','9100010001','SunArc 440W Compact Rooftop Solar Panel','SunArc',9600.00,5.00,TRUE,910201),
 (910303,910001,'SS-INV-5K','9100010002','RoofVolt 5kW On-grid Solar Inverter','RoofVolt',48500.00,18.00,TRUE,910202),
 (910304,910001,'SS-RAIL-4','9100010003','MountSure 4-panel Aluminium Roof Rail Kit','MountSure',7250.00,18.00,TRUE,910203),
 (910305,910001,'SS-INV-10K','9100010004','RoofVolt 10kW Three-phase On-grid Inverter','RoofVolt',82500.00,18.00,TRUE,910202),
 (910306,910001,'SS-INV-3K','9100010005','RoofVolt 3kW Single-phase On-grid Inverter','RoofVolt',30000.00,18.00,TRUE,910202),
 (910307,910001,'SS-ACDB-3P','9100010006','GridGuard 3-phase AC Distribution Box','GridGuard',7800.00,18.00,TRUE,910204),
 (910308,910001,'SS-DCDB-2S','9100010007','GridGuard 2-string DC Distribution Box','GridGuard',5200.00,18.00,TRUE,910204),
 (910309,910001,'SS-CABLE-6MM','9100010008','BrightWire 6mm² Solar DC Cable Roll 100m','BrightWire',7200.00,18.00,TRUE,910205),
 (910310,910001,'SS-MC4-PAIR','9100010009','SunLink MC4-compatible Connector Pair','SunLink',180.00,18.00,TRUE,910206),
 (910311,910001,'SS-EARTH-KIT','9100010010','SafeEarth Solar Earthing & Surge Protection Kit','SafeEarth',3950.00,18.00,TRUE,910206),
 (910312,910001,'SS-ROOF-5KW','9100010011','SunArc 5kW Grid-connected Rooftop Solar Kit','SunArc',285000.00,5.00,TRUE,910207),

 -- Helio Rural Energy: irrigation pumps, off-grid power, batteries and rural kits.
 (920301,920001,'HR-PUMP-3','9200010000','AgriRay 3HP Solar Submersible Pump','AgriRay',69500.00,5.00,TRUE,920201),
 (920302,920001,'HR-PUMP-5','9200010001','AgriRay 5HP Solar Irrigation Pump','AgriRay',98500.00,5.00,TRUE,920201),
 (920303,920001,'HR-BAT-100','9200010002','FieldCell 100Ah LiFePO4 Solar Battery','FieldCell',42000.00,18.00,TRUE,920202),
 (920304,920001,'HR-CTRL-5','9200010003','PumpWise 5HP MPPT Solar Pump Controller','PumpWise',18750.00,18.00,TRUE,920203),
 (920305,920001,'HR-BAT-200','9200010004','FieldCell 200Ah LiFePO4 Solar Battery','FieldCell',79000.00,18.00,TRUE,920202),
 (920306,920001,'HR-INV-75K','9200010005','FieldPower 7.5kW Off-grid Solar Inverter','FieldPower',125000.00,18.00,TRUE,920204),
 (920307,920001,'HR-INV-3K','9200010006','FieldPower 3kW Off-grid Solar Inverter','FieldPower',48000.00,18.00,TRUE,920204),
 (920308,920001,'HR-CC-60A','9200010007','ChargeWay 60A MPPT Solar Charge Controller','ChargeWay',12500.00,18.00,TRUE,920205),
 (920309,920001,'HR-HOME-5L','9200010008','VillageLite Five-point Solar Home Lighting Kit','VillageLite',18500.00,5.00,TRUE,920206),
 (920310,920001,'HR-KIT-1KW','9200010009','FieldPower 1kW Off-grid Home Solar Kit','FieldPower',98000.00,5.00,TRUE,920207),
 (920311,920001,'HR-PUMP-DC2','9200010010','AgriRay 2-inch DC Solar Surface Pump','AgriRay',28500.00,5.00,TRUE,920201),
 (920312,920001,'HR-BAT-150','9200010011','FieldCell 150Ah Tubular Solar Battery','FieldCell',19500.00,18.00,TRUE,920202),

 -- Lumina Solar Lighting: integrated street/flood/garden lights and lighting parts.
 (930301,930001,'LS-STREET-40','9300010000','NightSun 40W Integrated Solar Street Light','NightSun',8500.00,5.00,TRUE,930201),
 (930302,930001,'LS-STREET-60','9300010001','NightSun 60W Motion-sensing Solar Street Light','NightSun',12750.00,5.00,TRUE,930201),
 (930303,930001,'LS-GARDEN-6','9300010002','GlowLeaf Solar Garden Light Set of 6','GlowLeaf',3200.00,5.00,TRUE,930202),
 (930304,930001,'LS-CABLE-20','9300010003','BrightWire 20m Outdoor Solar DC Cable Kit','BrightWire',1450.00,18.00,TRUE,930203),
 (930305,930001,'LS-GARDEN-12','9300010004','GlowLeaf Solar Pathway Light Set of 12','GlowLeaf',5900.00,5.00,TRUE,930202),
 (930306,930001,'LS-FLOOD-80','9300010005','NightSun 80W Solar LED Flood Light','NightSun',15900.00,5.00,TRUE,930204),
 (930307,930001,'LS-FLOOD-100','9300010006','NightSun 100W Solar LED Flood Light','NightSun',14500.00,5.00,TRUE,930204),
 (930308,930001,'LS-BAT-40','9300010007','GlowCell 12.8V 40Ah LiFePO4 Lighting Battery','GlowCell',13500.00,18.00,TRUE,930205),
 (930309,930001,'LS-CTRL-10A','9300010008','DuskSmart 10A Automatic Solar Lighting Controller','DuskSmart',1450.00,18.00,TRUE,930206),
 (930310,930001,'LS-POLE-4M','9300010009','PolePro 4m Galvanized Solar Light Pole','PolePro',12500.00,18.00,TRUE,930207),
 (930311,930001,'LS-SENSOR-PIR','9300010010','DuskSmart PIR Motion Sensor Module','DuskSmart',890.00,18.00,TRUE,930206),
 (930312,930001,'LS-GARDEN-12W','9300010011','GlowLeaf 12W Solar Pathway Light','GlowLeaf',1150.00,5.00,TRUE,930202);

-- Verify that the local demo tenants own their designated POS stock location before writing.
INSERT INTO demo_catalog_check
SELECT NOT EXISTS (
  SELECT 1
  FROM (SELECT 910001 AS tenant_id UNION ALL SELECT 920001 UNION ALL SELECT 930001) d
  LEFT JOIN tenants t ON t.id=d.tenant_id
  LEFT JOIN branches b ON b.tenant_id=d.tenant_id AND b.id=d.tenant_id
  LEFT JOIN pos_stock_locations l ON l.tenant_id=d.tenant_id AND l.branch_id=d.tenant_id
  WHERE t.id IS NULL OR t.lifecycle_status<>'ACTIVE' OR b.id IS NULL OR l.branch_id IS NULL
);

-- Prevent a seed ID collision across tenants and duplicate tenant SKUs/barcodes.
INSERT INTO demo_catalog_check
SELECT NOT EXISTS (
  SELECT 1
  FROM demo_catalog_categories d
  JOIN catalog_categories c
    ON c.id=d.id OR (c.tenant_id=d.tenant_id AND c.slug=d.slug)
  WHERE c.tenant_id<>d.tenant_id OR c.id<>d.id OR c.slug<>d.slug
);

INSERT INTO demo_catalog_check
SELECT NOT EXISTS (
  SELECT 1
  FROM demo_catalog_products d
  JOIN products p
    ON p.id=d.id OR (p.tenant_id=d.tenant_id AND (p.sku=d.sku OR p.barcode=d.barcode))
  WHERE p.tenant_id<>d.tenant_id OR (p.id<>d.id AND (p.sku=d.sku OR p.barcode=d.barcode))
);

INSERT INTO catalog_categories(id,tenant_id,name,slug)
SELECT d.id,d.tenant_id,d.name,d.slug
FROM demo_catalog_categories d
WHERE NOT EXISTS (SELECT 1 FROM catalog_categories c WHERE c.id=d.id);

UPDATE catalog_categories c
JOIN demo_catalog_categories d ON c.id=d.id AND c.tenant_id=d.tenant_id
SET c.name=d.name,c.slug=d.slug,c.version=c.version+1
WHERE NOT (c.name <=> d.name AND c.slug <=> d.slug);

INSERT INTO products(id,tenant_id,sku,barcode,name,brand,price,tax_rate,published)
SELECT d.id,d.tenant_id,d.sku,d.barcode,d.name,d.brand,d.price,d.tax_rate,d.published
FROM demo_catalog_products d
WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.id=d.id);

UPDATE products p
JOIN demo_catalog_products d ON p.id=d.id AND p.tenant_id=d.tenant_id
SET p.sku=d.sku,p.barcode=d.barcode,p.name=d.name,p.brand=d.brand,
    p.price=d.price,p.tax_rate=d.tax_rate,p.published=d.published,p.version=p.version+1
WHERE NOT (p.sku <=> d.sku AND p.barcode <=> d.barcode AND p.name <=> d.name
       AND p.brand <=> d.brand AND p.price <=> d.price
       AND p.tax_rate <=> d.tax_rate AND p.published <=> d.published);

UPDATE product_categories pc
JOIN demo_catalog_products d ON pc.tenant_id=d.tenant_id AND pc.product_id=d.id
SET pc.category_id=d.category_id
WHERE pc.category_id<>d.category_id;

INSERT INTO product_categories(tenant_id,product_id,category_id)
SELECT d.tenant_id,d.id,d.category_id
FROM demo_catalog_products d
WHERE NOT EXISTS (
  SELECT 1 FROM product_categories pc
  WHERE pc.tenant_id=d.tenant_id AND pc.product_id=d.id
);

-- Each vendor has one designated POS location. Start missing balances at zero.
INSERT INTO inventory(tenant_id,branch_id,product_id,quantity,reserved)
SELECT d.tenant_id,d.tenant_id,d.id,0,0
FROM demo_catalog_products d
WHERE NOT EXISTS (
  SELECT 1 FROM inventory i
  WHERE i.tenant_id=d.tenant_id AND i.branch_id=d.tenant_id AND i.product_id=d.id
);

CREATE TEMPORARY TABLE demo_catalog_opening_receipts (
  tenant_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  available_after INT NOT NULL,
  command_key VARCHAR(80) NOT NULL,
  PRIMARY KEY (tenant_id,branch_id,product_id)
);

-- One positive receipt tops each product up to 100 available units only once.
-- Existing receipt keys make repeated application starts a no-op for stock.
INSERT INTO demo_catalog_opening_receipts(
  tenant_id,branch_id,product_id,quantity,available_after,command_key
)
SELECT i.tenant_id,i.branch_id,i.product_id,
       100-(i.quantity-i.reserved),100,
       CONCAT('local-demo-catalog-v2-',i.product_id)
FROM inventory i
JOIN demo_catalog_products d ON d.tenant_id=i.tenant_id AND d.id=i.product_id
WHERE i.quantity-i.reserved<100
  AND NOT EXISTS (
    SELECT 1 FROM inventory_receipts r
    WHERE r.tenant_id=i.tenant_id AND r.branch_id=i.branch_id
      AND r.command_key=CONCAT('local-demo-catalog-v2-',i.product_id)
  );

UPDATE inventory i
JOIN demo_catalog_opening_receipts r
  ON r.tenant_id=i.tenant_id AND r.branch_id=i.branch_id AND r.product_id=i.product_id
SET i.quantity=i.quantity+r.quantity;

INSERT INTO inventory_receipts(
  id,tenant_id,branch_id,product_id,quantity,actor,reason,
  command_key,available_after,created_at
)
SELECT UUID(),r.tenant_id,r.branch_id,r.product_id,r.quantity,'local-demo-seed',
       'Opening stock for local demo solar catalogue',
       r.command_key,r.available_after,CURRENT_TIMESTAMP
FROM demo_catalog_opening_receipts r;

INSERT INTO catalog_stock(tenant_id,branch_id,product_id,available)
SELECT i.tenant_id,i.branch_id,i.product_id,i.quantity-i.reserved
FROM inventory i
JOIN demo_catalog_products d ON d.tenant_id=i.tenant_id AND d.id=i.product_id
WHERE NOT EXISTS (
  SELECT 1 FROM catalog_stock s
  WHERE s.tenant_id=i.tenant_id AND s.branch_id=i.branch_id AND s.product_id=i.product_id
);

UPDATE catalog_stock s
JOIN inventory i
  ON s.tenant_id=i.tenant_id AND s.branch_id=i.branch_id AND s.product_id=i.product_id
JOIN demo_catalog_products d ON d.tenant_id=i.tenant_id AND d.id=i.product_id
SET s.available=i.quantity-i.reserved;

UPDATE catalog_sequences
SET next_id=GREATEST(next_id,950000)
WHERE name IN ('CATEGORY','PRODUCT');

DROP TEMPORARY TABLE demo_catalog_opening_receipts;
DROP TEMPORARY TABLE demo_catalog_products;
DROP TEMPORARY TABLE demo_catalog_categories;
DROP TEMPORARY TABLE demo_catalog_check;

COMMIT;
