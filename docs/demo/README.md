# Local demo accounts, tenant roles and permissions

For onboarding, subscriptions, support, MFA, Mailpit, and mock payment steps, see the [Platform Super Admin demo walkthrough](platform-super-admin.md).

> **Local/test only.** Fixture credentials are public development credentials. Never use them in production or expose the local environment publicly.

## Provisioning model

The local Spring profile runs a Flyway callback after V1 is applied. The callback creates the three tenant fixtures and current demo identities, materializes the five supported version-1 standard roles per tenant, and explicitly assigns employees to their persisted roles.

Authorization resolves active employee_role_assignments joined to active tenant-owned vendor_roles and vendor_role_permissions, then applies tenant entitlements and branch/register/session restrictions. Platform permissions remain in the separate global platform-authority store and are never inserted into vendor roles.

Only these standard roles currently have implemented permission sets and receive assignments:

- VENDOR_ADMIN
- POS_CASHIER
- SALES_MANAGER
- INVENTORY_MANAGER
- ACCOUNTANT

The following employee identities are deny-by-default fixtures. Their templates are deferred because the corresponding assignment-scoped feature APIs are not implemented. They receive no role assignment and no effective vendor permissions:

- SALES_EXECUTIVE
- OPERATIONS_MANAGER
- SERVICE_MANAGER
- SERVICE_ENGINEER
- CONTENT_MANAGER

## Fresh local setup

Follow [Clean Development Database Setup](../implementation/CLEAN_DEVELOPMENT_DATABASE.md). The procedure resets the disposable Compose MySQL volume before the clean Flyway install. With `SPRING_PROFILES_ACTIVE=local`, the demo callback runs automatically after Flyway applies V1.

## Platform account

| Scope | Role | Login | Password |
|---|---|---|---|
| Global; no tenant or vendor role | PLATFORM_SUPER_ADMIN | platform.superadmin@somya.local | Somya-Platform-2026! |

The platform identity receives platform permissions only through platform_user_permissions. It has no vendor employee membership, vendor role, commercial catalogue, POS, inventory or customer-order access. No permanent Platform Support login is seeded.

On a fresh local V1 install, this login can open the tenant directory and lifecycle controls, subscription plans and tenant subscriptions, platform health and audit, support access, global configuration, and platform identities. The configuration screen stores non-sensitive values and external secret references. Provider execution and email delivery depend on the integrations implemented and configured for the deployment.

In **Subscription plans**, select **Revise plan** to start from the current version and save new terms. Prior versions remain visible in the plan catalogue. In **Support access**, select **Revoke** on an active grant and enter the reason to record the revocation in the platform audit.

**Overview → Subscriptions by status** shows counts across platform subscription records, including historical statuses when present. `GET /api/v1/platform/dashboard` returns these as `subscriptionStatuses` entries with `status` and `count`. The existing `PLATFORM_ANALYTICS_VIEW` grant is required. The totals contain no tenant, invoice, or payment details.

In **Configuration**, use **Email templates** to save a plain-text subject and body, or choose **Edit template** beside a saved template. Template editing does not send email. Use **Payment providers** to record vendor and subscription provider names separately and to store a reference for the platform subscription credential. The API redacts the credential reference on reads. These settings do not enable a gateway or change the checkout adapter. The vendor mock adapter is controlled by the existing local/test deployment flag.

If a disposable local database was seeded before the tenant-view, configuration, and identity grants were added, use the clean database reset above. The seed callback applies when Flyway installs V1 and does not rewrite permissions at every restart.

## Vendor employee credentials

