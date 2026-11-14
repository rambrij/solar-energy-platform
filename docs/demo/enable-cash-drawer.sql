-- Optional local demo upgrade. Run after the demo cashier already exists.
-- Does not recreate the store, reset inventory or change a password.
USE solar;
INSERT INTO employee_permissions (username, permission)
SELECT e.username, p.permission
FROM employees e
CROSS JOIN (
    SELECT 'POS_CASH_IN' AS permission
    UNION ALL SELECT 'POS_CASH_OUT'
    UNION ALL SELECT 'POS_SAFE_DROP'
) p
WHERE e.username = 'local-cashier'
  AND e.tenant_id = 900001 AND e.branch_id = 900001
  AND NOT EXISTS (
      SELECT 1 FROM employee_permissions granted
      WHERE granted.username = e.username AND granted.permission = p.permission
  );
