package [[packageName]];

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

/** The dependency inverted DDD layered graph (architecture guide, step 4). */
@AnalyzeClasses(packagesOf = Application.class, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String TRANSACTIONAL = "org.springframework.transaction.annotation.Transactional";

    /** The interfaces and models the build generates from the contract; the rules below are for handwritten code. */
    private static final String GENERATED = "..api..";

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

    /** Controllers only at the edge, where the web lives (architecture guide, step 4). */
    @ArchTest
    static final ArchRule controllersOnlyInTheWebLayer = classes()
            .that()
            .areMetaAnnotatedWith("org.springframework.stereotype.Controller")
            .should()
            .resideInAPackage("..presentation..");

    /** A transaction starts in the application, around one use case (architecture guide, step 5). */
    @ArchTest
    static final ArchRule transactionalClassesOnlyInTheApplication = noClasses()
            .that()
            .resideOutsideOfPackage("..application..")
            .should()
            .beMetaAnnotatedWith(TRANSACTIONAL);

    @ArchTest
    static final ArchRule transactionalMethodsOnlyInTheApplication = noMethods()
            .that()
            .areDeclaredInClassesThat()
            .resideOutsideOfPackage("..application..")
            .should()
            .beMetaAnnotatedWith(TRANSACTIONAL);

    /** Dependencies come through the constructor, so a test can build the class without Spring. */
    @ArchTest
    static final ArchRule noFieldInjection = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    /** Output goes through the logger, so it reaches the logs with the trace ID. */
    @ArchTest
    static final ArchRule noStandardStreams =
            noClasses().that().resideOutsideOfPackage(GENERATED).should(GeneralCodingRules.ACCESS_STANDARD_STREAMS);

    @ArchTest
    static final ArchRule noJavaUtilLogging =
            noClasses().that().resideOutsideOfPackage(GENERATED).should(GeneralCodingRules.USE_JAVA_UTIL_LOGGING);

    /** An exception names what went wrong; Exception or RuntimeException says nothing. */
    @ArchTest
    static final ArchRule noGenericExceptions =
            noClasses().that().resideOutsideOfPackage(GENERATED).should(GeneralCodingRules.THROW_GENERIC_EXCEPTIONS);
}
