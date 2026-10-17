package io.pyahu.fixture.hexagonal;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.jmolecules.archunit.JMoleculesArchitectureRules;
import org.jmolecules.archunit.JMoleculesArchitectureRules.VerificationDepth;

@AnalyzeClasses(packages = "io.pyahu.fixture.hexagonal", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule hexagonal = JMoleculesArchitectureRules.ensureHexagonal(VerificationDepth.STRICT);

    /** The domain sees only the JDK, JSpecify, and itself; the library rule above does not cover it. */
    @ArchTest
    static final ArchRule domainDependsOnNothingElse = classes()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("..domain..", "java..", "org.jspecify..");

    @ArchTest
    static final ArchRule nothingOutsideAdaptersSeesAdapters = noClasses()
            .that()
            .resideOutsideOfPackage("..adapter..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..adapter..");
}
