package io.pyahu.start;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** The dependency inverted DDD layered graph (architecture guide, step 4). */
@AnalyzeClasses(packagesOf = Application.class, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule layers = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .withOptionalLayers(true)
            .layer("Presentation")
            .definedBy("..presentation..")
            .layer("Application")
            .definedBy("..application..")
            .layer("Domain")
            .definedBy("..domain..")
            .layer("Infrastructure")
            .definedBy("..infrastructure..")
            .whereLayer("Presentation")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("Infrastructure")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("Application")
            .mayOnlyBeAccessedByLayers("Presentation", "Infrastructure")
            .whereLayer("Domain")
            .mayOnlyBeAccessedByLayers("Presentation", "Application", "Infrastructure");

    /** The domain sees only the JDK, JSpecify and itself. */
    @ArchTest
    static final ArchRule domainDependsOnNothingElse = classes()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("..domain..", "java..", "org.jspecify..");
}
