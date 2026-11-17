-- OPTIONAL LOCAL DEMO ONLY after V12, demo supervisor and enable-pos-return-intake.sql.
-- Does not create money, change passwords, reset stock or pay a refund.
USE solar;
UPDATE branches SET cash_refunds_enabled=TRUE WHERE id=900001 AND tenant_id=900001;
INSERT INTO registers(id,tenant_id,branch_id)
SELECT 900002,900001,900001 FROM branches WHERE id=900001 AND tenant_id=900001
AND NOT EXISTS(SELECT 1 FROM registers WHERE id=900002);
INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by)
SELECT 900001,r.id,p.permission,'local-supervisor'
FROM vendor_roles r
CROSS JOIN (SELECT 'POS_VIEW' permission UNION ALL SELECT 'POS_REFUND_VIEW'
 UNION ALL SELECT 'POS_REFUND_APPROVE' UNION ALL SELECT 'POS_REFUND_CASH_RECORD'
 UNION ALL SELECT 'POS_REPORT_VIEW' UNION ALL SELECT 'POS_REFUND_PRINT'
 UNION ALL SELECT 'POS_SESSION_OPEN' UNION ALL SELECT 'POS_SESSION_CLOSE') p
WHERE r.tenant_id=900001 AND r.role_key='local-supervisor'
AND NOT EXISTS(SELECT 1 FROM vendor_role_permissions x WHERE x.tenant_id=r.tenant_id AND x.role_id=r.id AND x.permission=p.permission);
INSERT INTO employee_register_assignments(tenant_id,branch_id,username,register_id,active,assigned_by)
SELECT 900001,900001,'local-supervisor',900002,TRUE,'local-supervisor'
WHERE NOT EXISTS(SELECT 1 FROM employee_register_assignments WHERE tenant_id=900001 AND username='local-supervisor' AND register_id=900002);
