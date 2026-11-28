package com.saumya.pos;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.Set;
import java.util.Map;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import com.p204fixtures.architecture.modulea.internal.ModuleARepository;
import com.p204fixtures.architecture.moduleb.ModuleBController;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/** Architecture constraints for all currently implemented business modules. */
class ModuleArchitectureTest {
    static final String[] MODULES = {
        "cart", "catalog", "checkout", "customer", "documents", "identity",
        "integration", "installation", "inventory", "marketplace", "membership", "platform", "reporting",
        "retail", "returns", "security", "service", "shared", "subscription", "tenant", "warranty"
    };

    static JavaClasses productionClasses() {
        return new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.saumya.pos");
    }

    static ArchRule controllerRepositoryRule() {
        return noClasses().that().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
            .because("HTTP controllers must delegate to application services");
    }

    static ArchRule repositoryOwnerRule(String owner) {
        return noClasses().that().resideOutsideOfPackage("com.saumya.pos." + owner + "..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.saumya.pos." + owner + ".internal..")
            .andShould().dependOnClassesThat()
            .haveSimpleNameEndingWith("Repository")
            .because("repositories are owned by their module; consumers use public services or events");
    }

    static ArchRule internalTypesArePrivateRule(String owner) {
        return noClasses().that().resideOutsideOfPackage("com.saumya.pos." + owner + "..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.saumya.pos." + owner + ".internal..")
            .because("module internals are not cross-module APIs");
    }

    @Test
    void implementedModulesAreAcyclicAndInternalPackagesArePrivate() {
        JavaClasses imported = productionClasses();
        for (String module : MODULES) internalTypesArePrivateRule(module).check(imported);
        slices().matching("com.saumya.pos.(*)..")
            .should().beFreeOfCycles()
            .because("the modular monolith must remain acyclic")
            .check(imported);
    }

    @Test
    void controllersCannotAccessRepositoriesAndRepositoriesRemainModuleOwned() {
        JavaClasses imported = productionClasses();
        controllerRepositoryRule().check(imported);
        for (String module : MODULES) repositoryOwnerRule(module).check(imported);
    }

