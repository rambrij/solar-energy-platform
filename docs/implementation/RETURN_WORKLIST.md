# Paged POS return worklist

Traces SRS POS-028/POS-033/POS-040 and requirements §9.2. This extends the existing return intake/review/inspection flow; it does not add a financial command.

`GET /api/vendor/pos/returns/queue?state=REQUESTED&from=2026-11-01&to=2026-11-30&reference=&page=0&size=12`

- Requires both `POS_VIEW` and `POS_RETURN_VIEW`. Persisted active employee membership supplies tenant and assigned branch for both rows and count. No session needs to be open; closed-shift cases remain searchable. Review/inspection/refund rights are separately enforced by existing command APIs.
- States: REQUESTED (default), APPROVED, REJECTED, INSPECTED, ALL. These describe stock intake, not refund/payment state.
- Inclusive UTC intake dates, at most 366 days; default through current UTC date with 29 preceding days. An explicit `to` also anchors the default `from`. Older cases require explicit dates; this is not an all-time backlog counter.
- Case-insensitive return ID or original order ID prefix, max 36 ASCII letters/digits/hyphens. SQL wildcard input is rejected. No receipt-number, buyer, note or employee search is introduced.
- Page 0–10000, size 1–48. Stable oldest-intake-first ordering by created time then ID. Approval does not move intake time. Equal timestamps do not establish causal sequence. This is live offset pagination, so additions/state changes may shift pages; reload page zero when necessary.
- Response contains branch, resolved filters, pagination totals and rows with `id`, `orderId`, `state`, `createdAt` only. No requester, reasons, proof, session, retry key, lines or refund data in summaries. Opening a row separately loads current details from the existing authorized API.

Implementation stays in the returns module: Spring Data JPA constructor projection and explicitly scoped data/count queries in one read-only repeatable-read transaction. V15 adds only `ix_return_queue(tenant_id,branch_id,created_at,id)`; existing rows/events and migrations are unchanged. The legacy top-20 `GET /returns?state=...` route remains compatible; the UI uses the new worklist.

The responsive worklist loads only on staff request, pins server-resolved UTC dates while paging, and clears stale summaries on filter changes, command progress, disable/revision and errors. Late responses after revision/unmount are ignored. Selecting a summary passes only its reference into the existing detail recovery path. Reading summaries never clears a saved return command; acknowledgement still requires loading current case details and an explicit staff action.

## Local use and validation

Rebuild/restart the backend to apply V15, rebuild frontend, sign in as a named employee with existing return-view grants. Open POS returns, choose state/date/reference filters and Load return queue; use Next/Previous then Open return to load current case details. New production/demo grants are not required and no permissions are broadened automatically.

Unit tests cover canonical scope/default UTC dates and bounds. HTTP/database regression covers 25 real partial return intakes across three pages, equal-time ID ordering, closed shifts, filters/current state changes, cross-tenant and cross-branch data/count isolation, forged scope parameters, denied/invalid requests, and unchanged stock/cash after reads. Component tests cover explicit load, resolved-date paging, reference-only selection, empty/error/reset/stale-response handling and existing independent review/inspection workflows. Native browser/scanner/printer and visual-device validation are not claimed.
