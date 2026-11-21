-- Local demo identities receive shared tenant-owned standard roles.
CREATE TEMPORARY TABLE demo_standard_role_permissions(role_name VARCHAR(40) NOT NULL,permission VARCHAR(80) NOT NULL,PRIMARY KEY(role_name,permission));
INSERT INTO demo_standard_role_permissions VALUES
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
('POS_CASHIER','POS_SALE_CREATE'),('POS_CASHIER','POS_TENDER_CASH_RECORD'),('POS_CASHIER','POS_TENDER_EXTERNAL_RECORD'),('POS_CASHIER','POS_RECEIPT_VIEW'),
('SALES_MANAGER','POS_VIEW'),('SALES_MANAGER','POS_REPORT_VIEW'),
('INVENTORY_MANAGER','PRODUCT_VIEW'),('INVENTORY_MANAGER','INVENTORY_VIEW'),('INVENTORY_MANAGER','INVENTORY_RECEIVE'),
('ACCOUNTANT','POS_VIEW'),('ACCOUNTANT','POS_REPORT_VIEW'),('ACCOUNTANT','POS_REFUND_VIEW');
INSERT INTO vendor_roles(tenant_id,role_key,display_name,active,created_by,updated_by)
SELECT DISTINCT e.tenant_id,CONCAT('standard:',p.role_name),p.role_name,TRUE,admin.username,admin.username
FROM employees e CROSS JOIN (SELECT DISTINCT role_name FROM demo_standard_role_permissions) p
JOIN employees admin ON admin.tenant_id=e.tenant_id AND admin.username LIKE 'admin@%'
WHERE NOT EXISTS(SELECT 1 FROM vendor_roles r WHERE r.tenant_id=e.tenant_id AND r.role_key=CONCAT('standard:',p.role_name));
INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by)
SELECT r.tenant_id,r.id,p.permission,admin.username
FROM vendor_roles r JOIN demo_standard_role_permissions p ON r.role_key=CONCAT('standard:',p.role_name)
JOIN employees admin ON admin.tenant_id=r.tenant_id AND admin.username LIKE 'admin@%'
WHERE NOT EXISTS(SELECT 1 FROM vendor_role_permissions rp WHERE rp.tenant_id=r.tenant_id AND rp.role_id=r.id AND rp.permission=p.permission);
INSERT INTO employee_role_assignments(tenant_id,branch_id,username,role_id,active,assigned_by)
SELECT e.tenant_id,e.branch_id,e.username,r.id,TRUE,admin.username
FROM employees e JOIN vendor_roles r ON r.tenant_id=e.tenant_id AND r.role_key=CONCAT('standard:',CASE
 WHEN e.username LIKE 'admin@%' THEN 'VENDOR_ADMIN' WHEN e.username LIKE 'sales@%' THEN 'SALES_MANAGER'
 WHEN e.username LIKE 'inventory@%' THEN 'INVENTORY_MANAGER' WHEN e.username LIKE 'cashier@%' THEN 'POS_CASHIER'
 WHEN e.username LIKE 'finance@%' THEN 'ACCOUNTANT' ELSE '' END)
JOIN employees admin ON admin.tenant_id=e.tenant_id AND admin.username LIKE 'admin@%'
WHERE (e.username LIKE 'admin@%' OR e.username LIKE 'sales@%' OR e.username LIKE 'inventory@%' OR e.username LIKE 'cashier@%' OR e.username LIKE 'finance@%')
AND NOT EXISTS(SELECT 1 FROM employee_role_assignments a WHERE a.tenant_id=e.tenant_id AND a.username=e.username AND a.role_id=r.id);
INSERT INTO employee_register_assignments(tenant_id,branch_id,username,register_id,active,assigned_by)
SELECT e.tenant_id,e.branch_id,e.username,MIN(reg.id),TRUE,admin.username
FROM employees e JOIN employee_role_assignments a ON a.tenant_id=e.tenant_id AND a.username=e.username AND a.active=TRUE
JOIN vendor_roles r ON r.tenant_id=a.tenant_id AND r.id=a.role_id AND r.active=TRUE
JOIN vendor_role_permissions rp ON rp.tenant_id=r.tenant_id AND rp.role_id=r.id AND rp.permission='POS_SESSION_OPEN'
JOIN registers reg ON reg.tenant_id=e.tenant_id AND reg.branch_id=e.branch_id
JOIN employees admin ON admin.tenant_id=e.tenant_id AND admin.username LIKE 'admin@%'
WHERE e.username LIKE 'cashier@%' AND NOT EXISTS(SELECT 1 FROM employee_register_assignments x WHERE x.tenant_id=e.tenant_id AND x.username=e.username)
GROUP BY e.tenant_id,e.branch_id,e.username,admin.username;
DROP TEMPORARY TABLE demo_standard_role_permissions;
