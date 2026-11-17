-- Optional local demo only. Run after the named demo staff accounts exist.
-- Grants read-only reporting to staff assigned to this demo tenant/branch; changes no balances or passwords.
USE solar;
INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by)
SELECT 900001,r.id,p.permission,r.created_by
FROM vendor_roles r
CROSS JOIN (SELECT 'POS_REPORT_VIEW' permission) p
WHERE r.tenant_id=900001 AND r.role_key IN ('local-cashier','local-supervisor')
AND NOT EXISTS(SELECT 1 FROM vendor_role_permissions x WHERE x.tenant_id=r.tenant_id AND x.role_id=r.id AND x.permission=p.permission);
