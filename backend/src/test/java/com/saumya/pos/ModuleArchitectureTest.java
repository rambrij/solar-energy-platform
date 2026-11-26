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
        "inventory", "marketplace", "membership", "platform", "reporting",
        "retail", "returns", "security", "shared", "subscription", "tenant"
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
                "com.saumya.pos.fulfillment.ShipmentService"
            );
        Map<String, String> jdbcSqlSources = Map.of(
            "com.saumya.pos.membership.MembershipService", "backend/src/main/java/com/saumya/pos/membership/MembershipService.java",
            "com.saumya.pos.membership.VendorRoleService", "backend/src/main/java/com/saumya/pos/membership/VendorRoleService.java",
            "com.saumya.pos.retail.PosService", "backend/src/main/java/com/saumya/pos/retail/PosService.java",
            "com.saumya.pos.membership.TenantEntitlementPolicy", "backend/src/main/java/com/saumya/pos/membership/TenantEntitlementPolicy.java",
            "com.saumya.pos.membership.StandardRoleTemplates", "backend/src/main/java/com/saumya/pos/membership/StandardRoleTemplates.java",
            "com.saumya.pos.retail.DraftSessionProjection", "backend/src/main/java/com/saumya/pos/retail/DraftSessionProjection.java",
            "com.saumya.pos.retail.CashDrawerProjection", "backend/src/main/java/com/saumya/pos/retail/CashDrawerProjection.java",
            "com.saumya.pos.retail.BranchProvisioningServiceImpl", "backend/src/main/java/com/saumya/pos/retail/BranchProvisioningServiceImpl.java",
            "com.saumya.pos.fulfillment.ShipmentService", "backend/src/main/java/com/saumya/pos/fulfillment/ShipmentService.java"
        );
        assertThat(jdbcConsumers).as("every direct JDBC consumer has a query-level review")
            .containsExactlyInAnyOrderElementsOf(jdbcSqlSources.keySet());
        for (var entry : jdbcSqlSources.entrySet()) {
            String source = Files.readString(resolveBackendSource(entry.getValue()));
            assertNoClientSuppliedTenantParameter(source, entry.getKey());
            List<String> statements = extractSqlStatements(source);
            assertThat(statements).as("SQL statements discovered in %s", entry.getKey()).isNotEmpty();
            for (String statement : statements) assertTenantScopedSql(statement, entry.getKey());
        }
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
