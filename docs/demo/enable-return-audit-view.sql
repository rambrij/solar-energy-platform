-- OPTIONAL LOCAL DEMO after named supervisor setup; read-only view rights only.
USE solar;
INSERT INTO employee_permissions(username,permission)
SELECT e.username,p.permission FROM employees e
CROSS JOIN(SELECT 'POS_VIEW' AS permission UNION ALL SELECT 'POS_RETURN_VIEW' UNION ALL SELECT 'POS_RETURN_AUDIT_VIEW') p
WHERE e.username='local-supervisor' AND e.tenant_id=900001 AND e.branch_id=900001 AND e.enabled=TRUE
AND NOT EXISTS(SELECT 1 FROM employee_permissions g WHERE g.username=e.username AND g.permission=p.permission);
-- No approval/inspection/payout/print/export grants, cash policy, credentials or stock changes.
