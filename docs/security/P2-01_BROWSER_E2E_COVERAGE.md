# P2-01 Browser E2E coverage and traceability

**Status:** COMPLETE — VERIFIED 2026-11-22 on final tested branch head `9d239a6cf93f085657d88d3103c149507da76749`.  
**Branch:** `feature/develop-authorization-gap-analysis`  
**Runtime:** Playwright Chromium against the disposable Docker Compose stack, local Spring profile, and MySQL 8.4.

## Test environment and safety boundary

- The browser job starts a fresh Compose project and removes its volume after execution. It does not connect to a developer database.
- `SPRING_PROFILES_ACTIVE=local` loads the existing V39/V51+ demo fixtures. Credentials are sourced from `docs/demo/README.md`; they are development-only and must never be used in production.
- Login, refresh-cookie issuance, access-token use, persisted role assignment, controller method security and tenant scope use the real application path. No test-only authentication bypass or production API is added.
- A deterministic custom Sales role is created through the existing Vendor Role API and assigned to the seeded Sales Executive identity. It grants only `PRODUCT_VIEW`; product creation must return 403 without changing the product list.
- The browser job depends on the existing backend H2/MySQL and frontend jobs. It uploads the HTML report and available test-result attachments. Failed tests produce screenshots, videos and traces; a clean passing run may have no failure-only attachments. P0-06 and P1-04 retain their own acceptance criteria.

## Route / action / permission / result / test matrix

| Actor / journey | Browser route or API | Required permission / scope | Expected result | Playwright test |
|---|---|---|---|---|
| Vendor Admin catalogue | `/vendor/pos/products` | `PRODUCT_VIEW`, active Vendor membership | Route and catalogue page allowed | Vendor Admin route/history/mobile test |
| Vendor A foreign product IDOR | `PUT /api/v1/vendor/products/{vendorBProductId}` | `PRODUCT_UPDATE`; tenant from Vendor A authenticated membership | Non-disclosing 404; Vendor B product snapshot/version remains unchanged | Vendor Admin cross-tenant resistance test |
| Vendor Admin tenant filter | `GET /api/v1/vendor/products?tenantId=920001` | `PRODUCT_VIEW`; tenant from authenticated membership | Forged tenant query is ignored; response matches own-tenant query | Vendor Admin tenant-filter resistance |
| Custom Sales role | `/vendor/pos/products` | Persisted custom role grants `PRODUCT_VIEW` only | Direct route and product list allowed | Custom Sales role test |
| Custom Sales product mutation | `POST /api/v1/vendor/products` | Requires `PRODUCT_CREATE`, not granted | HTTP 403; product IDs unchanged | Custom Sales role test |
| Cashier session entry | `/vendor/pos` | `POS_VIEW`, `POS_SESSION_OPEN` | Session-selection page allowed | Cashier mobile navigation test |
| Cashier direct deep link | `/vendor/pos/products` | Requires `PRODUCT_VIEW`, not granted | Route renders Access denied even when URL is entered directly | Cashier mobile navigation test |
| Cashier administration routes | `/vendor/pos/roles`, `/vendor/pos/products` | `ROLE_VIEW`, `PRODUCT_VIEW` not granted | Navigation entries absent | Cashier mobile navigation test |
| Accountant reporting | `/vendor/pos/sales-insights` | `POS_REPORT_VIEW` | Reporting page allowed | Accountant test |
| Accountant inventory | `GET /api/v1/vendor/inventory/receipts` | Requires `INVENTORY_VIEW`, not granted | HTTP 403 | Accountant test |
| Platform Super Admin | `/platform` | Persisted platform grants; no vendor membership | Platform workspace allowed | Platform boundary test |
| Platform-to-vendor boundary | `/vendor/pos` | Vendor actor context required | Workspace access denied | Platform boundary test |
| Customer cross-workspace boundary | `/vendor/pos` | Vendor actor context required | Access-denied boundary | Customer direct vendor POS test |
| Vendor cross-workspace boundary | `/platform` | Platform actor context required | Platform access denied | Vendor direct Platform URL test |
| Customer purchase history | `/marketplace/purchases` | `CUSTOMER` actor; self-owned resources | Customer purchase page allowed | Customer test |

