-- Optional existing local demo upgrade after Flyway V8. Never use as production provisioning.
USE solar;
UPDATE tenants t SET online_branch_id = 900001
WHERE t.id = 900001 AND EXISTS (
    SELECT 1 FROM branches b WHERE b.tenant_id = t.id AND b.id = 900001
);
INSERT INTO employee_permissions (username, permission)
SELECT e.username, 'POS_SALE_CANCEL' FROM employees e
WHERE e.username = 'local-cashier' AND e.tenant_id = 900001 AND e.branch_id = 900001
AND NOT EXISTS (SELECT 1 FROM employee_permissions p
    WHERE p.username = e.username AND p.permission = 'POS_SALE_CANCEL');
