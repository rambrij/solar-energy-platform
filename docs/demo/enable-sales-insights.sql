-- Optional local demo only. Run after the named demo staff accounts exist.
-- Grants read-only reporting to staff assigned to this demo tenant/branch; changes no balances or passwords.
USE solar;
INSERT INTO employee_permissions(username,permission)
SELECT e.username,'POS_REPORT_VIEW' FROM employees e
WHERE e.username IN ('local-cashier','local-supervisor') AND e.tenant_id=900001 AND e.branch_id=900001
AND NOT EXISTS(SELECT 1 FROM employee_permissions p WHERE p.username=e.username AND p.permission='POS_REPORT_VIEW');
