package com.softwarelecture.ticket.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class LayerArchitectureTest {
    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.softwarelecture.ticket");

    @Test
    void domainDoesNotDependOnOuterLayers() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..", "..presentation..")
                .check(classes);
    }

    @Test
    void applicationDependsOnRepositoryContractsInsteadOfAdapters() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..", "..presentation..")
                .check(classes);
    }

    @Test
    void presentationCannotUsePersistenceImplementation() {
        noClasses().that().resideInAPackage("..presentation..")
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure.persistence..", "jakarta.persistence..")
                .check(classes);
    }
}
