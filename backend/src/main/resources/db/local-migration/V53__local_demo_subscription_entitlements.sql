-- Local profile only: all vendor demo users must exercise the same entitlement
-- resolver as production. Never use profile-specific authorization bypasses.
-- The local accounts are provisioned by V51 before this migration runs.

INSERT INTO subscription_plans(plan_key,display_name,active,current_version)
SELECT 'local-demo-unlimited','Local Demo Unlimited',TRUE,1
WHERE NOT EXISTS (
    SELECT 1 FROM subscription_plans WHERE plan_key='local-demo-unlimited'
);

INSERT INTO subscription_plan_versions(
    plan_id,version,price,currency,billing_interval,max_users,max_storage_mb,
    max_branches,max_warehouses,entitlements,grace_days,tax_metadata,
    invoice_description,created_by
)
SELECT p.id,1,0,'INR','MONTHLY',1000,100000,100,100,
    '{"features":{"POS":true,"CATALOG":true,"INVENTORY":true,"ADMINISTRATION":true,"CRM":true,"LEADS":true,"QUOTATIONS":true,"ORDERS":true,"RETURNS":true,"FINANCE":true,"SHIPMENTS":true,"SERVICE":true,"REPORTING":true}}',
    30,'{}','Local development demo subscription','saumya.admin@somya.local'
FROM subscription_plans p
WHERE p.plan_key='local-demo-unlimited'
  AND NOT EXISTS (
      SELECT 1 FROM subscription_plan_versions v
      WHERE v.plan_id=p.id AND v.version=1
  );

INSERT INTO tenant_subscriptions(
    tenant_id,plan_id,plan_version,status,billing_period_start,
    billing_period_end,terms_snapshot
)
SELECT d.tenant_id,p.id,1,'ACTIVE',DATE '2026-01-01',DATE '2099-12-31',
       '{"local_demo":true,"entitlements":"all_demo_features"}'
FROM (
    SELECT 910001 AS tenant_id
    UNION ALL SELECT 920001
    UNION ALL SELECT 930001
) d
JOIN subscription_plans p ON p.plan_key='local-demo-unlimited'
WHERE EXISTS (
    SELECT 1 FROM tenants t WHERE t.id=d.tenant_id
)
AND NOT EXISTS (
    SELECT 1 FROM tenant_subscriptions s WHERE s.tenant_id=d.tenant_id
);
