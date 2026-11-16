# Authorization Endpoint and Route Matrix

**Target branch:** `feature/platform-super-admin-v2`  
**Baseline commit:** `a383d937a6cef3445169f226d702d34cb9f37c8f`  
**Matrix status:** Static inventory of current controller families and protected workspace routes. This is a remediation baseline, not yet a generated CI-enforced canonical registry.

## Enforcement rules

- Spring Security authenticates protected `/api/v1/**` requests; controller method policies are authoritative for operation permissions.
- Tenant/customer ownership and branch/cashier/session scope are additional checks, not replacements for permissions.
- Platform endpoints require platform identity and the endpoint's exact `PLATFORM_*` grant. Platform context has no implicit tenant or branch.
- Vendor role grants must never contain `PLATFORM_*`.
- Frontend route/menu permissions are usability guards only; API and service checks remain authoritative.
- Unmapped routes and methods must fail closed. The current code still lacks an automated endpoint-to-route parity gate; see AUD-34/AUD-40 in the implementation report.

## Platform API families

| Controller | API base / operations | Current policy |
|---|---|---|
| `PlatformAdminController` | `/api/v1/platform`: dashboard, tenants list/create/update/lifecycle, audit, support-access, configuration | Explicit platform identity plus `PLATFORM_ANALYTICS_VIEW`, `PLATFORM_TENANT_*`, `PLATFORM_AUDIT_VIEW`, `PLATFORM_SUPPORT_ACCESS`, `PLATFORM_CONFIG_MANAGE` |
| `PlatformSubscriptionController` | `/api/v1/platform/subscription-plans`: list/create/version/active | `PLATFORM_SUBSCRIPTION_VIEW` or `PLATFORM_PLAN_MANAGE` |
| `PlatformTenantSubscriptionController` | `/api/v1/platform`: subscription list/create/status/events/billing history | `PLATFORM_SUBSCRIPTION_VIEW` or `PLATFORM_SUBSCRIPTION_ADJUST` |
| `PlatformHealthController` | Platform health | Platform-only metrics/health policy |
| `AuthenticationController` | `/api/v1/auth`: login/register/refresh/logout/me/csrf | Public authentication operations; protected identity resolution for `me` |
| `IdentityInvitationController` | Vendor invitation acceptance | Public token acceptance; invitation token and one-time state validation |

## Vendor/POS API families

| Controller family | Main route family | Permission / scope rule |
|---|---|---|
| `PosController` | `/api/v1/vendor/pos/sessions/**`, cash movements, reconciliation, products | Operation-specific POS grants; authenticated membership tenant/branch/cashier and own session |
| `RegisterDirectoryController` | `GET /api/v1/vendor/pos/registers` | `POS_VIEW + POS_SESSION_OPEN`; currently tenant+branch register listing, employee-register assignment still missing |
| `ShiftHistoryController` | `GET /api/v1/vendor/pos/sessions/history` | `POS_VIEW`; service filters to current cashier. Frontend policy aligned to POS_VIEW in this remediation |
| `PosCatalogController` | POS catalogue | `POS_VIEW` and tenant/branch catalogue scope |
| `CheckoutController`, `DraftRecoveryController` | Customer checkout drafts and POS drafts | CUSTOMER ownership for online; POS view/sale grants plus current cashier/session scope for POS |
| `PaymentCheckoutController` | Customer/vendor tender, payment attempts and manual verification | CUSTOMER order ownership or POS sale/tender grants; independent supervisor verification |
| `OrderHistoryController`, `OnlineReceiptController` | Customer/vendor order and receipt reads | CUSTOMER owner scope; POS tenant/branch and transaction visibility policy |
| `CustomerAssociationController` | POS customer association challenge/approval | POS sale owner and customer-initiated authenticated consent |
| `CashierSalesController`, `SalesReportController` | Sales and cashier reports | POS/report permissions and tenant/branch scope |
| `SaleReceiptController`, `ReceiptDocumentController` | Receipt read/reprint/PDF copy | Receipt view/reprint grants, owning tenant/branch/transaction, audit |
| `ProductAdminController`, `CategoryAdminController` | `/api/v1/vendor/products`, `/categories` | PRODUCT_VIEW / PRODUCT_CREATE / PRODUCT_UPDATE; independent price/archive rights |
| `StockReceivingController` | Inventory receipts | INVENTORY_VIEW / INVENTORY_RECEIVE; current MVP stock location is branch-scoped |
| `DiscountPolicyController`, `PromotionController`, `PromotionHistoryController` | Discount policy and promotion history | POS_VIEW plus promotion/discount management/approval grants |
| `BranchSettingsController` | Branch and register configuration | POS_VIEW for read; POS_SETTINGS_MANAGE for writes |
| `ReturnController`, `ReturnQueueController`, `ReturnAuditController` | POS returns, queue, inspection and audit | POS return view/create/approve/inspect/audit grants and original-sale scope |
| `CashRefundController`, `RefundHistoryController`, `RefundReportController` | Cash refund, history, report | POS refund view/approve/cash-record/print/report grants and original payment/session scope |
| `ReportExportController` | CSV/report exports | POS_REPORT_VIEW plus POS_REPORT_EXPORT and tenant/branch scope |

