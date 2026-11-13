# Product discount audit history

Traces CAT-006, POS-036/POS-037 and the requirements for shared approved catalogue discounts. It exposes existing immutable product-percentage change facts to authorized catalogue approvers; it does not add price-edit or basket-override commands.

`GET /api/vendor/pos/promotions/{productId}/history?page=0&size=12`

- Requires all POS_VIEW, CATALOG_PROMOTION_MANAGE and POS_DISCOUNT_APPROVE plus persisted enabled employee/active tenant membership. No grant is assigned automatically. Tenant comes only from membership. These promotions are tenant-wide, not branch-specific: a properly authorized employee in another branch of the same tenant may read them.
- Positive product ID; page 0–10000, size 1–48. Parent product lookup and both page/count predicates use canonical tenant/product. Foreign/missing products return generic 404. Withdrawn products retain their audit history for authorized staff.
- Newest timestamp first, descending immutable event ID for ties. Equal timestamps do not establish causal order. Live offset pages can shift as new approvals are recorded.
- Response: productId, currentSku/currentName, current publication status, pagination/count and events with id, previousPercent, newPercent, actor, reason, createdAt. Product labels are current, not historical snapshots. No list-price/tax history or historical currency totals are inferred. Existing no-change approvals do not append an event.
- Only recorded change facts are read. This history is not proof of stock, cashier/session activity, customer consent or physical printing. No buyer data or customer lookup is added. Reason text is private staff audit data; do not enter customer/payment credentials into promotion reasons.

## Architecture and operation

The catalogue module owns a Spring Data JPA constructor projection and scoped page/count query. Product metadata and audit rows/count share one read-only repeatable-read transaction. Marketplace controller resolves canonical retail membership and calls the public catalogue service; no foreign persistence imports are introduced. V17 adds ix_promotion_history(tenant_id,product_id,created_at,id) only. Existing discount approval writers, audit records, price rules and applied migrations remain unchanged.

Rebuild/restart backend for V17 and rebuild frontend. In POS, use Discount history for a product card, then explicitly Load discount audit; or use Reports → Discount audit and enter the internal product ID. Authorized approvers may read history after shift close. No new grant/demo script is needed. A permission error does not imply an empty history.

The responsive screen uses the existing solar palette/menu and explicit paging. It ignores late responses after selection/disable/unmount, clears stale data on reference/error changes, and checks response product identity. Product-card selection passes only an ID and never adds to the bill or fetches protected audit data automatically. Audit reads do not reconcile saved commands, change catalogue prices/stock, reopen sessions or initiate payments. Use existing checkout/return/refund recovery flows for uncertain commands.

## Validation and limits

Unit tests cover tenant/product binding, withdrawn labels, missing products and invalid bounds. HTTP/database tests cover 25 real approvals with tied-time deterministic pages/counts, unchanged approvals, foreign-tenant isolation and forged scope, same-tenant cross-branch access, inactive membership, each required permission, withdrawn/renamed products, empty later pages and unchanged price/stock/drawer values after reads. UI tests cover explicit load/paging, current-label semantics, empty/error/invalid/mismatched responses, preserved saved attempt, stale product/disable/unmount responses and reference-only catalogue navigation. Full backend/frontend suites and production build are run.

Real browser/mobile/tablet/assistive-technology acceptance remains outstanding (component tests use jsdom). General list-price/tax audit browsing, dedicated audit-only roles, audit retention/export and high-volume cursor/as-of paging remain policy/product work. This slice does not implement production payments or full Version 2.0 acceptance.