    @Test
    void directJdbcUsageIsLimitedToReviewedTenantScopedAdapters() throws Exception {
        JavaClasses imported = productionClasses();
        Set<String> jdbcConsumers = imported.stream()
            .filter(type -> type.getDirectDependenciesFromSelf().stream()
                .anyMatch(dependency -> dependency.getTargetClass().getPackageName()
                    .startsWith("org.springframework.jdbc")))
            .map(type -> type.getName())
            .collect(java.util.stream.Collectors.toSet());
        assertThat(jdbcConsumers)
            .as("direct JDBC dependencies require an explicitly reviewed service exception")
            .containsOnly(
                "com.saumya.pos.membership.MembershipService",
                "com.saumya.pos.membership.VendorRoleService",
                "com.saumya.pos.retail.PosService",
                "com.saumya.pos.membership.TenantEntitlementPolicy",
                "com.saumya.pos.membership.StandardRoleTemplates",
                "com.saumya.pos.retail.DraftSessionProjection",
                "com.saumya.pos.retail.CashDrawerProjection",
                "com.saumya.pos.retail.BranchProvisioningServiceImpl",
                "com.saumya.pos.fulfillment.ShipmentService",
                "com.saumya.pos.installation.InstallationService",
                "com.saumya.pos.service.ServiceRequestService",
                "com.saumya.pos.service.ServiceVisitService",
                "com.saumya.pos.service.ServiceSlaService",
                "com.saumya.pos.service.ServiceSlaEscalationService",
                "com.saumya.pos.warranty.WarrantyService",
                "com.saumya.pos.warranty.WarrantyClaimService",
                "com.saumya.pos.marketplace.MarketplaceEnquiryService",
                "com.saumya.pos.catalog.LocalDemoSolarCatalogSeed",
                "com.saumya.pos.inventory.WarehouseInventoryService",
                "com.saumya.pos.returns.OnlineOrderService"
            );
        Map<String, String> jdbcSqlSources = Map.ofEntries(
            Map.entry("com.saumya.pos.membership.MembershipService", "backend/src/main/java/com/saumya/pos/membership/MembershipService.java"),
            Map.entry("com.saumya.pos.membership.VendorRoleService", "backend/src/main/java/com/saumya/pos/membership/VendorRoleService.java"),
            Map.entry("com.saumya.pos.retail.PosService", "backend/src/main/java/com/saumya/pos/retail/PosService.java"),
            Map.entry("com.saumya.pos.membership.TenantEntitlementPolicy", "backend/src/main/java/com/saumya/pos/membership/TenantEntitlementPolicy.java"),
            Map.entry("com.saumya.pos.membership.StandardRoleTemplates", "backend/src/main/java/com/saumya/pos/membership/StandardRoleTemplates.java"),
            Map.entry("com.saumya.pos.retail.DraftSessionProjection", "backend/src/main/java/com/saumya/pos/retail/DraftSessionProjection.java"),
            Map.entry("com.saumya.pos.retail.CashDrawerProjection", "backend/src/main/java/com/saumya/pos/retail/CashDrawerProjection.java"),
            Map.entry("com.saumya.pos.retail.BranchProvisioningServiceImpl", "backend/src/main/java/com/saumya/pos/retail/BranchProvisioningServiceImpl.java"),
            Map.entry("com.saumya.pos.fulfillment.ShipmentService", "backend/src/main/java/com/saumya/pos/fulfillment/ShipmentService.java"),
            Map.entry("com.saumya.pos.installation.InstallationService", "backend/src/main/java/com/saumya/pos/installation/InstallationService.java"),
            Map.entry("com.saumya.pos.service.ServiceRequestService", "backend/src/main/java/com/saumya/pos/service/ServiceRequestService.java"),
            Map.entry("com.saumya.pos.service.ServiceVisitService", "backend/src/main/java/com/saumya/pos/service/ServiceVisitService.java"),
            Map.entry("com.saumya.pos.service.ServiceSlaService", "backend/src/main/java/com/saumya/pos/service/ServiceSlaService.java"),
            Map.entry("com.saumya.pos.service.ServiceSlaEscalationService", "backend/src/main/java/com/saumya/pos/service/ServiceSlaEscalationService.java"),
            Map.entry("com.saumya.pos.warranty.WarrantyService", "backend/src/main/java/com/saumya/pos/warranty/WarrantyService.java"),
            Map.entry("com.saumya.pos.warranty.WarrantyClaimService", "backend/src/main/java/com/saumya/pos/warranty/WarrantyClaimService.java"),
            Map.entry("com.saumya.pos.marketplace.MarketplaceEnquiryService", "backend/src/main/java/com/saumya/pos/marketplace/MarketplaceEnquiryService.java"),
            Map.entry("com.saumya.pos.catalog.LocalDemoSolarCatalogSeed", "backend/src/main/resources/db/local-seed/local_demo_solar_catalog.sql"),
            Map.entry("com.saumya.pos.inventory.WarehouseInventoryService", "backend/src/main/java/com/saumya/pos/inventory/WarehouseInventoryService.java"),
            Map.entry("com.saumya.pos.returns.OnlineOrderService", "backend/src/main/java/com/saumya/pos/returns/OnlineOrderService.java")
        );
        assertThat(jdbcConsumers).as("every direct JDBC consumer has a query-level review")
            .containsExactlyInAnyOrderElementsOf(jdbcSqlSources.keySet());
        for (var entry : jdbcSqlSources.entrySet()) {
            if (entry.getKey().equals("com.saumya.pos.catalog.LocalDemoSolarCatalogSeed")) {
                String bootstrap = Files.readString(resolveBackendSource(
                    "backend/src/main/java/com/saumya/pos/catalog/LocalDemoSolarCatalogSeed.java"));
                assertThat(bootstrap)
                    .as("the demo seed must remain an explicit local-only startup adapter")
                    .contains("@Profile(\"local\")", "db/local-seed/local_demo_solar_catalog.sql",
                        "ResourceDatabasePopulator");
                assertNoClientSuppliedTenantParameter(bootstrap, entry.getKey());
                assertLocalDemoCatalogSeedTenantScope(
                    Files.readString(resolveBackendSource(entry.getValue())));
                continue;
            }
            String source = Files.readString(resolveBackendSource(entry.getValue()));
            assertNoClientSuppliedTenantParameter(source, entry.getKey());
            List<String> statements = extractSqlStatements(source);
            assertThat(statements).as("SQL statements discovered in %s", entry.getKey()).isNotEmpty();
            for (String statement : statements) assertTenantScopedSql(statement, entry.getKey());
        }
    }

