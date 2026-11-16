package com.saumya.pos;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
class ModuleArchitectureTest {
    @Test void moduleInternalsArePrivateAndModuleDependenciesAreAcyclic(){
        var classes=new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests()).importPackages("com.saumya.pos");
        for(String module:new String[]{"cart","catalog","customer","membership","identity","inventory","checkout","returns","reporting","retail","documents","tenant","platform"}){
            noClasses().that().resideOutsideOfPackage("com.saumya.pos."+module+"..")
                .should().dependOnClassesThat().resideInAnyPackage("com.saumya.pos."+module+".internal..")
                .because("other modules must use the public application service").check(classes);
        }
        noClasses().that().resideInAnyPackage("..cart..","..catalog..","..identity..","..inventory..","..checkout..","..returns..","..reporting..","..documents..","..tenant..","..platform..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..")
            .because("these modules own Spring Data JPA persistence").check(classes);
        var vendorModules=new String[]{"com.saumya.pos.catalog..","com.saumya.pos.cart..","com.saumya.pos.checkout..","com.saumya.pos.inventory..","com.saumya.pos.returns..","com.saumya.pos.reporting..","com.saumya.pos.documents..","com.saumya.pos.retail..","com.saumya.pos.marketplace..","com.saumya.pos.membership..","com.saumya.pos.customer.."};
        noClasses().that().resideInAnyPackage("com.saumya.pos.platform..","com.saumya.pos.tenant..")
            .should().dependOnClassesThat().resideInAnyPackage(vendorModules)
            .because("the platform operator owns no commercial vendor data: catalogue, inventory, orders, POS, returns and reporting are vendor-tenant concerns").check(classes);
        noClasses().that().resideInAnyPackage(vendorModules)
            .should().dependOnClassesThat().resideInAnyPackage("com.saumya.pos.platform..")
            .because("vendor modules never depend on platform administration").check(classes);
        slices().matching("com.saumya.pos.(*)..").should().beFreeOfCycles().check(classes);
    }
}