## Public marketplace and customer API families

| Controller family | Route family | Policy |
|---|---|---|
| `VendorDirectoryController`, `CatalogSearchController`, `ProductDetailController` | `GET /api/v1/marketplace/**` | Public active vendor and published catalogue only |
| `MarketplaceController` | `/api/v1/customer/cart/**` and customer marketplace operations | CUSTOMER plus authenticated customer-owned cart and one-vendor rules |
| `OnlineReceiptController`, `OrderHistoryController` | `/api/v1/customer/orders/**`, receipt | CUSTOMER plus principal-derived customer ID and order ownership |

## Frontend route policy

| Workspace route | Required permission |
|---|---|
| `/vendor/pos`, `/vendor/pos/current-sale`, `/vendor/pos/held-bills`, `/vendor/pos/sales-history` | `POS_VIEW` |
| `/vendor/pos/receipt` | `POS_RECEIPT_VIEW` |
| `/vendor/pos/after-sales` | `POS_VIEW + POS_RETURN_VIEW` |
| `/vendor/pos/refunds` | `POS_VIEW + POS_REFUND_VIEW` |
| `/vendor/pos/products`, `/vendor/pos/categories` | Any of `PRODUCT_VIEW`, `PRODUCT_CREATE`, `PRODUCT_UPDATE` |
| `/vendor/pos/stock` | Any of `INVENTORY_VIEW`, `INVENTORY_RECEIVE` |
| `/vendor/pos/register` | `POS_VIEW` |
| `/vendor/pos/drawer-records` | `POS_VIEW` (own cashier/session records) |
| `/vendor/pos/shift-review` | `POS_SHIFT_VARIANCE_APPROVE` |
| `/vendor/pos/terminal-verification` | `POS_TENDER_MANUAL_VERIFY` |
| `/vendor/pos/settings` | `POS_VIEW` (individual writes are guarded by POS_SETTINGS_MANAGE) |
| `/vendor/pos/discount-audit` | `POS_VIEW + CATALOG_PROMOTION_MANAGE + POS_DISCOUNT_APPROVE` |
| `/vendor/pos/sales-insights` | `POS_VIEW + POS_REPORT_VIEW` |
| `/vendor/pos/refund-insights` | `POS_VIEW + POS_REPORT_VIEW + POS_REFUND_VIEW` |
| `/vendor/pos/cashier-sales` | `POS_VIEW + POS_REPORT_VIEW + POS_CASHIER_REPORT_VIEW` |
| `/platform/**` | Tenantless platform account with one or more explicit PLATFORM_* grants; page actions call corresponding secured platform APIs |
| `/marketplace/**`, `/vendors/**` | Public browsing; customer actions additionally require CUSTOMER |

## Explicit gaps (not represented as complete)

1. No persisted custom vendor Role/RolePermission/EmployeeRoleAssignment implementation or Vendor Admin UI exists on this target branch.
2. Production platform MFA is not implemented. Password-only production platform login remains blocked; platform routes do not bypass that block.
3. Employee-to-register assignment is absent. Register access is tenant+branch scoped.
4. A canonical machine-readable endpoint policy registry and CI completeness/parity test are not yet implemented. This document is manually maintained and must not be treated as proof of every method having a policy.
5. Service-level authorization is not yet uniformly enforced for all externally callable services.
6. Subscription data and lifecycle services exist, but centralized subscription/feature entitlement enforcement across all vendor request and background entry points is not yet complete.
7. CRM, leads, quotations, advanced inventory, multi-warehouse, fulfilment, installation and service route families are outside this current implemented vertical slice; do not add placeholder routes.
