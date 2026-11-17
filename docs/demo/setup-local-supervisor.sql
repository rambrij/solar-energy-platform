-- Optional LOCAL demonstration only, after pos-local-demo-setup.sql.
-- Login: local-supervisor / LocalSupervisor2026!
-- Safe to repeat: does not reset an existing password or grant cashier permissions.
USE solar;
INSERT INTO users(id,username,password_hash,enabled)
SELECT 900003,'local-supervisor','$2a$10$OHCFHlJucaUXl7alduReS.DzvA5uz.NxwhkDYWptgYOXOLS7MNSWu',TRUE
WHERE EXISTS(SELECT 1 FROM branches WHERE tenant_id=900001 AND id=900001)
 AND NOT EXISTS(SELECT 1 FROM users WHERE id=900003 OR username='local-supervisor');
INSERT INTO employees(username,tenant_id,branch_id,enabled)
SELECT 'local-supervisor',900001,900001,TRUE
WHERE EXISTS(SELECT 1 FROM users WHERE username='local-supervisor')
 AND NOT EXISTS(SELECT 1 FROM employees WHERE username='local-supervisor');
UPDATE identity_sequence SET next_id=GREATEST(next_id,900004) WHERE id=1;

INSERT INTO vendor_roles(tenant_id,role_key,display_name,active,created_by,updated_by)
SELECT 900001,'local-supervisor','Local Supervisor',TRUE,'local-supervisor','local-supervisor'
WHERE NOT EXISTS(SELECT 1 FROM vendor_roles WHERE tenant_id=900001 AND role_key='local-supervisor');

INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by)
SELECT 900001,r.id,p.permission,'local-supervisor'
FROM vendor_roles r CROSS JOIN (SELECT 'POS_VIEW' permission UNION ALL SELECT 'POS_SHIFT_VARIANCE_APPROVE') p
WHERE r.tenant_id=900001 AND r.role_key='local-supervisor'
AND NOT EXISTS(SELECT 1 FROM vendor_role_permissions x WHERE x.tenant_id=r.tenant_id AND x.role_id=r.id AND x.permission=p.permission);

INSERT INTO employee_role_assignments(tenant_id,branch_id,username,role_id,active,assigned_by)
SELECT 900001,900001,'local-supervisor',r.id,TRUE,'local-supervisor'
FROM vendor_roles r WHERE r.tenant_id=900001 AND r.role_key='local-supervisor'
AND NOT EXISTS(SELECT 1 FROM employee_role_assignments a WHERE a.tenant_id=900001 AND a.username='local-supervisor' AND a.role_id=r.id);

INSERT INTO employee_register_assignments(tenant_id,branch_id,username,register_id,active,assigned_by)
SELECT 900001,900001,'local-supervisor',900001,TRUE,'local-supervisor'
WHERE EXISTS(SELECT 1 FROM registers WHERE tenant_id=900001 AND branch_id=900001 AND id=900001)
AND NOT EXISTS(SELECT 1 FROM employee_register_assignments WHERE tenant_id=900001 AND username='local-supervisor' AND register_id=900001);