    /**
     * The local demo catalogue is populated from a checked-in SQL resource rather than
     * inline JdbcTemplate statements. Review the launcher and the resource together:
     * the launcher is local-profile-only, and all tenant-owned writes/joins in the
     * resource carry tenant_id. The global catalogue sequence is intentionally shared.
     */
    private static void assertLocalDemoCatalogSeedTenantScope(String sql) {
        assertThat(sql).as("local demo seed uses an atomic, checked tenant fixture")
            .contains("START TRANSACTION", "COMMIT",
                "valid BOOLEAN NOT NULL CHECK (valid = TRUE)",
                "910001 AS tenant_id UNION ALL SELECT 920001 UNION ALL SELECT 930001",
                "t.lifecycle_status<>'ACTIVE'");
        assertThat(sql).as("catalogue identity checks reject cross-tenant ID collisions")
            .contains("c.id=d.id OR (c.tenant_id=d.tenant_id AND c.slug=d.slug)",
                "p.id=d.id OR (p.tenant_id=d.tenant_id AND (p.sku=d.sku OR p.barcode=d.barcode))",
                "WHERE c.tenant_id<>d.tenant_id",
                "WHERE p.tenant_id<>d.tenant_id");
        assertThat(sql).as("tenant-owned catalogue updates join on both tenant and resource identity")
            .contains("c.id=d.id AND c.tenant_id=d.tenant_id",
                "p.id=d.id AND p.tenant_id=d.tenant_id",
                "pc.tenant_id=d.tenant_id AND pc.product_id=d.id",
                "d.tenant_id=i.tenant_id AND d.id=i.product_id",
                "s.tenant_id=i.tenant_id AND s.branch_id=i.branch_id AND s.product_id=i.product_id");
        assertThat(sql).as("all persisted tenant-owned seed targets include tenant_id")
            .contains("INSERT INTO catalog_categories(id,tenant_id,name,slug)",
                "INSERT INTO products(id,tenant_id,sku,barcode,name,brand,price,tax_rate,published)",
                "INSERT INTO product_categories(tenant_id,product_id,category_id)",
                "INSERT INTO inventory(tenant_id,branch_id,product_id,quantity,reserved)",
                "INSERT INTO inventory_receipts(",
                "id,tenant_id,branch_id,product_id,quantity,actor,reason,",
                "INSERT INTO catalog_stock(tenant_id,branch_id,product_id,available)");
        assertThat(sql).as("stock receipts and stock projections are constrained by tenant, branch and product")
            .contains("r.tenant_id=i.tenant_id AND r.branch_id=i.branch_id",
                "r.tenant_id=i.tenant_id AND r.branch_id=i.branch_id",
                "s.tenant_id=i.tenant_id AND s.branch_id=i.branch_id AND s.product_id=i.product_id");
    }

    private static void assertNoClientSuppliedTenantParameter(String sourceText, String source) {
        java.util.regex.Pattern forgedTenant = java.util.regex.Pattern.compile(
            "(?i)\\b(?:request|filter|criteria|dto|body|payload|query|params)\\s*\\.\\s*(?:get)?tenantId\\s*\\(");
        assertThat(forgedTenant.matcher(sourceText).find())
            .as("JDBC tenant authority must not be read from client-controlled request/filter DTOs in %s", source)
            .isFalse();
    }

