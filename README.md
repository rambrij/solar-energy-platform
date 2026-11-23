# Saumya Solar Marketplace and POS

A modular-monolith application for the solar marketplace, tenant-owned catalogues and inventory, customer carts and checkout drafts, store POS, returns/refunds, platform administration, and role-based authorization.

## Baseline status

- **Architecture:** modular monolith; this repository does not use microservices.
- **Technology:** Java 21, Spring Boot 3.5, Maven, React 19, TypeScript, Vite, Node.js 22, MySQL 8.4, JPA/JDBC, and Flyway.
- **Authorization:** backend-enforced, actor-separated authorization. Frontend permissions are an additional navigation/usability boundary only.
- **Development data:** the local Spring profile applies local-only demo seed migrations. Never enable this profile in production.
- **Database baseline:** the checked-in production schema currently uses 51 versioned Flyway migrations (40 SQL and 11 Java migrations) plus three local-only migrations. It is **not yet** the requested single V1__base_schema.sql baseline. Do not assume the migration chain has been consolidated. That work requires translation and MySQL 8.4 verification of Java migration behavior, ownership constraints, audit triggers, canonical permission data and local-profile seed boundaries before older migrations can be removed.

P0-02 service-boundary completeness and P2-02 exhaustive schema/UTC/optimistic-lock/branch-protection work remain separately tracked as open. A green regression workflow does not close those independent acceptance items.

## Architecture and repository layout

The backend is one Spring Boot application. Domain packages own their controllers, services and persistence boundaries:

- **backend/src/main/java/com/saumya/pos/identity** — global identity, login, refresh sessions, JWT, customer identity and MFA.
- **membership, security, tenant, platform** — vendor role assignments, permission policies, tenant lifecycle, platform identity and platform operations.
- **catalog, inventory, marketplace, cart, customer, checkout, retail, returns, documents, reporting, subscription** — implemented business domains.
- **backend/src/main/resources/db/migration** — production Flyway migrations.
- **backend/src/main/resources/db/local-migration** — local-profile demo data migrations.
- **backend/src/test/java** — backend unit, integration, architecture, authorization and database-contract tests.
- **frontend/src** — React workspaces, session and permission state, API clients, navigation and component tests.
- **frontend/e2e** — Playwright browser journeys.
- **frontend/scripts/check-authorization-registry.mjs** — frontend/backend authorization registry checks.
- **backend/scripts/generate-p0-06-runtime-report.py** — CI runtime authorization evidence report.
- **.github/workflows/pos-tests.yml** — H2, MySQL 8.4, frontend and Playwright validation.
- **docs/demo/README.md** — complete local demo identity, credential, role and permission inventory.
- **saumya-solar-energy-solution-requirements.md** and **saumya-solar-production-ready-srs-hld.md** — business and technical source documents.

## Prerequisites

For the Docker route, install Git and Docker Desktop (Linux containers enabled). For direct development, install:

| Tool | Version used by this repository/CI |
|---|---:|
| Java | 21 |
| Maven | 3.9.x |
| Node.js | 22 |
| MySQL | 8.4 |
| Docker Compose | Compose v2 |
| Playwright Chromium | 1.52.0 in CI |
| axe-core Playwright | 4.14.0 in CI |

The backend Maven build downloads dependencies. The frontend uses the committed npm lockfile; use npm ci, not npm install, for repeatable dependency installation.

## Environment variables

Copy .env.example to .env in the repository root. Keep .env local and never commit secrets.

| Variable | Purpose | Local behavior |
|---|---|---|
| DB_PASSWORD | MySQL application account password | Required by Docker Compose. |
| MYSQL_ROOT_PASSWORD | Disposable local MySQL root password | Required by Docker Compose. |
| SPRING_PROFILES_ACTIVE | Spring profile | Set to local to enable local demo seeds. |
| AUTH_JWT_SECRET | Base64 HMAC key, minimum 256 bits | May be blank only in local/test; the app generates an in-memory random key. Tokens will not survive a backend restart when this key is generated. Supply a private stable secret for persistent local sessions. A secret is mandatory outside local/test. |
| PAYMENTS_MOCK_ENABLED | Enables the mock payment adapter | Defaults to false. Enable only for isolated local/test demonstrations; never use a mock provider for real transactions or production. |

