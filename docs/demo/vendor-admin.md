# Vendor Admin demo walkthrough

This guide runs through the current vendor workspace as a seeded `VENDOR_ADMIN`, from catalogue setup and inventory receiving to a POS sale, reconciliation, and employee access.

> **Local development only.** These public demo credentials are for a disposable environment. Keep the local profile and mock integrations off public deployments.

## Open the vendor workspace

1. Set `SPRING_PROFILES_ACTIVE=local` in the repository root `.env` and run `docker compose up --build -d`. For a fresh checkout or an outdated disposable database, follow the [clean database setup](../implementation/CLEAN_DEVELOPMENT_DATABASE.md).
2. Open [Vendor POS](http://localhost:8080/vendor/pos). If running the frontend directly with Vite, open [Vendor POS on Vite](http://localhost:5173/vendor/pos).
3. Sign in with one account below. Choose **Vendor** if asked to select an actor. Use Saumya Solar Rooftops for the main walkthrough and Helio Rural Energy for the isolation check.

| Vendor and assigned branch ID | Login | Local password |
| --- | --- | --- |
| Saumya Solar Rooftops · `910001` | `saumya.admin@somya.local` | `Somya-Saumya-ADM-2026!` |
| Helio Rural Energy · `920001` | `helio.admin@somya.local` | `Somya-Helio-ADM-2026!` |
| Lumina Solar Lighting · `930001` | `lumina.admin@somya.local` | `Somya-Lumina-ADM-2026!` |

All three use the `VENDOR` actor and the persisted `VENDOR_ADMIN` role in their own tenant. The fixture branch ID equals the tenant ID for these accounts. Cashier, inventory, sales manager, accountant, and customer comparison accounts are in the [demo account matrix](README.md). For the customer-side checkout, order, receipt and tracking handoff, see the [Global Customer journey](customer-journey.md).

## Quick reference

| What you want to do | Where to find it | What to verify |
| --- | --- | --- |
| Organize the catalogue | **Catalogue & Inventory → Categories** | Changes apply to the signed-in vendor. |
| Create or edit a product | **Catalogue & Inventory → Manage Products** | Save a SKU, price, publication status, and reason. New products start with zero stock. |
| Record a delivery or find its receipt | **Catalogue & Inventory → Receive Stock** | The delivery goes to the assigned outlet. Receipt history can be filtered by product ID. |
| Register a carrier and update an online shipment | **Fulfilment → Shipments** | Only paid online customer orders can be registered; append tracking events with their audit reasons. Customers see the customer-safe tracking projection. |
| Invite staff and assign work scope | **Administration → Employees** | Choose an active role and branch. For POS staff, assign an eligible register separately. |
| Create or revise a role | **Administration → Roles & Permissions** | Check the permissions you can delegate and enter an audit reason. |
| Open a register and complete a walk-in sale | **POS → Cashier Session → Current Bill** | Use the assigned register and a saved opening float. Finalizing a sale creates a receipt. |
| Resume an unpaid bill | **POS → Held Bills** | Open the saved bill before retrying a sale whose outcome is uncertain. |
| Find a completed sale or receipt | **POS → Sales History → Current Receipt** | Check the original receipt number. Reprint and PDF copy actions require their applicable permission. |
| Close and reconcile the till | **Store Management → Opening Float & Drawer** and **Drawer Records** | Enter counted cash and check the server-calculated variance. Use **Supervisor Shift Review** when required. |
| Review external tender evidence | **Store Management → Terminal Verification** | Review eligible pending items. A reference entered by the initiating cashier does not confirm a payment. |
| Review returns or cash refunds | **After-Sale → After-sale Service** or **Cash Refunds** | Start from an eligible POS sale. Inspection and refund actions have separate controls. |
| Review branch performance | **Reports → Sales Insights**, **Refund Insights**, **Cashier Sales**, or **Discount Audit** | Set the reporting period and use the export controls where offered. |

## Catalogue and inventory flow

1. Open **Manage Products** and load the catalogue. Enter a name, SKU, or barcode to search the entire authorized catalogue, including unpublished products. Combine search with **Product publication status** to show published products, unpublished products, or both. After loading the catalogue, use **Filter by category** to narrow the results to a category. Use the category paging controls when the category you need is on another page. The submitted filters apply across product pages. The vendor products API accepts `published=true` or `published=false`; omitting the parameter includes both statuses. Add a positive `categoryId` to filter by category. A category with no matching products in the signed-in vendor’s catalogue returns an empty page. Use a seeded product for the shortest demo, or create a product with a unique SKU, a valid price, and a change reason. If you want it visible in marketplace browsing, select **Published in vendor catalogue** and verify it on your vendor storefront.
2. Open **Receive Stock**. Optionally enter a product name, SKU, or barcode. Select **Load products for receiving**, choose the product, enter a quantity and reason, and select **Record stock receipt**. Record the receipt ID shown after confirmation.
3. Enter the product ID and select **Load stock receipt history** to narrow the receipt list. The product filter persists when paging and the server scopes every page to your assigned outlet.
4. If the app reports an uncertain receipt outcome, use **Retry same stock receipt**. It sends the saved command with the same idempotency key. Avoid recording the delivery a second time as a new receipt.

The stock receipt shows availability immediately after that receipt. Subsequent sales or reservations can change the current balance.

## Vendor profile and product images

1. Sign in as the initial Vendor Admin for an active tenant. Tenant Admin and Vendor Admin refer to the same tenant-scoped role in this application.
2. Open **Administration / Vendor Profile** and select **Load vendor profile**. With `TENANT_PROFILE_VIEW` and `TENANT_PROFILE_UPDATE`, edit the business name, contact details, address or tax identifier and select **Save business profile**. Refresh the public storefront to verify the public fields. If another user saved the profile first, reload its current version.
3. Open **Catalogue & Inventory / Solar Packages & Images**. With `PRODUCT_VIEW`, choose a product in **Product image gallery**. With `PRODUCT_UPDATE`, select a PNG, JPEG or WebP image up to 5 MB, enter a description and a reason, and select **Upload product image**. The disposable object storage service must be healthy. Verify the preview in the vendor gallery and check the public product details after publishing the product.
4. To take a product out of the public catalogue, open **Manage Products**, edit the product, clear **Published in vendor catalogue**, and save with a reason. Unpublishing an already published product requires `PRODUCT_ARCHIVE`. Use a received stock delivery to change available quantity.

## Employee and role lifecycle

1. Open **Roles & Permissions** to inspect the seeded roles. To create a limited role, enter a role key, display name, only the needed permissions, and an audit reason. For example, a stock history viewer can use `INVENTORY_VIEW` without `INVENTORY_RECEIVE`. When editing an existing role, review **Permission changes** before saving. It compares the replacement set against the loaded role version and lists the grants that will be added or removed.
2. Open **Employees → Invite employee**. Enter a fresh demo email, your fixture branch ID, an active initial role, and an audit reason. A new identity receives a one-time invitation token. The invitation result shows the expiry and links to the vendor activation page. Share the token through an approved secure channel. The employee opens `/vendor/invitation`, pastes the token, sets a password, and signs in as Vendor. An already active identity may use its existing credentials.
3. In **Employees**, filter the directory by email, account status, or role where available. Choose **Manage** on the matching employee to review their assignments. **Save role assignments** saves the selected set, so include every role the employee should retain. Save POS register assignments separately where needed.
4. Use **Preview effective permissions** to read the server-calculated grants. The result explicitly shows when the employee has no effective permissions. Review the saved branch and register assignments separately, then sign in as the employee in a separate browser session. Confirm the pages and actions match the assigned role. If you remove access, verify the affected operation is denied by the backend.
5. Use **Disable** with an audit reason on a disposable employee if you want to demonstrate revocation. Keep at least one working administrator for the tenant.

The invitation sets the initial password. An eligible pending invitation can be reissued from employee administration. The current workspace has no password change or reset action for active employees.

Platform grants are outside this workflow. Vendor permissions depend on active memberships, tenant roles, entitlements, and applicable branch, register, and session assignments.

## Cash sale, close, and after-sale flow

1. In **Cashier Session**, select an assigned register and enter the opening float. If a session is already open, select it. Use a stocked product from the inventory flow to start a walk-in sale.
2. In **Current Bill**, review the quantities and server-calculated total, then complete a cash tender using the actual cash received in the demo. A guest cash sale can complete without creating a customer account. Do not ask for or search for a customer account as a condition of sale. If the buyer explicitly requests association with an existing global customer account, use the customer-requested approval flow described below before finalizing the eligible draft.
3. Open **Current Receipt** and verify the sale and receipt identifiers. Use **Sales History** to find the sale again. When reprinting, supply the requested reason and verify the result is a copy of the original.
4. Open **Opening Float & Drawer**, review the movements, and close with the counted cash. Check **Drawer Records** for the saved reconciliation. If a variance requires approval, use **Supervisor Shift Review** with the appropriate permission.
5. For an after-sale test, select the eligible sale from **Sales History** and follow the return action. Record inspection and disposition as required, then use **Cash Refunds** only once the return reaches the eligible state. Verify the refund against the original sale and tender.

Use cash for this walkthrough. External terminal confirmation, manual evidence review, and supervisor approval follow their separate controls. For local marketplace test payments, use the [vendor payment mock walkthrough](platform-super-admin.md#simulate-a-vendor-payment). Live payments and statutory GST invoicing are outside this demo.

### Optional: customer-requested global account association

A walk-in sale remains a guest sale unless the customer asks to link it to their own existing global customer account. The POS must not silently create an account or reveal whether an email belongs to a customer.

1. Keep the sale as a draft and open the **Walk-in customer** panel.
2. Only after the buyer asks for the association, select **Customer requested linking their own account** and choose **Create customer approval link**.
3. Share the one-time link with the customer so they can open it on their own device. The challenge currently expires after five minutes.
4. The customer signs in with their own CUSTOMER context, reviews the vendor and purchase total, and explicitly consents at `/customer/associate`. See the [customer association walkthrough](customer-journey.md#9-optional-link-a-verified-in-store-purchase-to-your-customer-account).
5. Complete the sale after approval. Only the current vendor relationship is created; another vendor's customer history is never exposed to the cashier or vendor.

The customer can complete or decline the association. A sale can still complete as a guest when the customer does not request or approve the link.

### Online shipment registration and customer tracking handoff

1. Open **Fulfilment → Shipments** using an account with `SHIPMENT_VIEW` and `SHIPMENT_UPDATE`.
2. Select an eligible **paid ONLINE** order. Enter the carrier, tracking reference, optional HTTPS tracking URL and required audit reason, then register the shipment.
3. Add progress events as they become available. Each event has a customer-facing event code/description and a separate vendor-private audit reason. Events are append-only; do not use this screen to overwrite the history.
4. Ask the customer to open **My Dashboard → Shipment updates** or the matching order detail in **Your Purchases**. The customer sees vendor, carrier, tracking reference, supplied carrier URL and customer-safe event history. Vendor audit reasons and employee identities are not returned to the customer.

The current integration records shipment and progress information entered by the vendor. It does not establish a live carrier API connection, prove an external delivery scan, or implement installation, warranty, service-ticket or advanced online-return workflows. Those capabilities remain later Operations scope; see the [Global Customer journey](customer-journey.md#13-capabilities-not-yet-exposed-as-a-customer-journey).

## Reports and outlet settings

- Under **Reports → Sales Insights**, select a date range that includes the demo sale. Compare the channel totals and the cash applied. Unpaid bills are separate from recorded sales. Use **Opening Float & Drawer** for physical cash reconciliation.
- Open **Cashier Sales** to review activity by cashier, **Refund Insights** for posted refund activity, and **Discount Audit** to review recorded discount changes. Export where offered and check that the date range and branch match the view.
- Under **Store Management → Outlet & Payment Settings**, review the current outlet configuration. Confirm the assigned branch before changing any settings. Platform subscription, provider credential, and tenant lifecycle administration belong to the [Platform Super Admin workspace](platform-super-admin.md).

## Verify access boundaries

| Check | Expected result |
| --- | --- |
| Sign in as the seeded Saumya POS cashier | Assigned POS sale and receipt actions are available. Employee and role administration and stock receiving are denied. |
| Sign in as the seeded Saumya Inventory Manager | Permitted catalogue and inventory workflows are available. Vendor administration and cashier mutations require their own grants. |
| Sign in as Helio and filter stock receipt history by the Saumya test product ID | The assigned Helio outlet returns no matching receipts. The Saumya receipt remains visible to an authorized Saumya user. |
| Try to open the Platform workspace as a Vendor Admin | Platform functions are denied. |
| Sign in as a seeded deferred employee | No implemented vendor role permissions are granted by the fixture. |

## Troubleshooting

- **A menu or action is missing** — Confirm the active actor is Vendor. Inspect the effective role and entitlements, then check the branch or register assignment. Frontend visibility follows permissions, and the backend authorizes each operation.
- **The register cannot open** — Confirm the signed-in employee has the register assignment and that the register has no conflicting active session.
- **A product is missing from the storefront** — Check that it is published and that you opened the intended vendor storefront. For a sale, also verify stock in the correct outlet.
- **A request failed after submission** — Review the saved receipt, held bill, or sales history first. Use the workflow’s saved retry action when offered.
- **You need a reset** — Follow the [clean database setup](../implementation/CLEAN_DEVELOPMENT_DATABASE.md) for disposable local data only.