    @Test
    void forgedTenantParameterNegativeFixturesRejectClientControlledTenantSources() {
        assertThatThrownBy(() -> assertNoClientSuppliedTenantParameter(
            "db.queryForList(sql, request.tenantId())", "negative fixture: request tenant"))
            .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> assertNoClientSuppliedTenantParameter(
            "db.update(sql, filter.getTenantId())", "negative fixture: filter tenant"))
            .isInstanceOf(AssertionError.class);
    }

    private static List<String> extractSqlStatements(String source) {
        java.util.regex.Pattern literals = java.util.regex.Pattern.compile(
            "(?s)\\\"\\\"\\\"(.*?)\\\"\\\"\\\"|\\\"((?:\\\\\\\\.|[^\\\"\\\\\\\\])*)\\\"");
        java.util.regex.Matcher matcher = literals.matcher(source);
        List<String> statements = new java.util.ArrayList<>();
        while (matcher.find()) {
            String candidate = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (candidate == null) continue;
            String sql = candidate.strip();
            if (sql.matches("(?is)^(SELECT|INSERT|UPDATE|DELETE)\\b[\\s\\S]*")) statements.add(sql);
        }
        return statements;
    }

    private static void assertTenantScopedSql(String sql, String source) {
        String normalized = sql.strip();
        if (normalized.matches("(?is)^SELECT\\b[\\s\\S]*\\bFROM\\s+tenants\\b[\\s\\S]*")) {
            assertThat(normalized).as("global tenant-directory lock is explicitly keyed by tenant primary key in %s", source)
                .containsPattern("(?i)\\bWHERE\\s+id\\s*=\\s*\\?");
            return;
        }
        if (normalized.matches("(?is)^INSERT\\b[\\s\\S]*")) {
            java.util.regex.Matcher columns = java.util.regex.Pattern
                .compile("(?is)^INSERT\\s+INTO\\s+[a-z_][a-z0-9_]*\\s*\\(([^)]*)\\)").matcher(normalized);
            assertThat(columns.find()).as("INSERT column list is statically visible in %s: %s", source, normalized).isTrue();
            assertThat(columns.group(1)).as("tenant-owned INSERT must persist tenant_id in %s", source)
                .containsPattern("(?i)(?:^|,\\s*)tenant_id(?:\\s*,|$)");
            return;
        }
        assertThat(normalized).as("tenant-owned SELECT/UPDATE/DELETE must constrain tenant_id with a bound parameter in %s: %s", source, normalized)
            .containsPattern("(?is)\\bWHERE\\b[\\s\\S]*\\b(?:[a-z_][a-z0-9_]*\\.)?tenant_id\\s*=\\s*\\?");
    }

    @Test
    void unsafeSqlNegativeFixturesRejectUnscopedReadsWritesAndDeletes() {
        assertThatThrownBy(() -> assertTenantScopedSql(
            "SELECT * FROM vendor_roles WHERE id=?", "negative fixture: unscoped read"))
            .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> assertTenantScopedSql(
            "UPDATE vendor_roles SET active=FALSE WHERE id=?", "negative fixture: unscoped update"))
            .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> assertTenantScopedSql(
            "DELETE FROM vendor_roles WHERE id=?", "negative fixture: unscoped delete"))
            .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> assertTenantScopedSql(
            "INSERT INTO vendor_roles(role_key,display_name) VALUES(?,?)", "negative fixture: tenantless insert"))
            .isInstanceOf(AssertionError.class);
        assertTenantScopedSql("SELECT id FROM vendor_roles WHERE tenant_id=? AND id=?",
            "positive fixture: tenant-bound read");
    }

    private static Path resolveBackendSource(String repositoryRelativePath) {
        Path fromRepositoryRoot = Path.of(repositoryRelativePath);
        if (Files.isRegularFile(fromRepositoryRoot)) return fromRepositoryRoot;
        Path fromBackendRoot = Path.of(repositoryRelativePath.replaceFirst("^backend/", ""));
        if (Files.isRegularFile(fromBackendRoot)) return fromBackendRoot;
        throw new AssertionError("Approved SQL adapter source is missing: " + repositoryRelativePath);
    }

    @Test
    void negativeFixturesProveControllerAndCrossModuleRepositoryRulesFail() {
        JavaClasses fixtures = new ClassFileImporter().importClasses(ModuleARepository.class, ModuleBController.class);
        assertThatThrownBy(() -> controllerRepositoryRule().check(fixtures))
            .isInstanceOf(AssertionError.class);
        ArchRule fixtureRepositoryOwner = noClasses()
            .that().resideOutsideOfPackage("com.p204fixtures.architecture.modulea..")
            .should().dependOnClassesThat().resideInAPackage("com.p204fixtures.architecture.modulea.internal..")
            .because("fixture consumers cannot access another module's repositories");
        ArchRule fixtureInternalBoundary = noClasses()
            .that().resideOutsideOfPackage("com.p204fixtures.architecture.modulea..")
            .should().dependOnClassesThat().resideInAPackage("com.p204fixtures.architecture.modulea.internal..")
            .because("fixture consumers cannot import another module's internal types");
        assertThatThrownBy(() -> fixtureRepositoryOwner.check(fixtures)).isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> fixtureInternalBoundary.check(fixtures)).isInstanceOf(AssertionError.class);
        assertThat(fixtures).isNotEmpty();
    }
}
