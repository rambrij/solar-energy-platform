# Unified JPA cash checkout and reservation tender

Traces SRS POS-005/POS-006/POS-009/POS-010/POS-014/POS-030/POS-035/POS-042 and requirements §9.2. Pre-production refactor: remove the separate retail JDBC pricing/order/stock path rather than retain a compatibility adapter.

## One checkout owner

POST /api/vendor/pos/sales is now owned by CheckoutController and CheckoutService.CashSale. It retains the existing cash JSON fields and explicit sale/tender/receipt permissions. Canonical membership is resolved once; the outlet is locked first, then the shared POS reservation path persists JPA order/line snapshots and holds stock. The same transaction consumes the hold and finalizes PAID through the existing finalizer, receipt sequence/documents and inventory ledger. Receipt, invoice identity, stock and reservation state commit together. A failed cash command leaves no partial draft/hold/sequence allocation.

Fast cash performs reserve/finalize atomically after the cashier confirms cash; it does not claim a separately durable unpaid stage between HTTP requests. The explicit Reserve walk-in sale path still persists an unpaid time-limited reservation for later review/payment. ONLINE reservations use the same inventory domain but remain unpaid; no new provider simulation or typed-reference payment verification is introduced.

Remove PosService.checkout, its request/line DTOs, request hashing and unused pricing/inventory dependencies. Retail continues to own sessions, drawer movements, outlet sequence and document/ledger SQL in the shared JPA transaction. It does not depend on checkout internals. The checkout module owns order/line/reservation orchestration; module cycles/internal imports remain tested. Invoice issuance audit now comes from the one document writer for both cash paths.

Fast-cash and explicit-reservation idempotency intents have distinct POS_CASH/POS namespaces. Cash fingerprints include canonical received amount. Matching retries return the same paid receipt even after shift close; changed cash/body conflicts rather than creates a second sale. Outlet-first lock ordering is consistent with reserved finalization. Cash bounds are validated before persistence for both paths.

No schema/config migration is needed. This pre-prod cutover does not recognize former direct-JDBC raw-key records through a fallback. Finish/reconcile pre-cutover demo attempts before changing builds; do not replay old browser cash attempts across this refactor. Fresh workflows and their exact retries are covered. No database/browser data is cleared automatically.

## Per-reservation cash UI

ReservedDrafts now stores cash and cancellation reasons by draft ID. Values cannot bleed into a recovered/different reservation. Only an IN_STORE DRAFT with the exact current canonical session, valid future expiry and no expired flag is eligible for new tender. Missing session or malformed expiry fails closed; no optional-session bypass remains. The Draft contract requires a sessionId field (null for ONLINE).

Direct and reserved sales reuse CashTender/cashTenderMath for exact/rounded entry, cent-based validation and short-cash feedback. Each instance has a unique accessible description ID. Receipt callbacks use the concrete Receipt type instead of any. Cash and cancellation reasons clear only for the successfully resolved reservation; uncertain attempts keep the exact original payload and remain frozen. Explicit Retry same draft operation is independent of local edited amounts/new-entry eligibility.

Rebuild backend/frontend, start fresh pre-prod workflows, and test fast cash plus explicit reservation/finalization. Use exact/rounded cash on the intended reservation; a short amount cannot finalize. A foreign/elapsed/missing-session reservation cannot prepare cash. Marketplace cancellation reasons are also bound to their own draft. Existing menus, live connectivity requirements, scope, server revalidation and payment-provider gates remain unchanged.

## Tests and remaining work

Existing sale/return/report/session regressions now call the canonical checkout owner. New tests cover invalid cash before persistence, shared reserved cash bounds, consumed reservation evidence, unchanged replay/changed-cash conflict, failed cash rollback and separation from explicit-reservation keys. Existing concurrent last-unit and mixed JPA/JDBC rollback tests remain in the suite. Client policy tests cover channel/session/expiry eligibility; component tests cover isolated draft inputs, exact/short cash, unique descriptions and original uncertain retry behavior. Full backend/frontend suites and production build are run.

Native browser/mobile/tablet, physical scanner/printer/cash and assistive-technology acceptance remain outstanding. Production vendor-direct online/UPI/card/split tender, invoice legal configuration and full V2 release gates are unchanged. This does not implement offline checkout or full migration of retail session/accounting SQL to JPA.
