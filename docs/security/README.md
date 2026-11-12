# Security controls and verification

Backend Spring Security, service ownership checks and tenant-scoped data access are authoritative. Frontend navigation
visibility is not an authorization boundary. Keep tenant/branch scoping on backend reads and mutations, and keep
authentication, refresh-token and CSRF behavior covered by the backend regression suites.

Security verification is maintained in the existing Maven unit/integration tests and the GitHub Actions workflows.
Do not add a parallel source-scanning or audit-script framework. The protected production tenant-integrity preflight
remains separate from ordinary CI and must never expose production credentials to pull-request workflows.

See .github/workflows/security-checks.yml, .github/workflows/release-validation.yml and
.github/workflows/phase-2-production-preflight.yml.
