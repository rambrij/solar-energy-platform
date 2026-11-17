-- Run once in MySQL Workbench against your LOCAL solar database.
-- Creates demo data only; does not overwrite existing records.
-- Login: local-cashier / LocalCashier2026!
-- Register: 900001; opening float: the actual cash in your drawer (or 0.00).
USE solar;

DELIMITER $$
CREATE PROCEDURE setup_pos_local_demo_900001()
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;
    INSERT INTO tenants (id, name, active, slug)
    VALUES (900001, 'Local Demo Solar Store', TRUE, 'vendor-900001');

    INSERT INTO branches (id, tenant_id, name, invoice_required)
    VALUES (900001, 900001, 'Local Demo Outlet', FALSE);

    INSERT INTO users(id,username,password_hash,enabled) VALUES(900002,'local-cashier','$2a$10$RBxhLWRTjYNiuArGbCGYb.gUH9NNUwBY7usYQChMysjVQ0vdlKOuC',TRUE);
    INSERT INTO employees(username,tenant_id,branch_id,enabled) VALUES('local-cashier',900001,900001,TRUE);
    UPDATE identity_sequence SET next_id=GREATEST(next_id,900003) WHERE id=1;

    INSERT INTO vendor_roles(tenant_id,role_key,display_name,active,created_by,updated_by)
    VALUES(900001,'local-cashier','Local Cashier',TRUE,'local-cashier','local-cashier');

    INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by)
    SELECT 900001,id,p.permission,'local-cashier'
    FROM vendor_roles
    JOIN (SELECT 'POS_VIEW' permission UNION ALL SELECT 'POS_SESSION_OPEN' UNION ALL SELECT 'POS_SESSION_CLOSE'
          UNION ALL SELECT 'POS_SALE_CREATE' UNION ALL SELECT 'POS_TENDER_CASH_RECORD' UNION ALL SELECT 'POS_RECEIPT_VIEW'
          UNION ALL SELECT 'POS_RECEIPT_REPRINT' UNION ALL SELECT 'POS_CASH_IN' UNION ALL SELECT 'POS_CASH_OUT'
          UNION ALL SELECT 'POS_SAFE_DROP') p
    WHERE tenant_id=900001 AND role_key='local-cashier';

    INSERT INTO employee_role_assignments(tenant_id,branch_id,username,role_id,active,assigned_by)
    SELECT 900001,900001,'local-cashier',id,TRUE,'local-cashier'
    FROM vendor_roles WHERE tenant_id=900001 AND role_key='local-cashier';

    INSERT INTO registers (id, tenant_id, branch_id)
    VALUES (900001, 900001, 900001);

    INSERT INTO employee_register_assignments(tenant_id,branch_id,username,register_id,active,assigned_by)
    VALUES(900001,900001,'local-cashier',900001,TRUE,'local-cashier');

    UPDATE retail_sequences SET next_id=GREATEST(next_id,900002) WHERE name='REGISTER';

    -- Demonstration price/tax only, not a production tax configuration.
    INSERT INTO products
        (id, tenant_id, sku, barcode, name, price, tax_rate, published)
    VALUES
        (900001, 900001, 'DEMO-PANEL', '900001', 'Demo Solar Panel', 100.00, 0.00, TRUE);

    INSERT INTO inventory (tenant_id, branch_id, product_id, quantity)
    VALUES (900001, 900001, 900001, 10);
    COMMIT;
END$$
DELIMITER ;

CALL setup_pos_local_demo_900001();
DROP PROCEDURE setup_pos_local_demo_900001;

SELECT id AS register_id, tenant_id, branch_id
FROM registers WHERE id = 900001;

-- Local fixture synchronization; live stock changes publish this projection transactionally.
INSERT INTO catalog_stock(tenant_id,branch_id,product_id,available) SELECT tenant_id,branch_id,product_id,quantity-reserved FROM inventory WHERE tenant_id=900001 ON DUPLICATE KEY UPDATE available=VALUES(available);
