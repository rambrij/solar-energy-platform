package com.saumya.pos;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class ModuleArchitectureTest {
    @Test void moduleInternalsArePrivateAndModuleDependenciesAreAcyclic(){
        var classes=new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests()).importPackages("com.saumya.pos");
        for(String module:new String[]{"cart","catalog","customer","membership","identity","inventory","checkout","returns","reporting","retail","documents","tenant","platform","subscription"}){
            noClasses().that().resideOutsideOfPackage("com.saumya.pos."+module+"..")
                .should().dependOnClassesThat().resideInAnyPackage("com.saumya.pos."+module+".internal..")
                .because("other modules must use the public application service").check(classes);
        }
        noClasses().that().resideInAnyPackage("..cart..","..catalog..","..identity..","..inventory..","..checkout..","..returns..","..reporting..","..documents..","..tenant..","..subscription..","..platform..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..")
            .because("modules own Spring Data JPA persistence").check(classes);
        noClasses().that().resideInAnyPackage("..platform..")
            .should().dependOnClassesThat().resideInAnyPackage("..catalog.internal..","..membership.internal..","..identity.internal..","..tenant.internal..","..retail.internal..","..subscription.internal..")
            .because("platform orchestration must use public application-service interfaces").check(classes);
        slices().matching("com.saumya.pos.(*)..").should().beFreeOfCycles().check(classes);
    }
}
