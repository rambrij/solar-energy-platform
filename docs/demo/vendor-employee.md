# Vendor Employee demo walkthrough

This guide covers only the employee roles that are currently provisioned with implemented permissions. It is an implementation walkthrough for the local demo fixtures; it does not claim that every role named in the Version 2.0 role catalogue is operational.

> **Local/test only.** Use the disposable local profile and the demo credentials in [Local demo accounts, tenant roles and permissions](README.md). Never use these passwords or the mock provider configuration in production.

## 1. Prerequisites and sign-in

1. Follow [Clean Development Database Setup](../implementation/CLEAN_DEVELOPMENT_DATABASE.md) for a fresh disposable database. Set `SPRING_PROFILES_ACTIVE=local` and start the stack with `docker compose up --build -d`.
2. Choose an employee credential from the **Vendor employee credentials** table in [README.md](README.md). The supported demo roles are `POS_CASHIER`, `SALES_MANAGER`, `INVENTORY_MANAGER`, and `ACCOUNTANT`. Use the account for the vendor you intend to test.
3. Open [Vendor POS](http://localhost:8080/vendor/pos) or, with Vite, http://localhost:5173/vendor/pos. Sign in and choose **Vendor** if the actor selector is displayed.
4. Verify the active tenant, branch and role before working. The role, its persisted permission grants, tenant entitlements, and applicable branch/register/session assignments jointly determine access. Do not use a Platform or Customer identity for vendor operations.

The seeded employee accounts are already activated. For a newly invited employee, a Vendor Admin must first invite the email, select an active supported role, set the branch and any applicable register assignment, and share the one-time invitation token through an approved secure channel. The employee opens `/vendor/invitation`, accepts the invitation, sets a password, and then signs in with the Vendor actor. An already-active identity uses its existing credentials; the invitation process does not reset that account's password.

## 2. Role and permission reference

The [demo account matrix](README.md) lists the local usernames and credentials. The [supported standard-role permission snapshots](README.md) are the checked-in seed reference. The following summaries describe the current effective demo grants, not a promise of unrestricted access to every screen in a feature area.

| Role | Current supported grants | Available workflow |
| --- | --- | --- |
| `POS_CASHIER` | `POS_VIEW`, `POS_SESSION_OPEN`, `POS_SESSION_CLOSE`, `POS_SALE_CREATE`, `POS_TENDER_CASH_RECORD`, `POS_TENDER_EXTERNAL_RECORD`, `POS_RECEIPT_VIEW` | Assigned register/session, walk-in sale, permitted cash/external tender actions, receipt and own shift controls |
| `SALES_MANAGER` | `LEAD_VIEW`, `POS_VIEW`, `POS_REPORT_VIEW` | Read the current vendor lead inbox and view permitted POS sales reporting |
| `INVENTORY_MANAGER` | `PRODUCT_VIEW`, `INVENTORY_VIEW`, `INVENTORY_RECEIVE` | Browse the vendor catalogue and receive stock for the assigned outlet |
| `ACCOUNTANT` | `POS_VIEW`, `POS_REPORT_VIEW`, `POS_REFUND_VIEW` | View available POS/refund reporting and refund records; no refund approval or cash-refund recording grant is included |

## 3. POS Cashier

**Prerequisite:** A Vendor Admin has assigned the employee to the vendor branch and an eligible POS register. The register must not already have another open/closing session.

**Required role/permissions:** `POS_CASHIER` with the grants shown above, plus the server-validated employee, branch, register and active-session assignment.

**Navigation:** `Vendor -> POS -> Cashier Session` (`/vendor/pos`).

1. Open **Cashier Session**, select the assigned register and open the shift with the opening float. If the employee already has an open session, select that session rather than opening a second one.
2. Open **Current Bill** (`/vendor/pos/current-sale`). Search the vendor catalogue by product name, SKU or barcode, add items, and check the server-calculated amount due.
3. Complete a cash sale using the received amount. The server records cash received, cash applied and change, checks stock and session state, finalizes the sale once, and issues the receipt.
4. Open **Current Receipt** (`/vendor/pos/receipt`) or **Sales History** (`/vendor/pos/sales-history`) to review the completed sale. A receipt is visible only within the employee's permitted tenant/branch scope.
5. Open **Opening Float & Drawer** (`/vendor/pos/register`) to review movements and close the shift with a counted amount. Use **Drawer Records** (`/vendor/pos/drawer-records`) to review the saved reconciliation.

**Expected result:** A completed sale is one vendor-owned `IN_STORE` order, with the current vendor's inventory and receipt records updated. A guest sale does not create a customer account or require contact details unless legally required.

**Validation and restrictions:** An unassigned register, another cashier's session, an inactive tenant, unavailable stock, or a stale/expired held bill is rejected. This seeded cashier role does not include `POS_TENDER_MANUAL_VERIFY`, `POS_REFUND_APPROVE`, `POS_RECEIPT_REPRINT`, `POS_SHIFT_VARIANCE_APPROVE`, or POS report grants. A typed UPI/card reference cannot mark a tender paid. When separate supervisor review is required, the initiating cashier cannot approve it.

## 4. Sales Manager

**Prerequisite:** Use a seeded Sales Manager account for the tenant whose leads and reports you want to inspect.

**Required role/permissions:** `SALES_MANAGER`: `LEAD_VIEW`, `POS_VIEW`, and `POS_REPORT_VIEW`.

**Navigation:** `Vendor -> Customer Management -> Customers & CRM` (`/vendor/customers`); `Vendor -> Reports -> Sales Insights` (`/vendor/pos/sales-insights`).

1. Open **Customers & CRM** to view the lead list returned for the active vendor. The current employee permission snapshot includes `LEAD_VIEW`; it does not grant customer-relationship mutation or CRM-note access.
2. Open **Sales Insights** to review the POS reporting available to the role. Select the period exposed by the report and verify the vendor context.
3. Try a direct URL or action that requires a permission not in the role. The menu may be hidden, but the backend must also reject an unauthorized request.

**Expected result:** Lead visibility and POS reports are limited to the signed-in vendor. The employee cannot view another vendor's lead records or CRM notes.

**Validation and restrictions:** The current lead controller exposes a read-only vendor lead list. Lead assignment/updates, site surveys, quotations, quotation approval and conversion are not available as employee workflows in this implementation. Do not describe this role as supporting those operations.

## 5. Inventory Manager

**Prerequisite:** Use a seeded Inventory Manager account with its assigned vendor branch/stock location configured.

**Required role/permissions:** `INVENTORY_MANAGER`: `PRODUCT_VIEW`, `INVENTORY_VIEW`, and `INVENTORY_RECEIVE`.

**Navigation:** `Vendor -> Catalogue & Inventory -> Manage Products` (`/vendor/pos/products`) and `Vendor -> Catalogue & Inventory -> Receive Stock` (`/vendor/pos/stock`).

1. Open **Manage Products** and search the current vendor's catalogue. Review SKU, barcode, publication state and displayed product details.
2. Open **Receive Stock**, select a product, enter the received quantity and reason, and submit the stock receipt.
3. Record the receipt identifier. Review the receipt history; an optional product ID filter narrows the list and remains applied while paging.
4. Sign in as an employee from another vendor and confirm the first vendor's products and receipts are not returned.

**Expected result:** Stock receipt records and balances are updated for the signed-in vendor and assigned outlet only.

**Validation and restrictions:** Product visibility does not grant product create/update/archive or price-update authority. Stock receiving requires a positive quantity, a valid vendor product and a supported reason. Cross-vendor product IDs must not reveal another vendor's data.

## 6. Accountant

**Prerequisite:** Use a seeded Accountant account for the target vendor. POS/refund records must exist to make the reporting views meaningful.

**Required role/permissions:** `ACCOUNTANT`: `POS_VIEW`, `POS_REPORT_VIEW`, and `POS_REFUND_VIEW`.

**Navigation:** `Vendor -> Reports -> Sales Insights` (`/vendor/pos/sales-insights`), `Vendor -> Reports -> Refund Insights` (`/vendor/pos/refund-insights`), and `Vendor -> After-Sale -> Cash Refunds` (`/vendor/pos/refunds`).

1. Open **Sales Insights** and review the available sales totals for the active tenant.
2. Open **Refund Insights** to inspect refund reporting and the current vendor's permitted refund records.
3. Open **Cash Refunds** to review the existing cash refund workflow and its records. Do not assume that viewing a refund gives permission to approve or issue one.

**Expected result:** The Accountant can see only reporting and refund information authorized for their vendor.

**Validation and restrictions:** The seeded Accountant permission set does not include `POS_REFUND_APPROVE` or `POS_REFUND_CASH_RECORD`. It also does not include general vendor invoice/payment/reconciliation grants. Those operations must be denied unless the administrator assigns a separately supported, delegable permission and the corresponding API exists.

## 7. Verify role and tenant isolation

1. Sign in as a Saumya employee and open only the pages listed for that role.
2. Open a direct URL for an unrelated vendor or a permission-protected action. A hidden navigation item is not the security boundary; the backend must enforce actor context, tenant, membership, permission, branch/resource assignment and workflow state.
3. Sign out and sign in as the corresponding Helio employee. Confirm the new session has only Helio's actor scope and data.
4. Ask a Vendor Admin to inspect **Administration -> Employees -> Manage -> Preview effective permissions** and the role assignments. The server-calculated permission list is authoritative.
5. For database-level verification, use the read-only RBAC queries in [README.md](README.md). Vendor role grants must not contain any `PLATFORM_*` permission.

**Expected result:** A vendor employee cannot read, update, search, export or infer another tenant's products, stock, CRM notes, sales, receipts or refunds by changing a URL, resource ID or request payload. Unassigned roles and ungranted actions remain deny-by-default.

## 8. Roles intentionally not available in the local demo

The seed includes identity fixtures for `SALES_EXECUTIVE`, `OPERATIONS_MANAGER`, `SERVICE_MANAGER`, `SERVICE_ENGINEER`, and `CONTENT_MANAGER`, but does not assign them active role grants. Their corresponding assignment-scoped APIs/workflows are not implemented. They are not test accounts for a successful feature journey and must not be assigned speculative permissions to make menus appear.

The current implementation also does not expose the complete Version 2.0 assisted-sales workflow (lead creation/assignment, site surveys, quotation revisions/approval/conversion), installation, warranty, service-ticket or advanced shipped-online return journeys. These remain explicit gaps or later-scope work; see the requirements-to-code report at the end of this task.

## 9. Related walkthroughs

- [Local demo accounts, tenants and permission snapshots](README.md)
- [Vendor Admin setup, employees and role assignment](vendor-admin.md)
- [Global Customer journey](customer-journey.md)
- [Platform Super Admin onboarding and subscriptions](platform-super-admin.md)
