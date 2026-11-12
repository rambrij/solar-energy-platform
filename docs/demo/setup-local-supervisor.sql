-- Optional LOCAL demonstration only, after pos-local-demo-setup.sql.
-- Login: local-supervisor / LocalSupervisor2026!
-- Safe to repeat: does not reset an existing password or grant cashier permissions.
USE solar;
INSERT INTO employees(username,password_hash,tenant_id,branch_id,enabled)
SELECT 'local-supervisor','$2a$10$OHCFHlJucaUXl7alduReS.DzvA5uz.NxwhkDYWptgYOXOLS7MNSWu',900001,900001,TRUE
WHERE EXISTS(SELECT 1 FROM branches WHERE tenant_id=900001 AND id=900001)
  AND NOT EXISTS(SELECT 1 FROM employees WHERE username='local-supervisor');

INSERT INTO employee_permissions(username,permission)
SELECT e.username,p.permission FROM employees e
CROSS JOIN(SELECT 'POS_VIEW' AS permission UNION ALL SELECT 'POS_SHIFT_VARIANCE_APPROVE') p
WHERE e.username='local-supervisor' AND e.tenant_id=900001 AND e.branch_id=900001
  AND NOT EXISTS(SELECT 1 FROM employee_permissions g WHERE g.username=e.username AND g.permission=p.permission);
