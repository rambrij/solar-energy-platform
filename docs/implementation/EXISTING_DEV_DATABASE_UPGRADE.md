# Existing Development Database Upgrade — Authorization and Standard Roles

This procedure upgrades an existing local MySQL 8.4 development database in place. It does not reset or recreate the database volume.

## Compatibility assessment

- P1-02's V54 migration is additive: it adds nullable `template_key` and `template_version` columns and a consistency check to `vendor_roles`. Existing custom roles and their permission/assignment rows remain unchanged; their template-origin columns remain NULL.
- Standard templates are catalogue definitions, not global authorities. New tenant provisioning materializes tenant-owned roles. Existing administrators can clone a template through the existing role-management API/UI; this creates a new tenant-owned role and does not rewrite existing roles.
- V43 and V49 are already-versioned migrations. Do not edit their contents, change their checksums, or run `flyway repair` as a routine upgrade step.
- V49 intentionally removes the legacy `employee_permissions` store and V45 synthetic per-employee `legacy-*` roles. Explicit custom roles are retained. The local demo profile subsequently provisions its demo users from the staged V39 seed through local V50. For non-demo employees who had only a V45 synthetic role, the old grants are not recoverable after V49 has succeeded; those employees correctly have zero effective permissions until an authorized administrator assigns an explicit tenant role. Do not restore direct permission fallback or guess which employee should become Vendor Admin.

## Before upgrading

1. Stop application writes or schedule a short development maintenance window.
2. Back up the database. From PowerShell at the repository root:

   ```powershell
   docker compose exec -T mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers solar' > .\solar-before-auth-upgrade.sql
   ```

3. Confirm the database volume is present and the Compose configuration targets the intended database:

   ```powershell
   docker compose config
   docker compose ps -a
   ```

## Special recovery for a previously failed V43

If `flyway_schema_history` contains version `43` with `success=0`, do not treat it as a normal upgrade. First back up the database and verify both the MySQL variable and trigger state:

```sql
SHOW VARIABLES LIKE 'log_bin';
SHOW VARIABLES LIKE 'log_bin_trust_function_creators';

SELECT TRIGGER_NAME, EVENT_MANIPULATION, EVENT_OBJECT_TABLE, ACTION_STATEMENT
FROM information_schema.TRIGGERS
WHERE TRIGGER_SCHEMA=DATABASE()
  AND TRIGGER_NAME IN ('trg_platform_audit_no_update','trg_platform_audit_no_delete');

SELECT version, success
FROM flyway_schema_history
WHERE version='43';
```

The local Compose configuration must show `log_bin_trust_function_creators=ON` after the MySQL container is recreated. If V43 is recorded as failed and **neither** expected trigger exists, run Flyway `repair` once against this same database (using the same Flyway version, connection and migration locations as the application) to remove the failed history entry, then restart the backend so V43 can run again. Do not run repair if either trigger already exists: the deployed V43 migration is not idempotent, and an existing trigger requires a reviewed reconciliation before retrying. Do not manually delete successful history rows or edit migration checksums.

## Apply the upgrade without deleting data

The checked-in Compose configuration enables `--log-bin-trust-function-creators=ON` for the local MySQL service so the least-privilege application account can install the V43 audit triggers when binary logging is enabled. An already-created MySQL container does not acquire a changed command until it is recreated. Recreate only the container; the named `mysql-data` volume remains attached:

```powershell
docker compose up -d --force-recreate mysql
docker compose ps
docker compose logs --tail 100 mysql
docker compose up --build -d backend frontend
docker compose logs -f backend
```

Wait for Flyway validation/migration and Spring Boot startup to complete before using the application. If the database is already at V54, Flyway should validate the existing history and perform no new schema migration.

**Never run** `docker compose down -v`, `docker volume rm`, database drop/recreate scripts, `flyway clean`, or manual deletion from `flyway_schema_history` as part of this upgrade.

## Verify database state

Connect to the `solar` schema and inspect migration history:

```sql
SELECT installed_rank, version, description, success, checksum
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT COUNT(*) AS tenant_roles FROM vendor_roles;
SELECT COUNT(*) AS role_permission_rows FROM vendor_role_permissions;
SELECT COUNT(*) AS active_role_assignments
FROM employee_role_assignments WHERE active = TRUE;

SELECT COUNT(*) AS roles_without_template_origin
FROM vendor_roles WHERE template_key IS NULL AND template_version IS NULL;

SELECT COUNT(*) AS inconsistent_template_origin
FROM vendor_roles
WHERE (template_key IS NULL) <> (template_version IS NULL)
   OR (template_version IS NOT NULL AND template_version < 1);
```

Expected for a healthy V54 schema: both template-origin columns exist; custom roles may have both values NULL; standard-template-derived roles have both values populated; the inconsistent count is zero. Existing tenant-owned role and assignment rows should remain present. A missing legacy table after successful V49 is expected.

For local demo tenants, V50 creates explicit demo roles/assignments from the V39 staged seed, and V53 adds local demo entitlements. Confirm those demo employees have active role assignments and register assignments where POS session permissions require them.

## Existing non-demo employees

If V49 already ran and an employee previously relied only on a synthetic `legacy-*` role, do not manually recreate that role or restore `employee_permissions`. Have an authorized tenant administrator create or clone an approved tenant role, review the exact permissions, and explicitly assign it. If the tenant has no administrator with effective `ROLE_CREATE`/`ROLE_ASSIGN` authority, stop and use the platform's controlled tenant-administration/provisioning process; do not grant permissions through SQL or promote an arbitrary employee.

A role-less employee receives no vendor permissions. Branch, register, POS session, tenant entitlement and resource-ownership checks continue to apply after role assignment.
