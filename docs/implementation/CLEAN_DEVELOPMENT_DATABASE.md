# Clean Development Database Setup

## Purpose

This repository is still in development. Authorization migrations and local seed scripts are maintained as a clean-install baseline; backward compatibility with earlier development databases is intentionally not supported.

Use this procedure when switching to the current RBAC implementation or when an earlier Flyway run left a failed/inconsistent schema. It deletes the local Compose database volume and rebuilds the schema from the single Flyway V1 baseline.

> **Destructive operation:** the reset permanently deletes every database, tenant, employee, role, permission, product, inventory row, order, payment, audit record and other data stored in this Compose MySQL volume. Confirm that the selected Compose project is only your disposable local development environment. Do not use this procedure against production, staging or any database containing data you need.

## 1. Update the feature branch

Run from the repository root in PowerShell:

```powershell
git fetch origin
git switch refactor/development-baseline-cleanup
git pull --ff-only origin refactor/development-baseline-cleanup
```

Confirm that the current checkout is the intended repository and branch:

```powershell
git status --short --branch
```

## 2. Check local configuration

Keep your existing ignored .env file, or create it from the tracked template:

```powershell
Copy-Item .env.example .env
```

Set DB_PASSWORD and MYSQL_ROOT_PASSWORD to local-only secrets. The local profile must be active:

```dotenv
SPRING_PROFILES_ACTIVE=local
```

Do not commit .env or use demo credentials outside local development.

## 3. Stop containers and delete the disposable database volume

Run:

```powershell
docker compose down -v
```

This removes the Compose MySQL named volume (mysql-data), not merely the MySQL container. Verify that no container from this Compose project is still running:

```powershell
docker compose ps -a
docker volume ls
```

Do not manually delete arbitrary Docker volumes. The command above is scoped to the current Compose project.

## 4. Build and start from an empty database

```powershell
docker compose up --build -d
docker compose ps
docker compose logs --tail 200 mysql
docker compose logs -f backend
```

The Compose MySQL command includes log-bin-trust-function-creators=ON for the append-only platform audit triggers when binary logging is enabled. A newly created container receives this setting. Flyway then creates the schema from the repository's V1 migration. Wait for the backend's successful Spring Boot startup before opening the application at http://localhost:8080.

If startup fails, stop and inspect the first Flyway or Spring exception. Do not run flyway repair, manually delete flyway_schema_history rows, or manually edit checksums as a workaround for this clean-install workflow.

## 5. Expected authorization and local seed sequence

V1 creates the schema, the tenant-owned role and assignment tables, both permission catalogues, and append-only platform audit triggers. The local callback seeds the demo tenants, branch stock locations, catalogue, inventory, identities, role assignments, and subscription entitlements. Supported role snapshots carry their template origin when inserted. Deferred demo roles remain unassigned.

Platform authority remains separate from vendor RBAC. No vendor role may contain PLATFORM_* permissions. Vendor effective permissions are resolved from active persisted tenant role assignments and role permission rows, filtered by the tenant entitlement policy and then constrained by authenticated tenant, branch, register and session assignments.

## 6. Verify database state

Connect to the solar schema and run:

```sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT tenant_id, role_key, template_key, template_version, active
FROM vendor_roles
ORDER BY tenant_id, role_key;

SELECT a.tenant_id, a.username, r.role_key, a.active
FROM employee_role_assignments a
JOIN vendor_roles r ON r.tenant_id=a.tenant_id AND r.id=a.role_id
ORDER BY a.tenant_id, a.username;

SELECT COUNT(*) AS employee_permissions_table
FROM information_schema.tables
WHERE table_schema=DATABASE() AND table_name='employee_permissions';

SELECT COUNT(*) AS vendor_platform_permissions
FROM vendor_role_permissions
WHERE permission LIKE 'PLATFORM_%';

SELECT COUNT(*) AS roles_with_invalid_template_origin
FROM vendor_roles
WHERE (template_key IS NULL) <> (template_version IS NULL)
   OR (template_version IS NOT NULL AND template_version < 1);
```

Expected results:

- Flyway history contains exactly one successful versioned migration, V1.
- Supported standard role rows have template_key populated and template_version=1.
- Only VENDOR_ADMIN, POS_CASHIER, SALES_MANAGER, INVENTORY_MANAGER and ACCOUNTANT receive demo role assignments.
- Deferred demo employee identities have no active employee_role_assignments.
- employee_permissions_table is 0.
- vendor_platform_permissions is 0.
- roles_with_invalid_template_origin is 0.

See [Local demo accounts, tenant roles and permissions](../demo/README.md) for fixture credentials and exact supported permission snapshots.

## 7. Rebuild after code changes

For ordinary code-only changes after the clean install, use:

```powershell
docker compose up --build -d
docker compose logs -f backend
```

Do not reset the database for every code change. Reset is required when intentionally changing the disposable development migration baseline or when moving from the old development authorization schema to this clean RBAC baseline.
