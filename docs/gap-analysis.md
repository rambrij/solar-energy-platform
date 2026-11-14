# Gap analysis: Marketplace, Physical Store POS, Walk-in Sales

Baseline: `feature/v2-physical-store-pos` (90 commits, 22 Flyway migrations, 32 backend test classes, 38 frontend test files / 219 tests passing).
Sources: requirements doc, SRS/HLD (§12.15 POS-001..POS-044, PAY-*, ORD-*, §25.8), README, docs/implementation/*.

## 1. Key finding
The branch is already far past a greenfield build. Marketplace browsing, carts, reservations, guest walk-in cash POS, receipts, returns, cash refunds, shift reconciliation, and gated UPI/card simulation exist. The work remaining is a defined set of gaps, not a rebuild.

## 2. Requirement vs. existing

| Area | Already built | Gap (to implement) |
|---|---|---|
| Marketplace | Vendor directory, storefront catalogue, search, category nav, sort/stock filters, product details, single-vendor cart, archived carts, 10-min reservation, order history, online receipts | Production online checkout is gated off (simulation only); no live provider adapter; checkout is not a single "simple checkout" page flow; no delivery/fulfilment capture; JWT/refresh auth (HTTP Basic remains) |
| Physical Store POS | Register/session, barcode Enter lookup, bill panel, steppers, tax/change, cash tender, drawer, close, supervisor review, returns, cash refunds, audited reprints | **Split tender (POS-013)**; manual line/bill discounts with supervisor threshold override (POS-024); customer association (verified); hold/resume parked bills UX |
| Walk-in Sales | Guest checkout without account (POS-001), idempotent cash sale, reserved drafts, receipt, conditional invoice | Split tender; optional verified customer association; share receipt |
| Shell/UI | Sidebar groups, active highlight, hamburger, breadcrumb, skip link, solar palette | Dropdown menus; tabs inside workspaces; no Playwright/real-browser tests |

## 3. Proposed scope (ordered)
1. **Split tender**: multiple tender rows per order; applied sum must equal server amount due before PAID; change modelled separately from tender (POS-013, SRS line 1161).
2. **Manual discounts**: line and bill discounts, server-validated, per-role threshold, supervisor approval above threshold, immutable audit (POS-024).
3. **Walk-in customer association**: optional, verified existing-customer link (SRS §25.8).
4. **Simple online checkout screen** over the existing reservation + gated payment flow.
5. **Shell**: add dropdown (workspace switcher / account) and in-page tabs (Sell / Hold / Receipt) using existing `Shell.tsx` and `navigation.ts`.
6. Tests for each, then frontend + backend suites.

## 4. Module boundaries (existing, to be kept)
`catalog`, `cart`, `checkout`, `inventory`, `identity`, `retail`, `returns`, `documents`, `reporting`, `marketplace`, `shared`; each with `internal` for entities/repositories. ArchUnit rules already forbid cross-module internals and cycles. New split-tender and discount logic goes in `checkout` (tenders/payment attempts) and `catalog` (discount policy) and is exposed to `retail` via service interfaces.

## 5. Data model changes (draft; next Flyway is V23)
- `order_tenders` (id, tenant_id, order_id, method, amount, currency, status, reference, created_at) with composite tenant FK; unique (order_id, idempotency_key).
- `order_discounts` (id, tenant_id, order_id, line_id nullable, type PERCENT|AMOUNT, value, reason, approved_by nullable, created_by, created_at).
- `branch_policies.max_cashier_discount_percent` (default 0 = manual discounts disabled).
- Possible `orders.customer_id` association audit row.

## 6. API contracts (draft)
- `POST /api/pos/checkout/{draftId}/tenders` body `{method, amount, idempotencyKey, reference?}` -> tender + remaining due.
- `POST /api/pos/checkout/{draftId}/finalize` requires sum(tenders) == due (cash may exceed; change computed).
- `POST /api/pos/checkout/{draftId}/discounts` body `{lineId?, type, value, reason, approvalToken?}`.
- `POST /api/pos/checkout/{draftId}/customer` body `{customerId, verification}`.
All mutations: CSRF, tenant/branch/session scoping, idempotency key, type-specific permissions (new: `POS_DISCOUNT_APPLY`, `POS_DISCOUNT_APPROVE`, `POS_TENDER_SPLIT`).

## 7. Existing code likely to conflict (fix directly, no shims)
- `PaymentCheckoutService` / `PaymentPolicy` assume one attempt per order ("order ID is the initiation idempotency boundary"); this must become per-tender.
- `CheckoutService.cashSale` finalization assumes a single cash tender (received minus change); generalise to tender rows.
- Reserved-draft cash/cancellation values stored by draft ID; extend for tenders.
- Receipt/PDF/online-receipt renderers print a single tender; update.
- Reporting totals assume cash vs. one method; update channel/tender breakdown.
- HTTP Basic "legacy" auth still alongside global customer auth.

## 8. Open questions
1. Is JWT/refresh in scope for this PR or a separate security PR?
2. Which payment methods may be combined in split tender (cash+UPI+card)? Max tender rows?
3. Default manual-discount threshold and who approves (supervisor permission only?).
4. Is delivery/fulfilment in scope for marketplace checkout?
5. Tax rounding policy for discounted lines (bill-level proration).
6. Is a live provider adapter in scope, or does simulation remain?

## 9. Environment notes
Frontend baseline verified here: `npx vitest run` -> 38 files, 219 tests passed. Maven and JDK tooling are not available in this sandbox (no `mvn`; Maven Central is not reachable), so backend tests could not be run here and must be run in your CI/Claude Code environment.
