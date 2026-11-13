# Version 2.0 POS traceability

Baseline: requirements §9.2 and SRS/HLD §25.8. `develop` contained documents only, so this delivery creates the source foundation.

| Requirement | Implementation / evidence |
|---|---|
| Guest checkout, no global account | `CheckoutService.cashSale`, nullable customer ID, `PosTest.guestCashIsAtomicAndIdempotent` |
| Tenant/branch/cashier context | Employee DB membership, active tenant, Spring Security permissions, composite foreign keys; unknown request fields rejected |
| Named cashier / assigned register | `open` locks assigned register, unique active register constraint |
| Cashier session recovery | Own OPEN/CLOSING-session discovery, audited explicit resume, saved-session verification and retained uncertain attempts; tests cover cross-tenant/cashier isolation, permissions/CSRF, unchanged float/history, close races and browser recovery |
| Server pricing and cash amounts | Tax-exclusive catalogue snapshots; exact received minus change equals total |
| Inventory issue | Shared inventory row lock, guarded decrement, immutable issue ledger in sale transaction |
| Retry / concurrency | Tenant key + request fingerprint, outlet sequence lock, concurrent last-unit test |
| Receipt and conditional invoice | Stable outlet receipt sequence and conditional invoice identity; audited reprints |
| Cash movements | Idempotent cash-in/cash-out/safe drop, type-specific permissions, own tenant/branch/cashier session, reason/actor/time and audit; `PosTest` permission/isolation/concurrency/retry tests |
| Session reconciliation | Live opening float + cash received - change + cash-in - cash-out - safe drops - paid cash refunds; exact count closes; discrepant count requires a reason and freezes the drawer in CLOSING until separate supervisor review |
| Supervisor variance review | POS-020/POS-022/POS-023/POS-025: branch-scoped independent supervisor approval/return, immutable review ledger, frozen counts, stale-request/concurrent-decision protection, OPEN/CLOSING till uniqueness and upgrade backfill |
| Authentication | BCrypt + HTTP Basic + CSRF for initial slice; JWT/refresh integration still required |
| Online marketplace and cart | Public active vendor storefronts, global customer identity, scoped single-vendor cart and deliberate archived-cart switching; shared pricing and live POS inventory checkout preview; see MARKETPLACE_API.md |
| Archived cart recovery and JPA modules | Customer-owned bounded history and confirmed restore; JPA cart/catalogue/identity/inventory application modules, pessimistic locks and cart lifecycle versions; ArchUnit module rules and combined JPA/JDBC rollback regression |
| Durable draft and stock reservation | Canonical ONLINE/IN_STORE unpaid orders, designated online branch, shared held stock, 10-minute TTL and scheduled release, reasoned cancellation; checkout JPA module and cross-channel last-unit race tests |
| Reserved cash finalization | Same POS draft becomes PAID once; cash/change, stock ledger, receipt and conditional invoice in one transaction; repeat/concurrent finalization and cancel race tests |
| Order history and responsive UI | Customer-owned ONLINE and tenant/branch-scoped IN_STORE paginated history, saved line details, paid receipt recovery; responsive solar workspace, catalogue sort/stock filters, service unit and UI component tests; see ORDER_HISTORY_API.md |
| Public product details | Active-vendor/published-product scoped lookup, shared unit tax calculation, deep-linked responsive detail dialog and existing single-vendor cart integration |
| Basic channel sales reporting | POS-032 foundation: dedicated POS_REPORT_VIEW, assigned tenant/branch, separate online/in-store paid totals, saved tax/quantity, invoice/cash totals and daily/current-state counts; approved promotion savings are included; net refund reporting, manual basket overrides and external tenders remain outstanding |
| Retail catalogue and billing | CAT-001/CAT-006, POS-005/POS-006/POS-009/POS-024/POS-035: scoped categories, audited supervisor-approved product offers, shared effective prices and discount snapshots; barcode Enter lookup, quantity steppers, tax/change bill panel, gated tender tiles and audited print copies |
| POS return intake and inspected stock | POS-028/POS-031 foundation: original paid POS sale, explicit branch window, proof/quantities, independent review, inspected restock/quarantine and immutable events; no refund/credit note or cash movement is implied; see POS_RETURN_INTAKE.md |
| Marketplace cart quantity edits | Absolute quantity updates use the active cart vendor, including edits while browsing another storefront; unavailable-stock reductions and unknown-response retries covered by component tests |
| MySQL integrity | Flyway composite ownership constraints; MySQL CI job (results must be checked) |

Not implemented: external tender initiation/verification, split tender, customer-initiated association, POS exchanges/provider refunds/credit notes and further return lifecycle integration, online paid checkout, subscription entitlement checks, tax/legal invoice templates, export masking, metrics/alerts, Playwright browser suites, approved production payment providers. These remain required by Version 2.0 and are **not** reclassified as deferred product scope. Docker Compose definitions are included but local engine access was unavailable for a smoke test.

