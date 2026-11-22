# Receipt identity and itemized presentation

Traces requirements §9.2 and SRS POS-009/POS-026/POS-027/POS-035/POS-046. New paid cash receipts retain seller/outlet display identity and use an itemized responsive/printable preview. This does not introduce statutory tax templates or payment methods.

## Identity capture and compatibility

V19 adds nullable orders.receipt_seller_name and receipt_outlet_name (160 characters each). New direct cash checkout captures both in its existing transaction. Reserved POS cash finalization captures them after its paid transition in the same JPA/JDBC transaction as inventory issue, receipt sequence and conditional invoice. Invoice creation uses the same captured seller/outlet values. There is no extra receipt number or audit-copy action.

Snapshots are captured at payment, not unpaid draft creation. Replay returns the stored receipt before document writes; reprint keeps identity/number/time. Later vendor/outlet renames cannot relabel these receipts. Transaction failure rolls snapshots back with the sale/stock/sequence.

Receipt GET/reprint add these nullable fields and issued_at. For reserved sales, issued_at is the saved paid status_changed_at, not the earlier draft created_at. Existing direct/legacy paid orders without a separate status time use their saved created_at. Existing authorization, tenant/branch receipt scope and copy audits remain unchanged.

There is deliberately no historical backfill from current mutable names. A legacy receipt can use its already stored invoice seller/outlet snapshots in the UI. If neither receipt nor invoice has that identity, it remains explicitly not recorded. Reading/reprinting legacy receipts never writes guessed snapshots. These are existing vendor/outlet display names, not new verified legal/GST/address fields; statutory invoice configuration/approval remains a release gate.

## UI and printing

The shared SaleReceipt component renders saved seller/outlet, original branch/receipt number, sale reference, issue time (labelled local), line quantities/unit prices, saved discounts, tax/rates, saved line totals and received cash/change. Discounts are already included in unit/line totals and are not subtracted again. No current catalogue pricing or total recomputation occurs in receipt rendering.

The line table scrolls locally on narrow screens; metadata/actions stack. Print CSS fits table width to the printable area, repeats table headings and avoids splitting individual rows; no particular thermal printer/paper width is certified. Linked invoice identities remain separate. Missing legacy values are not rendered as zero or a platform merchant name.

Current receipt appears in the existing Sell sidebar only when mounted. App retains the original audited reprint/print commands, permission/CSRF behavior, stock-return action and parent busy/error controls. Printing still requires the successful copy response; generating a copy/opening the print dialog does not prove physical printing. The existing sale/refund print selector remains separate, and menus/errors/actions stay out of printed receipts.

## Local review and tests

Rebuild/restart backend for V19 and rebuild frontend. Complete or recover a paid POS receipt, check itemization and use Print receipt copy for an audited copy. A new sale made before a vendor/outlet rename must continue to show its original saved identity. For reserved sales, the issue time is the payment transition. Legacy guest receipts may explicitly lack names; use recorded source documents rather than substituting current tenant data.

Backend regressions cover direct receipt snapshots across rename/retry/copy, tenant isolation, reserved capture at payment with matching invoice identity/time, rollback, and legacy unknown preservation. Component tests cover line/tax/discount/cash presentation, immutable invoice fallback, missing/invalid historical metadata, copy identity and parent-owned print/error actions. Existing App audited-print success/denial tests remain in the full suite. Backend/frontend regression suites and production build are run.

Native browser/mobile/tablet, assistive-technology and physical printer/paper acceptance remain outstanding; component tests use jsdom. No PDF storage, legal invoice/credit-note compliance, online paid checkout or new tender verification is claimed.
