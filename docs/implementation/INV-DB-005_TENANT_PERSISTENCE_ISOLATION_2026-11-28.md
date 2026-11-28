# INV-DB-005 — Tenant persistence isolation matrix

Date: 28 November 2026  
Branch: \`refactor/development-baseline-cleanup\`

## Status

**IN PROGRESS — initial critical-family persistence matrix added.** This increment adds real database writes and negative ownership probes to the existing reusable two-vendor/two-customer \`TenantIsolationFixture\`. It does not add a production migration or alter application behavior.

## Coverage added

The \`P004TenantIsolationTest.criticalTenantChildFamiliesEnforceDatabaseOwnershipInBothDirections\` test runs in the existing H2 and MySQL 8.4 backend lanes.

| Resource family | Positive controls | Rejected writes |
|---|---|---|
| Product tenant-scoped SKU | Same SKU is accepted in another tenant | Duplicate SKU inside the same tenant |
| Cart items | Each vendor writes an item to its own cart and product | Foreign-tenant product, foreign-tenant cart, missing cart, null tenant, duplicate tenant/cart/product key |
| Order items | Each vendor writes an item to its own online order and product | Foreign-tenant product, foreign-tenant order, missing order, null tenant, duplicate tenant/order/product key |
| Warehouse inventory | Each vendor records stock for its own warehouse and product | Foreign-tenant warehouse, foreign-tenant product, missing warehouse, null tenant, duplicate tenant/warehouse/product key |

The test confirms that rejected statements do not create partial child rows and that the two vendors' pre-existing branch inventory remains unchanged.

## Existing coverage reused

- \`P004TenantIsolationTest\` already exercises tenant-scoped application behavior, global customer identity ownership, vendor/customer relationships, inventory cross-tenant probes and same-tenant wrong-branch denial.
- \`P202MysqlPersistenceIntegrationTest\` independently checks the tenant-owned table census, \`tenant_id NOT NULL\` across the classified tenant tables, composite FK metadata and ordered unique parent keys. It also contains data-backed cross-tenant probes for role assignments and subscription billing relationships.

## Boundaries and remaining work

This increment is not an exhaustive DML test for every tenant-owned table. The remaining table-family matrix must be expanded for stock reservations, receipts, returns/refunds, POS projections, shipment/serial children, service/warranty children and remaining operational resources, using valid parent fixtures before each negative probe.

No migration was added: the new cases target existing constraints. A production constraint should be introduced only if a reproducible missing or incorrect ownership relationship is found and its semantics are supported by the Version 2.0 requirements.

## Acceptance evidence

The test source is committed on the working branch. H2, MySQL 8.4 and full workflow results must be checked on the exact commit before this increment can be reported as CI-verified.
