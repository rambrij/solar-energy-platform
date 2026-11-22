package com.saumya.pos;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.Set;
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
    void directJdbcUsageIsLimitedToReviewedTenantScopedAdapters() {
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
                "com.saumya.pos.retail.PosService"
            );
    }

    @Test
    void negativeFixturesProveControllerAndCrossModuleRepositoryRulesFail() {
        JavaClasses fixtures = new ClassFileImporter().importPackages("com.p204fixtures.architecture");
        assertThatThrownBy(() -> controllerRepositoryRule().check(fixtures))
            .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> repositoryOwnerRule("modulea").check(fixtures))
            .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> internalTypesArePrivateRule("modulea").check(fixtures))
            .isInstanceOf(AssertionError.class);
        assertThat(fixtures).isNotEmpty();
    }
}
