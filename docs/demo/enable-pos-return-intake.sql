-- Optional LOCAL stock-return demonstration after Flyway V11 and named demo staff setup.
-- Explicitly enables a 30 x 24-hour return window only for demo branch 900001 if currently disabled.
-- Preserves nonzero custom windows; no sale, drawer balance, stock, password or refund is changed.
USE solar;
UPDATE branches b SET pos_return_window_days=30
WHERE b.id=900001 AND b.tenant_id=900001 AND b.pos_return_window_days=0
AND EXISTS(SELECT 1 FROM tenants t WHERE t.id=b.tenant_id AND t.active=TRUE);
INSERT INTO employee_permissions(username,permission)
SELECT e.username,p.permission FROM employees e
CROSS JOIN(SELECT 'POS_RETURN_VIEW' AS permission UNION ALL SELECT 'POS_RETURN_CREATE') p
WHERE e.username='local-cashier' AND e.tenant_id=900001 AND e.branch_id=900001
AND NOT EXISTS(SELECT 1 FROM employee_permissions g WHERE g.username=e.username AND g.permission=p.permission);
INSERT INTO employee_permissions(username,permission)
SELECT e.username,p.permission FROM employees e
CROSS JOIN(SELECT 'POS_RETURN_VIEW' AS permission UNION ALL SELECT 'POS_RETURN_APPROVE' UNION ALL SELECT 'POS_RETURN_INSPECT') p
WHERE e.username='local-supervisor' AND e.tenant_id=900001 AND e.branch_id=900001
AND NOT EXISTS(SELECT 1 FROM employee_permissions g WHERE g.username=e.username AND g.permission=p.permission);