| Cashier completed sale | `/vendor/pos` → assigned register → current bill → receipt | `POS_VIEW`, `POS_SESSION_OPEN`, `POS_SALE_CREATE`, `POS_TENDER_CASH_RECORD`, `POS_RECEIPT_VIEW`; persisted register assignment | Cash sale is finalized and original receipt is displayed | Cashier POS cash-sale test |
| Permission revocation | Existing Sales token → `GET /api/v1/vendor/products` → reload protected product route | `PRODUCT_VIEW` removed from active persisted role assignment | Existing bearer request returns 403; refreshed session no longer renders protected page | Revoked custom Sales role test |
| Expired refresh session | Reload protected `/vendor/pos` with refresh rejected | Valid refresh family required | Local actor cleared; sign-in boundary shown | Session expiry test |
| Responsive navigation | `/vendor/pos/products`, 390×844 viewport | Route's normal permission | Mobile menu opens and only allowed links are shown | Vendor Admin + Cashier tests |
| Browser history / refresh | Catalogue → Sales History → Back → Reload | Existing route permissions | Deep link and browser history restore expected page | Vendor Admin route/history test |

## Execution and completion rules

- Local run: start the repository Compose stack with the local profile and the documented demo-only credentials, install pinned `@playwright/test@1.52.0`, install Chromium, then run `npm run test:e2e` from `frontend`.
- CI run: `browser-e2e` in `.github/workflows/pos-tests.yml` waits for backend H2/MySQL and frontend jobs, starts a fresh MySQL 8.4 local-profile stack, waits for `/api/v1/auth/csrf`, runs Playwright, and uploads artifacts.
- **P2-01 acceptance:** COMPLETE on the final tested branch head only after the full Chromium suite, frontend CI, backend H2, backend MySQL 8.4, browser E2E and Playwright artifact review all passed. This does not close P0-06's full Role × Endpoint matrix or P1-04's exhaustive page/tab/action audit.
- **2026-11-22 CI failure analysis:** workflow [#44990198027](https://github.com/rambrij/solar-energy-platform/actions/runs/44990198027) passed frontend, backend H2 and backend MySQL 8.4 jobs; the Playwright job failed only in the Customer → `/vendor/pos` deep-link test. The shell rendered the expected generic `Access denied` state because the route permission guard denied the customer before the actor-specific message was rendered. The original assertion incorrectly required the actor-specific message. Updated the browser assertion to verify denial and absence of protected POS/product content (commit `d807f5c211e045b40175ae21dd90374914dce4e9`). The full Playwright suite and all CI jobs must pass again on this new head before completion. Failure screenshots, videos, traces and HTML report from run #44990198027 were downloaded and inspected; they confirm the mismatch is the assertion, not successful access to POS. Rerun: [#44991134966](https://github.com/rambrij/solar-energy-platform/actions/runs/44991134966) (pending at documentation update).
- **Final verification (2026-11-22): COMPLETE — VERIFIED.** Branch-head workflow [#44991165423](https://github.com/rambrij/solar-energy-platform/actions/runs/44991165423) on `9d239a6cf93f085657d88d3103c149507da76749` passed frontend, backend H2, backend MySQL 8.4 and Browser E2E (Playwright / MySQL 8.4). Frontend: 62 test files / 315 tests passed; `npm run build` passed. Backend H2: 662 tests, 0 failures, 0 errors, 1 skipped; MySQL 8.4: 662 tests, 0 failures, 0 errors, 0 skipped; both Maven builds succeeded. Playwright Chromium: 11/11 expected, 0 unexpected, 0 flaky, 0 skipped. The HTML report was downloaded and its embedded `report.json` verified to contain those results and the exact tested commit. The failed-run screenshot and both trace archives from [#44990198027](https://github.com/rambrij/solar-energy-platform/actions/runs/44990198027) were also inspected: the customer was shown the generic access-denied page with no POS content, confirming the initial failure was an over-specific assertion. The corrected test now asserts the stable denial state and absence of protected POS/product headings. The successful run's report artifact contains the HTML report only; there were no failure-only screenshots/traces to inspect because all 11 tests passed. Artifact: [Playwright report](https://github.com/rambrij/solar-energy-platform/actions/runs/44991165423/artifacts/14741918540).
- No backend production code, authorization policy, migration, schema, or source-of-truth product/SRS document is changed by this test addition.
