# Implementation conventions

- Application source and automated tests stay in `backend/` and `frontend/`.
- Database changes stay in versioned Flyway migrations. Never edit an already-applied migration.
- Docker Compose is the supported local orchestration entry point.
- CI workflows stay under `.github/workflows/`; avoid duplicate root shell wrappers.
- Architecture decisions belong in `docs/architecture/`; security controls and evidence belong in `docs/security/`.

Run `docker compose up --build` for local development. Use
`docker compose -f docker-compose.yml -f docker-compose.test.yml up --build` for a disposable local test stack.
Run backend and frontend suites with Maven and npm as documented in the root README.

A green disposable CI run is not production data cleanliness or release approval.
