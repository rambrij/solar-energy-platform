# P2-01 Browser E2E coverage and traceability

**Status:** IN PROGRESS — implementation added; final CI validation pending.  
**Branch:** `feature/develop-authorization-gap-analysis`  
**Runtime:** Playwright Chromium against the disposable Docker Compose stack, local Spring profile, and MySQL 8.4.

## Test environment and safety boundary

- The browser job starts a fresh Compose project and removes its volume after execution. It does not connect to a developer database.
- `SPRING_PROFILES_ACTIVE=local` loads the existing V39/V51+ demo fixtures. Credentials are sourced from `docs/demo/README.md`; they are development-only and must never be used in production.
- Login, refresh-cookie issuance, access-token use, persisted role assignment, controller method security and tenant scope use the real application path. No test-only authentication bypass or production API is added.
- A deterministic custom Sales role is created through the existing Vendor Role API and assigned to the seeded Sales Executive identity. It grants only `PRODUCT_VIEW`; product creation must return 403 without changing the product list.
- The browser job depends on the existing backend H2/MySQL and frontend jobs. It uploads the HTML report, screenshots, videos and traces even on failure. P0-06 and P1-04 retain their own acceptance criteria.

## Route / action / permission / result / test matrix

| Actor / journey | Browser route or API | Required permission / scope | Expected result | Playwright test |
|---|---|---|---|---|
| Vendor Admin catalogue | `/vendor/pos/products` | `PRODUCT_VIEW`, active Vendor membership | Route and catalogue page allowed | Vendor Admin route/history/mobile test |
| Vendor Admin tenant filter | `GET /api/v1/vendor/products?tenantId=920001` | `PRODUCT_VIEW`; tenant from authenticated membership | Forged tenant query is ignored; response matches own-tenant query | Vendor Admin tenant-filter resistance |
| Custom Sales role | `/vendor/pos/products` | Persisted custom role grants `PRODUCT_VIEW` only | Direct route and product list allowed | Custom Sales role test |
| Custom Sales product mutation | `POST /api/v1/vendor/products` | Requires `PRODUCT_CREATE`, not granted | HTTP 403; product IDs unchanged | Custom Sales role test |
| Cashier session entry | `/vendor/pos` | `POS_VIEW`, `POS_SESSION_OPEN` | Session-selection page allowed | Cashier mobile navigation test |
| Cashier administration routes | `/vendor/pos/roles`, `/vendor/pos/products` | `ROLE_VIEW`, `PRODUCT_VIEW` not granted | Navigation entries absent | Cashier mobile navigation test |
| Accountant reporting | `/vendor/pos/sales-insights` | `POS_REPORT_VIEW` | Reporting page allowed | Accountant test |
| Accountant inventory | `GET /api/v1/vendor/inventory/receipts` | Requires `INVENTORY_VIEW`, not granted | HTTP 403 | Accountant test |
| Platform Super Admin | `/platform` | Persisted platform grants; no vendor membership | Platform workspace allowed | Platform boundary test |
| Platform-to-vendor boundary | `/vendor/pos` | Vendor actor context required | Workspace access denied | Platform boundary test |
| Customer purchase history | `/marketplace/purchases` | `CUSTOMER` actor; self-owned resources | Customer purchase page allowed | Customer test |

| Cashier completed sale | `/vendor/pos` → assigned register → current bill → receipt | `POS_VIEW`, `POS_SESSION_OPEN`, `POS_SALE_CREATE`, `POS_TENDER_CASH_RECORD`, `POS_RECEIPT_VIEW`; persisted register assignment | Cash sale is finalized and original receipt is displayed | Cashier POS cash-sale test |
| Permission revocation | Existing Sales token → `GET /api/v1/vendor/products` → reload protected product route | `PRODUCT_VIEW` removed from active persisted role assignment | Existing bearer request returns 403; refreshed session no longer renders protected page | Revoked custom Sales role test |
| Expired refresh session | Reload protected `/vendor/pos` with refresh rejected | Valid refresh family required | Local actor cleared; sign-in boundary shown | Session expiry test |
| Responsive navigation | `/vendor/pos/products`, 390×844 viewport | Route's normal permission | Mobile menu opens and only allowed links are shown | Vendor Admin + Cashier tests |
| Browser history / refresh | Catalogue → Sales History → Back → Reload | Existing route permissions | Deep link and browser history restore expected page | Vendor Admin route/history test |

## Execution and completion rules

- Local run: start the repository Compose stack with the local profile and the documented demo-only credentials, install pinned `@playwright/test@1.52.0`, install Chromium, then run `npm run test:e2e` from `frontend`.
- CI run: `browser-e2e` in `.github/workflows/pos-tests.yml` waits for backend H2/MySQL and frontend jobs, starts a fresh MySQL 8.4 local-profile stack, waits for `/api/v1/auth/csrf`, runs Playwright, and uploads artifacts.
- P2-01 is not COMPLETE until the browser job passes on the final branch head and the Playwright report/traces are inspected. A passing browser job does not close P0-06's full Role × Endpoint matrix or P1-04's exhaustive page/tab/action audit.
- No backend production code, authorization policy, migration, schema, or source-of-truth product/SRS document is changed by this test addition.
