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
                "com.saumya.pos.retail.BranchProvisioningServiceImpl"
            );
        Map<String, String> tenantScopedSqlSources = Map.of(
            "com.saumya.pos.membership.TenantEntitlementPolicy", "backend/src/main/java/com/saumya/pos/membership/TenantEntitlementPolicy.java",
            "com.saumya.pos.membership.StandardRoleTemplates", "backend/src/main/java/com/saumya/pos/membership/StandardRoleTemplates.java",
            "com.saumya.pos.retail.DraftSessionProjection", "backend/src/main/java/com/saumya/pos/retail/DraftSessionProjection.java",
            "com.saumya.pos.retail.CashDrawerProjection", "backend/src/main/java/com/saumya/pos/retail/CashDrawerProjection.java",
            "com.saumya.pos.retail.BranchProvisioningServiceImpl", "backend/src/main/java/com/saumya/pos/retail/BranchProvisioningServiceImpl.java"
        );
        for (var entry : tenantScopedSqlSources.entrySet()) {
            String source = Files.readString(resolveBackendSource(entry.getValue()));
            assertThat(source).as("approved JDBC adapter %s must bind tenant ownership in SQL", entry.getKey())
                .contains("tenant_id");
        }
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