| Vendor | Role | Login | Password |
|---|---|---|---|
| Saumya Solar Rooftops | VENDOR_ADMIN | saumya.admin@somya.local | Somya-Saumya-ADM-2026! |
| Saumya Solar Rooftops | SALES_MANAGER | saumya.sales.manager@somya.local | Somya-Saumya-SM-2026! |
| Saumya Solar Rooftops | SALES_EXECUTIVE (deferred; no role assignment) | saumya.sales.executive@somya.local | Somya-Saumya-SE-2026! |
| Saumya Solar Rooftops | INVENTORY_MANAGER | saumya.inventory.manager@somya.local | Somya-Saumya-IM-2026! |
| Saumya Solar Rooftops | POS_CASHIER | saumya.pos.cashier@somya.local | Somya-Saumya-POS-2026! |
| Saumya Solar Rooftops | ACCOUNTANT | saumya.accountant@somya.local | Somya-Saumya-ACC-2026! |
| Saumya Solar Rooftops | OPERATIONS_MANAGER (deferred; no role assignment) | saumya.operations.manager@somya.local | Somya-Saumya-OPS-2026! |
| Saumya Solar Rooftops | SERVICE_MANAGER (deferred; no role assignment) | saumya.service.manager@somya.local | Somya-Saumya-SVCM-2026! |
| Saumya Solar Rooftops | SERVICE_ENGINEER (deferred; no role assignment) | saumya.service.engineer@somya.local | Somya-Saumya-SVCE-2026! |
| Saumya Solar Rooftops | CONTENT_MANAGER (deferred; no role assignment) | saumya.content.manager@somya.local | Somya-Saumya-CONTENT-2026! |
| Helio Rural Energy | VENDOR_ADMIN | helio.admin@somya.local | Somya-Helio-ADM-2026! |
| Helio Rural Energy | SALES_MANAGER | helio.sales.manager@somya.local | Somya-Helio-SM-2026! |
| Helio Rural Energy | SALES_EXECUTIVE (deferred; no role assignment) | helio.sales.executive@somya.local | Somya-Helio-SE-2026! |
| Helio Rural Energy | INVENTORY_MANAGER | helio.inventory.manager@somya.local | Somya-Helio-IM-2026! |
| Helio Rural Energy | POS_CASHIER | helio.pos.cashier@somya.local | Somya-Helio-POS-2026! |
| Helio Rural Energy | ACCOUNTANT | helio.accountant@somya.local | Somya-Helio-ACC-2026! |
| Helio Rural Energy | OPERATIONS_MANAGER (deferred; no role assignment) | helio.operations.manager@somya.local | Somya-Helio-OPS-2026! |
| Helio Rural Energy | SERVICE_MANAGER (deferred; no role assignment) | helio.service.manager@somya.local | Somya-Helio-SVCM-2026! |
| Helio Rural Energy | SERVICE_ENGINEER (deferred; no role assignment) | helio.service.engineer@somya.local | Somya-Helio-SVCE-2026! |
| Helio Rural Energy | CONTENT_MANAGER (deferred; no role assignment) | helio.content.manager@somya.local | Somya-Helio-CONTENT-2026! |
| Lumina Solar Lighting | VENDOR_ADMIN | lumina.admin@somya.local | Somya-Lumina-ADM-2026! |
| Lumina Solar Lighting | SALES_MANAGER | lumina.sales.manager@somya.local | Somya-Lumina-SM-2026! |
| Lumina Solar Lighting | SALES_EXECUTIVE (deferred; no role assignment) | lumina.sales.executive@somya.local | Somya-Lumina-SE-2026! |
| Lumina Solar Lighting | INVENTORY_MANAGER | lumina.inventory.manager@somya.local | Somya-Lumina-IM-2026! |
| Lumina Solar Lighting | POS_CASHIER | lumina.pos.cashier@somya.local | Somya-Lumina-POS-2026! |
| Lumina Solar Lighting | ACCOUNTANT | lumina.accountant@somya.local | Somya-Lumina-ACC-2026! |
| Lumina Solar Lighting | OPERATIONS_MANAGER (deferred; no role assignment) | lumina.operations.manager@somya.local | Somya-Lumina-OPS-2026! |
| Lumina Solar Lighting | SERVICE_MANAGER (deferred; no role assignment) | lumina.service.manager@somya.local | Somya-Lumina-SVCM-2026! |
| Lumina Solar Lighting | SERVICE_ENGINEER (deferred; no role assignment) | lumina.service.engineer@somya.local | Somya-Lumina-SVCE-2026! |
| Lumina Solar Lighting | CONTENT_MANAGER (deferred; no role assignment) | lumina.content.manager@somya.local | Somya-Lumina-CONTENT-2026! |

## Global customer credentials

| Role | Login | Password | Relationship fixture |
|---|---|---|---|
| CUSTOMER | customer.asha@somya.local | Somya-Asha-Buyer-2026! | Explicit-consent relationship with all three vendors |
| CUSTOMER | customer.rahul@somya.local | Somya-Rahul-Buyer-2026! | Explicit-consent relationship with Helio Rural Energy only |

