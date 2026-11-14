-- OPTIONAL LOCAL DEMO after Flyway V13 and named demo supervisor setup.
-- Grants report/export access only. No cash policy, register, password, sale or stock changes.
USE solar;
INSERT INTO employee_permissions(username,permission)
SELECT e.username,p.permission FROM employees e
CROSS JOIN(SELECT 'POS_VIEW' AS permission UNION ALL SELECT 'POS_REPORT_VIEW'
UNION ALL SELECT 'POS_REPORT_EXPORT' UNION ALL SELECT 'POS_REFUND_VIEW') p
WHERE e.username='local-supervisor' AND e.tenant_id=900001 AND e.branch_id=900001 AND e.enabled=TRUE
AND NOT EXISTS(SELECT 1 FROM employee_permissions g WHERE g.username=e.username AND g.permission=p.permission);
