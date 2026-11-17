-- Optional LOCAL stock-return demonstration after Flyway V11 and named demo staff setup.
-- Explicitly enables a 30 x 24-hour return window only for demo branch 900001 if currently disabled.
-- Preserves nonzero custom windows; no sale, drawer balance, stock, password or refund is changed.
USE solar;
UPDATE branches b SET pos_return_window_days=30
WHERE b.id=900001 AND b.tenant_id=900001 AND b.pos_return_window_days=0
AND EXISTS(SELECT 1 FROM tenants t WHERE t.id=b.tenant_id AND t.active=TRUE);
INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by)
SELECT 900001,r.id,p.permission,'local-cashier'
FROM vendor_roles r
CROSS JOIN (SELECT 'POS_RETURN_VIEW' permission UNION ALL SELECT 'POS_RETURN_CREATE') p
WHERE r.tenant_id=900001 AND r.role_key='local-cashier'
AND NOT EXISTS(SELECT 1 FROM vendor_role_permissions x WHERE x.tenant_id=r.tenant_id AND x.role_id=r.id AND x.permission=p.permission);
INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by)
SELECT 900001,r.id,p.permission,'local-supervisor'
FROM vendor_roles r
CROSS JOIN (SELECT 'POS_RETURN_VIEW' permission UNION ALL SELECT 'POS_RETURN_APPROVE' UNION ALL SELECT 'POS_RETURN_INSPECT') p
WHERE r.tenant_id=900001 AND r.role_key='local-supervisor'
AND NOT EXISTS(SELECT 1 FROM vendor_role_permissions x WHERE x.tenant_id=r.tenant_id AND x.role_id=r.id AND x.permission=p.permission);