Customers have one global identity and self-only transaction access. They have no employee, platform or vendor-role grants.

## Supported standard-role permission snapshots

The backend StandardRoleTemplates catalogue is the source for provisioning. Tests validate every grant against the implemented vendor permission allow-list and reject unknown, unimplemented or platform permissions.

| Template | Permissions |
|---|---|
| VENDOR_ADMIN | CATALOG_PROMOTION_MANAGE; INVENTORY_RECEIVE, INVENTORY_VIEW; POS_CASHIER_REPORT_VIEW, POS_CASH_IN, POS_CASH_OUT, POS_DISCOUNT_APPLY, POS_DISCOUNT_APPROVE, POS_DISCOUNT_OVERRIDE, POS_RECEIPT_REPRINT, POS_RECEIPT_VIEW, POS_REFUND_APPROVE, POS_REFUND_CASH_RECORD, POS_REFUND_PRINT, POS_REFUND_VIEW, POS_REPORT_EXPORT, POS_REPORT_VIEW, POS_RETURN_AUDIT_VIEW, POS_RETURN_APPROVE, POS_RETURN_CREATE, POS_RETURN_INSPECT, POS_RETURN_VIEW, POS_SAFE_DROP, POS_SALE_CANCEL, POS_SALE_CREATE, POS_SESSION_CLOSE, POS_SESSION_OPEN, POS_SESSION_REOPEN, POS_SETTINGS_MANAGE, POS_SHIFT_VARIANCE_APPROVE, POS_TENDER_CASH_RECORD, POS_TENDER_EXTERNAL_RECORD, POS_TENDER_MANUAL_VERIFY, POS_VIEW; PRICE_UPDATE, PRICE_VIEW; PRODUCT_ARCHIVE, PRODUCT_CREATE, PRODUCT_UPDATE, PRODUCT_VIEW; ROLE_ASSIGN, ROLE_CREATE, ROLE_UPDATE, ROLE_VIEW; USER_CREATE, USER_DISABLE, USER_UPDATE, USER_VIEW |
| POS_CASHIER | POS_VIEW, POS_SESSION_OPEN, POS_SESSION_CLOSE, POS_SALE_CREATE, POS_TENDER_CASH_RECORD, POS_TENDER_EXTERNAL_RECORD, POS_RECEIPT_VIEW |
| SALES_MANAGER | POS_VIEW, POS_REPORT_VIEW |
| INVENTORY_MANAGER | PRODUCT_VIEW, INVENTORY_VIEW, INVENTORY_RECEIVE |
| ACCOUNTANT | POS_VIEW, POS_REPORT_VIEW, POS_REFUND_VIEW |

Future CRM, quotations, order, shipment, installation and service permissions are not granted until the corresponding APIs and assignment-scoped enforcement are implemented. Deferred templates remain visible as catalogue definitions with explicit reasons, but cannot be materialized or assigned.

## Verify persisted RBAC

Connect to the solar database and run:

```sql
SELECT tenant_id, role_key, template_key, template_version, active
FROM vendor_roles
ORDER BY tenant_id, role_key;

SELECT a.tenant_id, a.username, r.role_key, a.active
FROM employee_role_assignments a
JOIN vendor_roles r ON r.tenant_id=a.tenant_id AND r.id=a.role_id
ORDER BY a.tenant_id, a.username;

SELECT COUNT(*) AS direct_employee_permission_table
FROM information_schema.tables
WHERE table_schema=DATABASE() AND table_name='employee_permissions';

SELECT COUNT(*) AS vendor_platform_grants
FROM vendor_role_permissions
WHERE permission LIKE 'PLATFORM_%';
```

Expected: standard role rows have a non-null template key and version 1; only the five supported role names have active assignments; the direct permission table count is zero; vendor platform grant count is zero. Platform authorities are stored separately.

### Filter outlet stock receipt history

In the vendor workspace, open **Receive stock** and enter a product ID under **Filter stock receipt history by product ID**. Choose **Load stock receipt history**. Leave the product ID blank to include all receipts in the assigned outlet. The filter stays applied when paging.

`GET /api/v1/vendor/inventory/receipts` accepts optional `productId` alongside `page` and `size`. The server requires `INVENTORY_VIEW` and resolves the vendor and branch from the signed-in actor. A product with no matching receipts in that outlet returns an empty page.