Cash movement delivery traces to SRS POS-021/POS-022/POS-044 and the cash management API matrix row: every new movement requires an open owned session, positive amount, reason and unique retry key. Identical retries after session close return the original movement without changing history. Concurrent cash removals cannot exceed the expected balance. Cash refunds use the separate V12 immutable refund journal and subtract from the same drawer formula; ordinary cash movements cannot fabricate a refund. Supervisor variance review is implemented with an explicit branch-scoped permission; other cross-cashier operations remain unavailable.

The cash-only endpoint atomically finalizes after cash is received; external methods are absent rather than accepting typed payment references. There is no card-data request model. Taxes and invoice output are engineering scaffolding pending the documented approved vendor tax configuration. Production activation is not claimed.

Session recovery extends the SRS §25.8 named-cashier, one-active-session-per-register, OPEN-session validation and connectivity-recovery scenarios. Resume reattaches the UI to an owned OPEN or CLOSING session without reopening it; a CLOSING drawer remains frozen. The session-recovery slice itself required no schema changes (`POS_VIEW` and `POS_SESSION_OPEN` already exist); variance review adds V5 and the separate supervisor grant. The UI handles the 409 caused by reopening an occupied register without SQL/browser-console workarounds. New-browser resume restores server session state, not unsent cart inputs or another tab's local retry payloads.

## Cash-refund increment

V12 adds a default-disabled, independent supervisor cash refund for inspected non-tax-invoice cash POS returns. Immutable JPA refund lines retain original discounted price/tax allocations and cumulative caps. Exactly-once retry recovery, journal-aware drawer close/removal, audited copy printing and responsive cash-reconciliation UI are implemented. Marketplace gains a server quote review invalidated by cart changes. See [CASH_REFUNDS.md](CASH_REFUNDS.md) for contract, test scope and finance release gates. Gross sales analytics is not a net financial report.

## Cash refund reporting increment

POS-032/POS-040 now include a dedicated branch-scoped cash-payout report with inclusive UTC filters, paid count/units/cash/net/tax allocations, daily zero buckets and paying-cashier totals. Two JPA aggregates in one consistent read snapshot prevent line multiplication. Explicit POS_VIEW/POS_REPORT_VIEW/POS_REFUND_VIEW checks and canonical membership protect employee data. The responsive screen is explicitly loaded, clears stale filters/results and leaves gross online/POS sales and drawer reconciliation separate. See [CASH_REFUND_REPORTS.md](CASH_REFUND_REPORTS.md). Provider refunds, complete net financial reports and statutory credit-note reporting remain release work.

## Paged marketplace catalogue increment

The public marketplace uses bounded server-side name/SKU search, category/stock filters, effective promotion-price sorting and stable product-ID ties. Stock booleans on the new catalogue and product details use designated online free stock; POS remains assigned-branch scoped. A read-only JPA projection supplies page/count/facets without exposing internal stock or importing inventory persistence classes. The responsive storefront preserves the cart across paging and ignores stale requests. See [CATALOGUE_SEARCH.md](CATALOGUE_SEARCH.md); legacy unpaged routes remain for compatibility.

## Paged POS catalogue increment

POS-005/POS-006/POS-033/POS-035 now include bounded server-side staff search/filter/sort, assigned-branch free stock and exact SKU/barcode lookup across the full published tenant catalogue. The billing panel preserves selected lines across pages, restores scanner focus, rejects ambiguous/unconfirmed codes and pauses financial actions while scanning. Legacy unpaged products API remains for compatibility but is no longer called by the POS UI. See [POS_CATALOGUE.md](POS_CATALOGUE.md).

## Reservation reference recovery increment

POS-010/POS-042 now include direct owned-reservation status lookup for customer ONLINE and assigned tenant/branch/cashier POS records, independent of recent-list limits. Current JPA order/line reads retain immutable snapshots, accurate terminal-state messages and an elapsed-expiry hint without stock/payment writes. The responsive reference form pins recovered records, preserves financial retry locks and known holds on refresh failure, and blocks old-session/expired cash commands. See [DRAFT_REFERENCE_RECOVERY.md](DRAFT_REFERENCE_RECOVERY.md).

## Aggregate report export increment

POS-032/POS-040 now include explicitly permissioned, CSRF-protected sales/cash-refund CSV generation with canonical tenant/branch scope, bounded rows, spreadsheet formula protection and buyer-field exclusion. The reporting module owns immutable JPA V13 generation audits with exact-byte hashes; report writes do not change finance/inventory. Responsive export panels use loaded report filters and ignore stale/abandoned responses. See [REPORT_EXPORTS.md](REPORT_EXPORTS.md). General customer/detail exports, platform-wide masking, async export scale and statutory accounting remain release work.

## Selling cashier report increment

POS-032/POS-040 now include paged original-selling-cashier paid POS totals with saved tax/discount/unit/invoice/cash facts, closed-shift history and canonical assigned-branch scope. A dedicated POS_CASHIER_REPORT_VIEW gate protects employee data. Separate header/count and page-bounded line JPA aggregates prevent multiplication; page-only totals are explicitly labelled. Refund-paying cashiers and existing sales exports remain separate. See [CASHIER_SALES_REPORT.md](CASHIER_SALES_REPORT.md).

