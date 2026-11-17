-- OPTIONAL LOCAL DEMO after named supervisor setup; read-only view rights only.
USE solar;
INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by)
SELECT 900001,r.id,p.permission,'local-supervisor'
FROM vendor_roles r
CROSS JOIN (SELECT 'POS_VIEW' permission UNION ALL SELECT 'POS_RETURN_VIEW' UNION ALL SELECT 'POS_RETURN_AUDIT_VIEW') p
WHERE r.tenant_id=900001 AND r.role_key='local-supervisor'
AND NOT EXISTS(SELECT 1 FROM vendor_role_permissions x WHERE x.tenant_id=r.tenant_id AND x.role_id=r.id AND x.permission=p.permission);
-- No approval/inspection/payout/print/export grants, cash policy, credentials or stock changes.
