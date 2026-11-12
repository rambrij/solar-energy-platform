package com.saumya.pos;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
class ModuleArchitectureTest {
    @Test void moduleInternalsArePrivateAndModuleDependenciesAreAcyclic(){
        var classes=new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests()).importPackages("com.saumya.pos");
        for(String module:new String[]{"cart","catalog","identity","inventory"}){
            noClasses().that().resideOutsideOfPackage("com.saumya.pos."+module+"..")
                .should().dependOnClassesThat().resideInAnyPackage("com.saumya.pos."+module+".internal..")
                .because("other modules must use the public application service").check(classes);
        }
        noClasses().that().resideInAnyPackage("..cart..","..catalog..","..identity..","..inventory..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..")
            .because("these modules own Spring Data JPA persistence").check(classes);
        slices().matching("com.saumya.pos.(*)..").should().beFreeOfCycles().check(classes);
    }
}
