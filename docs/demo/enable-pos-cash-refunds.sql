-- OPTIONAL LOCAL DEMO ONLY after V12, demo supervisor and enable-pos-return-intake.sql.
-- Does not create money, change passwords, reset stock or pay a refund.
USE solar;
UPDATE branches SET cash_refunds_enabled=TRUE WHERE id=900001 AND tenant_id=900001;
INSERT INTO registers(id,tenant_id,branch_id)
SELECT 900002,900001,900001 FROM branches WHERE id=900001 AND tenant_id=900001
AND NOT EXISTS(SELECT 1 FROM registers WHERE id=900002);
INSERT INTO employee_permissions(username,permission)
SELECT e.username,p.permission FROM employees e
CROSS JOIN(SELECT 'POS_VIEW' AS permission UNION ALL SELECT 'POS_REFUND_VIEW'
UNION ALL SELECT 'POS_REFUND_APPROVE' UNION ALL SELECT 'POS_REFUND_CASH_RECORD'
UNION ALL SELECT 'POS_REPORT_VIEW' UNION ALL SELECT 'POS_REFUND_PRINT' UNION ALL SELECT 'POS_SESSION_OPEN' UNION ALL SELECT 'POS_SESSION_CLOSE') p
WHERE e.username='local-supervisor' AND e.tenant_id=900001 AND e.branch_id=900001
AND NOT EXISTS(SELECT 1 FROM employee_permissions g WHERE g.username=e.username AND g.permission=p.permission);