The Compose application uses the internal MySQL service address mysql:3306. The Compose override publishes MySQL to the host at 127.0.0.1:3307. Spring Boot does not automatically load the repository .env file when running directly from a shell; export the variables in that shell.

## Local infrastructure and URLs

The checked-in Compose stack contains three services:

| Service | Host access | Purpose |
|---|---|---|
| Frontend (Nginx) | http://localhost:8080 | React application and /api reverse proxy. |
| Backend (Spring Boot) | Container port 8080; not separately published by Compose | REST API, actuator and application services. |
| MySQL 8.4 | 127.0.0.1:3307 | Local database; container port 3306. |

The backend health endpoint is /actuator/health on the backend service. Compose does not publish a separate host port for the backend actuator. The browser-facing authentication/CSRF endpoint is available through the frontend proxy at http://localhost:8080/api/v1/auth/csrf.

Mailpit, Redis, MinIO/S3, a message broker and an asynchronous worker are **not** included in the current Compose stack. Do not expect a local mail inbox or storage/queue service.

## Clean local start with Docker Compose

> **Destructive reset:** docker compose down -v deletes the current Compose project's named MySQL volume, including tenants, identities, products, orders, financial/audit history and demo data. Use it only when the selected Compose project is a disposable local database. Never run it against staging, production, shared or valuable data.

From the repository root, in PowerShell:

~~~powershell
Copy-Item .env.example .env
# Edit .env and replace DB_PASSWORD and MYSQL_ROOT_PASSWORD.
# Keep SPRING_PROFILES_ACTIVE=local for local demo seed data.

docker compose down -v
docker compose up --build -d
docker compose ps
docker compose logs --tail 200 mysql
docker compose logs -f backend
~~~

Or, from Bash:

~~~bash
cp .env.example .env
# Edit .env and replace the local database passwords.
docker compose down -v
docker compose up --build -d
docker compose ps
docker compose logs --tail 200 mysql
docker compose logs -f backend
~~~

MySQL must become healthy before the backend starts. The backend applies Flyway migrations at startup and validates the schema; the frontend is served by Nginx. Confirm successful Spring Boot startup in the backend logs, then open http://localhost:8080.

### Flyway and database reset

The current production migration locations contain 51 versioned migrations: 40 SQL resources in backend/src/main/resources/db/migration and 11 Java migrations in backend/src/main/java/db/migration. The local profile adds three local-only SQL migrations under backend/src/main/resources/db/local-migration (V39, V51 and V53). The local seed creates the three vendor fixtures, current demo identities and supported tenant-owned role assignments.

This is the current migration state, not the requested single-V1 end state. Until a one-file baseline is implemented and verified on an empty MySQL 8.4 database, use the checked-in chain. Do not run flyway repair, edit migration checksums, delete migration sources or manually alter flyway_schema_history. See [Clean Development Database Setup](docs/implementation/CLEAN_DEVELOPMENT_DATABASE.md) for the existing chain's reset and seed details.

## Run backend and frontend directly

This option uses the Compose MySQL service but runs Java and Vite on the host. Stop Compose's frontend and backend first to free host port 8080:

~~~powershell
docker compose stop frontend backend
docker compose up -d mysql

# Set these values from your local .env file:
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3307/solar'
$env:DB_USER = 'solar'
$env:DB_PASSWORD = '<the DB_PASSWORD value from .env>'
$env:SPRING_PROFILES_ACTIVE = 'local'

cd backend
mvn spring-boot:run
~~~

In a second terminal:

~~~powershell
cd frontend
npm ci
npm run dev
~~~

Vite normally serves http://localhost:5173 and proxies /api to the backend at http://localhost:8080. The Compose frontend uses host port 8080 only when the direct backend is stopped.

## Tests and validation

Run commands from the repository root unless a working directory is shown.

### Backend: H2

~~~bash
mvn -B -f backend/pom.xml clean test
~~~

The normal integration-test configuration uses an isolated H2 in-memory database in MySQL compatibility mode unless TEST_DB_URL is set. H2 is the fast regression lane; it is not proof of MySQL-specific constraints, triggers or migration behavior.

### Backend: MySQL 8.4

