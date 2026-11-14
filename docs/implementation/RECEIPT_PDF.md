# Audited receipt PDF copies

Traces SRS DOC-001/DOC-003 and POS-026/POS-027/POS-036/POS-040. Generates a downloadable COPY of an existing paid POS receipt; it never creates a sale, receipt number, invoice, credit note or tender.

`POST /api/vendor/pos/receipts/{orderId}/document` with JSON `{ "reason": "Customer requested a copy" }` and CSRF.

## Scope and audit

Requires POS_VIEW + POS_RECEIPT_VIEW + POS_RECEIPT_REPRINT and persisted enabled employee/active tenant membership. The existing retail receipt application API enforces canonical tenant/assigned branch and PAID ownership. Closed-shift receipts remain available. Client tenant/branch fields are not accepted or trusted.

Reason: 1–250 plain characters after validation; no control characters. Do not enter buyer/contact/payment credentials. The private audit stores actor, tenant/branch/order, reason, creation time, exact byte length and SHA-256 hash with a new document reference. Reasons/employee usernames are not included in the customer PDF. V21 adds only receipt_document_audit with composite ownership FKs and a lookup index.

Success returns application/pdf, attachment filename, no-store/nosniff and X-Document-Reference. The audit is flushed/committed before the response is delivered. No file is retained server-side, only its generation facts/hash. Every successful request creates a distinct copy/audit; this is not a charge command or idempotent financial retry. A network error, discarded UI response or browser save failure may leave a generation audit. Retrying can create another copy, not another sale. Generation does not prove saving, delivery or printing.

## Rendering and module boundaries

The documents module depends on the public retail receipt API and owns a typed saved-data mapper, OpenPDF renderer and private Spring Data JPA audit repository. It imports no foreign persistence classes or JDBC. A repeatable-read transaction covers scoped saved receipt data and its audit. ArchUnit guards document internals and module cycles. Existing browser copy/print APIs remain their own output actions; no version adapter or data compatibility lookup is introduced.

The PDF includes COPY on the receipt heading/footer, original branch/number/order reference, saved seller/outlet identity, original issue time and generated time in UTC, saved item quantities/unit/discount/tax/total amounts, cash/change, linked invoice reference and document reference. No current catalogue or price recomputation is used. Existing immutable invoice identity can supply a missing saved receipt label; unknown facts remain Not recorded, never current mutable names. This is not a new statutory invoice template or legal identity verification.

OpenPDF 3.0.5 and its font module are pinned from the [upstream release](https://github.com/LibrePDF/OpenPDF/releases/tag/3.0.5). Liberation Sans is embedded. Unsupported glyphs fail explicitly rather than silently disappear; the existing browser print remains available. A broader approved font/script policy is open (complex-script and full international glyph support are not claimed). PDFs use A4, repeated table headings and page/copy footers, and support 1–100 saved lines, max 2 MB output. No thermal paper/printer certification is claimed.

## UI and refactors

Rebuild/restart backend for V21 and rebuild frontend. Open a paid Sale receipt and use Download a PDF copy: provide the reason, then Generate PDF copy. No protected document fetch happens automatically. The control freezes duplicate clicks while pending, validates content type/reference/size, and ignores responses after receipt/disable/unmount changes. It never clears a financial saved attempt or updates parent receipt/payment state. The notice distinguishes generation from browser saving/printing.

CSV and PDF now use one fileDownload browser-save helper; existing CSV callers/tests are updated directly and the old CSV-specific save helper is removed, not re-exported as an alias. Existing receipt audited-print actions, menus and financial flows remain unchanged. Reasons and download controls are excluded from the printed HTML receipt with the existing action area.

## Validation and remaining work

Renderer units extract PDF text, check saved identity/amounts and Unicode coverage, paginate 100 long lines, verify per-page copy footers, and reject unsupported glyphs/empty data. Service units reject invalid reasons/references before reads and prevent missing/foreign receipt access. HTTP/database tests verify permissions/CSRF, closed-shift access, preserved seller after rename, private reason exclusion, exact returned-byte audit hashes/length, scope rejection and no sale/sequence/stock/drawer mutation. Client tests verify CSRF requests, response validation, explicit generation, denied/unknown states, no download from abandoned responses and saved-attempt preservation. Existing backend/frontend regressions and production build are run.

A generated long receipt is rendered for visual review of first/last pages; PDF text checks alone are not treated as layout proof. Real browser/mobile/tablet, screen-reader and physical printer acceptance remain open. Production rate limiting, document retention/object storage, broader fonts/scripts and statutory invoice configuration require separate decisions. Production online/UPI/card/split tender work remains unchanged.