## Refund journal history increment

POS-028/POS-040 now include a bounded server-side cash-payout journal with UTC dates, ID/return/order prefixes, receipt-number search and deterministic timestamp/ID ordering. Canonical tenant/branch scope applies to data and counts. Summary rows omit reasons/session/retry details; explicit selection uses the existing acknowledgement API and retains saved-handover reconciliation controls. Legacy latest-20 routes remain for compatibility. See [REFUND_JOURNAL_HISTORY.md](REFUND_JOURNAL_HISTORY.md).

## Return audit timeline increment

POS-037/POS-040 now include a paged metadata-only event timeline protected by POS_RETURN_AUDIT_VIEW and canonical tenant/branch scope. The JPA projection excludes notes/financial details; V14 adds a lookup/sort index only. The UI distinguishes copy generation from printing, clears stale data and preserves uncertain workflow commands. Equal-time ID ordering is deterministic, not proof of causal sequence. See [RETURN_AUDIT_TIMELINE.md](RETURN_AUDIT_TIMELINE.md).

## Paged return worklist increment

POS-028/POS-033/POS-040 now include a scoped, read-only JPA worklist beyond the legacy 20-row queue, with state, inclusive UTC intake dates and return/order prefix search. Metadata-only summaries open fresh case details; live oldest-first pages preserve saved command recovery controls. V15 adds a lookup index without changing financial or stock data. See [RETURN_WORKLIST.md](RETURN_WORKLIST.md) for API bounds, default 30-day range, tests and operating limits.

## Own closed-shift history increment

POS-022/POS-023/POS-025/POS-040 now include a cashier-owned, assigned-branch paged JPA history of recorded CLOSED drawer snapshots. UTC close-date/session-prefix filters, deterministic paging and a read-only responsive screen preserve saved command recovery. V16 adds an index only; pending-review and other-cashier shifts are excluded. See [CLOSED_SHIFT_HISTORY.md](CLOSED_SHIFT_HISTORY.md).

## Marketplace vendor discovery increment

Marketplace vendor selection now uses an anonymous bounded JPA directory with name/slug substring search, stable name/ID pages and public-card projections. The responsive UI preserves the selected storefront/cart and existing explicit vendor-switch controls; the legacy vendor-list API remains compatible. No schema or payment-policy change is introduced. See [VENDOR_DIRECTORY.md](VENDOR_DIRECTORY.md).

## Responsive navigation increment

Grouped marketplace/POS sidebars, active fragment/workspace states, breadcrumbs and mobile hamburger disclosure now navigate existing mounted sections. Keyboard focus, Escape, browser history and delayed session targets preserve current bills and saved commands. No service/schema/authorization change is introduced. See [WORKSPACE_NAVIGATION.md](WORKSPACE_NAVIGATION.md).

## Product discount audit increment

CAT-006/POS-036/POS-037 now include a tenant-scoped paged JPA history of existing approved percentage-change events, with existing approver grants, withdrawn-product support, responsive read-only screen and Reports navigation. V17 adds an index only; catalogue approvals/checkout/recovery remain unchanged. See [PROMOTION_AUDIT.md](PROMOTION_AUDIT.md).

## Fast cash tender increment

POS-005/POS-014/POS-035 now include responsive exact/rounded cash entry, cent-based validation, explicit amount-still-due feedback and a local new-sale readiness guard. Existing server calculation and saved retry payloads remain authoritative/unchanged. No schema/service/grant change is introduced. See [FAST_CASH_TENDER.md](FAST_CASH_TENDER.md).

## Assigned register selection increment

POS-007/POS-020/POS-025/POS-043 now include paged, canonical assigned-branch JPA register availability and an explicit-load dropdown. Only register ID/availability is exposed. OPEN/CLOSING sessions occupy registers; server opening locks remain authoritative. Unknown own-session lookup blocks new opening. V18 adds a lookup index only; existing recovery/configuration/permissions are preserved. See [REGISTER_SELECTION.md](REGISTER_SELECTION.md).

## Receipt presentation and identity increment

POS-009/POS-026/POS-027/POS-035/POS-046 now include paid seller/outlet display snapshots, stable original issue-time projection and a shared itemized responsive/printable receipt with conditional Sell navigation. V19 adds nullable fields without guessing legacy identity. Existing JPA/JDBC transaction, pricing, invoice policy, scope and copy audits remain intact. See [RECEIPT_PRESENTATION.md](RECEIPT_PRESENTATION.md).

## Unified cash checkout increment

POS-005/POS-010/POS-014/POS-030/POS-035/POS-042 now use one JPA checkout owner for fast cash and reserved finalization. The duplicate JDBC checkout/DTO/hash path is removed, with outlet-first locking and distinct intent keys. Reserved UI uses per-draft cash/reason state, shared cash controls, strict canonical session/expiry eligibility and typed receipts. No compatibility adapter/schema change is added. See [UNIFIED_CASH_CHECKOUT.md](UNIFIED_CASH_CHECKOUT.md) for pre-prod cutover and test scope.