Start the Compose MySQL service and create the disposable test schema:

~~~powershell
docker compose up -d mysql
docker compose exec mysql mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS solar_test"
~~~

Then set the test connection to the local MySQL 8.4 service (replace the password with MYSQL_ROOT_PASSWORD from .env):

~~~powershell
$env:TEST_DB_URL = 'jdbc:mysql://127.0.0.1:3307/solar_test'
$env:TEST_DB_USER = 'root'
$env:TEST_DB_PASSWORD = '<the MYSQL_ROOT_PASSWORD value from .env>'
mvn -B -f backend/pom.xml clean test
Remove-Item Env:TEST_DB_URL, Env:TEST_DB_USER, Env:TEST_DB_PASSWORD
~~~

The MySQL persistence tests create isolated schemas and therefore require a test-only user with database-creation privileges. Use only a disposable local MySQL 8.4 instance.

### Frontend tests and production build

~~~bash
cd frontend
npm ci
npm test
npm run build
~~~

npm test runs Vitest and the authorization registry checker. npm run build runs tsc --noEmit followed by the Vite production build.

### Playwright browser tests

Playwright and axe-core are pinned and installed by the CI browser job but are not regular dependencies in the checked-in package.json. To mirror CI locally:

~~~bash
cd frontend
npm ci
npm install --no-save --package-lock=false @playwright/test@1.52.0 @axe-core/playwright@4.14.0
npx playwright install --with-deps chromium
npm run test:e2e
~~~

Start the Compose application with a disposable local database before the browser run. Browser tests expect the local-profile seeded actors and a base URL of http://127.0.0.1:8080. For tests that exercise mock payment paths, set PAYMENTS_MOCK_ENABLED=true in the local .env and restart the local stack. Do not enable that setting outside local/test. Playwright uses Chromium, one worker, trace/screenshot/video on failure and an HTML report.

### Complete local validation sequence

1. Reset only the disposable Compose volume and start MySQL 8.4.
2. Run the backend H2 clean test suite.
3. Run the backend MySQL 8.4 clean test suite against solar_test.
4. Run frontend npm ci, npm test and npm run build.
5. Start/rebuild the app on the local profile, install the pinned Playwright packages and Chromium, then run npm run test:e2e.
6. Confirm Docker service health, the auth/CSRF endpoint, permitted/denied actor journeys and application logs.
7. Review the P0-06 evidence report and uploaded CI artifacts.

CI is defined in [pos-tests.yml](.github/workflows/pos-tests.yml). It runs backend H2 and MySQL 8.4 matrices, focused security/schema gates, frontend tests/build and a dependent Playwright/MySQL 8.4 browser job. A local run does not replace branch-protection or GitHub Actions required-check verification.

## Local demo users and actor matrix

All credentials are **public local-development fixtures only**. Never deploy the local profile or these accounts to a public environment. Exact passwords for every seeded identity, including deferred identities, are maintained in [docs/demo/README.md](docs/demo/README.md).

