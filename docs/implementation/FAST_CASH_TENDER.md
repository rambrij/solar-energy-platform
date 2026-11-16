# Fast cash tender entry

Traces SRS POS-005, POS-014, POS-035 and requirements §9.2 cash billing/change. This is a frontend billing improvement on the existing server-authoritative cash checkout.

- Current bill offers Exact amount plus round-up suggestions to the next 100/500 amount units. Duplicate suggestions and values above the existing cash API limit are omitted. These are numeric entry helpers, not a denomination inventory or proof that physical cash was received.
- Suggestions only fill Cash received. Cashiers must check physical cash before completing the sale. They do not POST, reserve stock, update the drawer, or finalize a sale.
- Plain non-negative decimal cash is parsed to integer cents, with at most 12 integer digits and two fractional digits, matching the current cash API limit. Scientific notation, excess precision and unsafe/oversized amounts are rejected locally.
- Feedback distinguishes missing/invalid cash, cash still due and sufficient estimated tender/change. Short/missing/invalid entries show no numeric estimated change rather than suggesting a zero-change completed tender.
- New Complete cash sale requests are blocked locally until valid cash covers the current valid bill estimate. A price/quantity change recomputes readiness while preserving typed cash. The server still recalculates price, approved discounts, tax, total and actual change and may reject stale estimates.
- Financial locks disable shortcuts and cash entry. Empty/invalid bills cannot prepare a shortcut. Existing saved sale retries bypass new-entry estimate checks and replay the original session/body/key; no attempt is changed or acknowledged by a shortcut.
- Zero-total estimates require explicit cash entry (Exact amount can fill 0.00). A zero estimate alone does not submit a sale.

## Architecture and operation

No backend endpoint, schema, grant, price rule or Spring Data JPA module change is required. A pure cashTenderMath helper owns bounded decimal parsing, status and suggestions. CashTender renders accessible status/help and responsive controls inside the existing locked billing fieldset. App gates only new submissions using the same current bill estimate; original pending-request recovery remains independent. Existing sidebar/current-bill anchors, mobile navigation and payment-provider gates remain unchanged.

Rebuild the frontend, open your named cashier session, add products and enter cash or choose a shortcut. Verify the Cash still due message for an insufficient amount; Complete cash sale enables once cash covers the estimated total. Complete only after receiving the physical cash. Use the returned server receipt for final change. Rounded buttons use the existing amount unit; no new currency or legal-tender configuration is introduced.

## Validation

Unit tests cover exact decimal cents, invalid/exponent/precision input, missing/short/sufficient state, exact/rounded suggestions, duplicates, API amount limits and explicit zero cash. Component tests cover no-submit shortcuts, short-cash feedback, financial locks, empty/invalid bills and recomputation after bill changes. An App regression verifies a short entry cannot reach /sales and an explicit exact-amount selection submits the selected value through the existing checkout flow. Existing saved-sale retry tests remain in the full suite. Backend/frontend regression suites and production build are run.

Browser/mobile/tablet visual and physical cash/device acceptance remain outstanding; component tests use jsdom. This does not add external tender verification, split payment, a cash denomination counter, online paid checkout or offline operation.
