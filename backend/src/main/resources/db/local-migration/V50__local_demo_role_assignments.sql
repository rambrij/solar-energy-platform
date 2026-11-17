-- Local-only follow-up to V39. V39 stages demo grants before V45 creates RBAC tables.
-- V49 completes the role schema; this migration creates local demo tenant roles.
INSERT INTO vendor_roles(tenant_id,role_key,display_name,active,created_by,updated_by)
SELECT DISTINCT g.tenant_id,CONCAT('demo-',REPLACE(g.username,'@','-')),g.role_name,TRUE,g.username,g.username
FROM local_demo_role_permission_seed g
WHERE NOT EXISTS(
  SELECT 1 FROM employee_role_assignments a WHERE a.tenant_id=g.tenant_id AND a.username=g.username
)
AND NOT EXISTS(
  SELECT 1 FROM vendor_roles r
  WHERE r.tenant_id=g.tenant_id AND r.role_key=CONCAT('demo-',REPLACE(g.username,'@','-'))
);

INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by)
SELECT g.tenant_id,r.id,g.permission,g.username
FROM local_demo_role_permission_seed g
JOIN vendor_roles r ON r.tenant_id=g.tenant_id AND r.role_key=CONCAT('demo-',REPLACE(g.username,'@','-'))
WHERE NOT EXISTS(
  SELECT 1 FROM employee_role_assignments a WHERE a.tenant_id=g.tenant_id AND a.username=g.username
)
AND NOT EXISTS(
  SELECT 1 FROM vendor_role_permissions p
  WHERE p.tenant_id=g.tenant_id AND p.role_id=r.id AND p.permission=g.permission
);

INSERT INTO employee_role_assignments(tenant_id,branch_id,username,role_id,active,assigned_by)
SELECT e.tenant_id,e.branch_id,e.username,r.id,TRUE,e.username
FROM employees e
JOIN vendor_roles r ON r.tenant_id=e.tenant_id AND r.role_key=CONCAT('demo-',REPLACE(e.username,'@','-'))
WHERE EXISTS(
  SELECT 1 FROM local_demo_role_permission_seed g
  WHERE g.tenant_id=e.tenant_id AND g.username=e.username
)
AND NOT EXISTS(
  SELECT 1 FROM employee_role_assignments a
  WHERE a.tenant_id=e.tenant_id AND a.username=e.username
);

INSERT INTO employee_register_assignments(tenant_id,branch_id,username,register_id,active,assigned_by)
SELECT e.tenant_id,e.branch_id,e.username,MIN(reg.id),TRUE,e.username
FROM employees e
JOIN employee_role_assignments a ON a.tenant_id=e.tenant_id AND a.username=e.username AND a.active=TRUE
JOIN vendor_roles r ON r.tenant_id=a.tenant_id AND r.id=a.role_id AND r.active=TRUE
JOIN vendor_role_permissions rp ON rp.tenant_id=r.tenant_id AND rp.role_id=r.id AND rp.permission='POS_SESSION_OPEN'
JOIN registers reg ON reg.tenant_id=e.tenant_id AND reg.branch_id=e.branch_id
WHERE e.username LIKE 'cashier@%'
AND EXISTS(SELECT 1 FROM local_demo_role_permission_seed g WHERE g.tenant_id=e.tenant_id AND g.username=e.username)
AND NOT EXISTS(SELECT 1 FROM employee_register_assignments existing WHERE existing.tenant_id=e.tenant_id AND existing.username=e.username)
GROUP BY e.tenant_id,e.branch_id,e.username;

DROP TABLE local_demo_role_permission_seed;