| Scope / tenant | Actor / role | Login | Local-only password | Accessible area (subject to resource assignments and backend policy) |
|---|---|---|---|---|
| Platform (global) | PLATFORM_SUPER_ADMIN | platform.superadmin@somya.local | Somya-Platform-2026! | Platform administration and platform-authorized operations only; no vendor POS/catalogue/customer grants. |
| Saumya Solar Rooftops (tenant 910001) | VENDOR_ADMIN | saumya.admin@somya.local | Somya-Saumya-ADM-2026! | Implemented tenant catalogue, role/employee administration, POS operations, inventory and reports. Branch/register/session restrictions still apply. |
| Saumya Solar Rooftops (tenant 910001) | POS_CASHIER | saumya.pos.cashier@somya.local | Somya-Saumya-POS-2026! | Assigned-register POS session, sale/tender and receipt operations; no role administration or inventory receiving. |
| Saumya Solar Rooftops (tenant 910001) | SALES_MANAGER | saumya.sales.manager@somya.local | Somya-Saumya-SM-2026! | POS/report read scope; no implied mutation rights. |
| Saumya Solar Rooftops (tenant 910001) | INVENTORY_MANAGER | saumya.inventory.manager@somya.local | Somya-Saumya-IM-2026! | Product/inventory read and inventory receiving within tenant/branch rules. |
| Saumya Solar Rooftops (tenant 910001) | ACCOUNTANT | saumya.accountant@somya.local | Somya-Saumya-ACC-2026! | POS financial reports and permitted refund viewing; not inventory receiving. |
| Helio Rural Energy (tenant 920001) | VENDOR_ADMIN | helio.admin@somya.local | Somya-Helio-ADM-2026! | Tenant-local catalogue, role/employee administration, POS and reports. |
| Helio Rural Energy (tenant 920001) | POS_CASHIER | helio.pos.cashier@somya.local | Somya-Helio-POS-2026! | Assigned-register POS session and sale operations only. |
| Lumina Solar Lighting (tenant 930001) | VENDOR_ADMIN | lumina.admin@somya.local | Somya-Lumina-ADM-2026! | Tenant-local catalogue, role/employee administration, POS and reports. |
| Lumina Solar Lighting (tenant 930001) | POS_CASHIER | lumina.pos.cashier@somya.local | Somya-Lumina-POS-2026! | Assigned-register POS session and sale operations only. |
| Global customer | CUSTOMER | customer.asha@somya.local | Somya-Asha-Buyer-2026! | Own customer cart, orders and paid receipts; no access to another customer's data. |
| Global customer | CUSTOMER | customer.rahul@somya.local | Somya-Rahul-Buyer-2026! | Own customer resources; the seeded vendor relationship is Helio Rural Energy only. |

The same user may have Customer and Vendor identity records only when explicitly provisioned for both. Login must select one actor context; permissions are never merged. A Platform identity is separate and cannot switch into Vendor or Customer context. The local seed does not create a permanent Platform Support login. Sales Executive, Operations Manager, Service Manager, Service Engineer and Content Manager identities are deferred fixtures with **no active role assignment and no effective vendor permissions**.

## Security and operational notes

- Vendor permissions come from active tenant-owned role assignments and role permissions. There is no employee-level permission fallback. Missing or deferred assignments fail closed.
- Platform, Vendor and Customer actor authorities are separate. Tenant, branch, membership, register, cashier and session scope are resolved and checked on the server; client-provided identifiers are not authorization evidence.
- Frontend route/action checks improve usability. The backend remains authoritative and denies unauthorized API operations.
- The online payment simulation/mock adapter is not a production payment provider. Provider, tax, invoice and legal/commercial decisions remain subject to the approvals recorded in the requirements/SRS and authorization documents.
- Financial and audit history is restrictive/immutable in the database where defined. Do not delete rows manually to make tests or demo flows pass.
- Do not expose MySQL, actuator, demo credentials, mock providers or local-profile services to the public internet.

## Troubleshooting

| Symptom | Check |
|---|---|
| Frontend loads but API calls return 502 | Check docker compose ps and docker compose logs --tail 200 backend mysql. A stopped/failed backend or Flyway startup failure makes the Nginx API proxy return 502. |
| Backend fails during Flyway startup | Read the first migration exception. Check MySQL 8.4 health and local binlog settings. Do not use flyway repair, edit checksums or delete history rows to suppress a failure. |
| MySQL health check never becomes healthy | Inspect docker compose logs mysql; verify Docker has enough resources and .env has valid initial passwords. |
| MySQL says access denied after editing .env | MySQL initialization variables apply only to a new data volume. Existing users keep their existing passwords. |
| Port 8080 or 3307 is already allocated | Stop the conflicting local service or change the host-side port mapping and use the new URL. |
| Platform login is denied | Platform credentials are local-only. Confirm the local profile is active. Outside local/test, platform access requires the approved MFA configuration. |
| Demo accounts are absent | Confirm SPRING_PROFILES_ACTIVE=local and inspect the local Flyway migrations. The local seed runs only on a fresh migration history. |
| A cashier cannot open a register | Check the exact tenant/branch/register assignment and whether the register is occupied by an existing session. Resume the owning cashier's open session instead of deleting it. |
| A customer/vendor identity asks for actor context | This is expected for a dual identity. Select exactly one explicit context; the application does not merge grants. |

For implementation-specific API, permission and acceptance detail, use the documents under docs/implementation and docs/security, together with the two source-of-truth requirements documents.
