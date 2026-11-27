package io.pyahu.fixture.billing;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.pyahu.fixture.api.billing.PingApi;
import io.pyahu.fixture.orders.StandIn;
import org.junit.jupiter.api.Test;

/** The boundary rule of ArchitectureTest fails on a class that reaches into another vertical (monorepo guide, step 3). */
class VerticalBoundaryTest {

    @SuppressWarnings("unused")
    static final class ReachesIntoOrders {
        private final StandIn orders = new StandIn();
    }

    @SuppressWarnings("unused")
    static final class UsesAContract {
        private PingApi contract;
    }

    @Test
    void aClassUsingAnotherVerticalsCodeBreaksTheRule() {
        var classes = new ClassFileImporter().importClasses(ReachesIntoOrders.class);

        assertThatThrownBy(() -> ArchitectureTest.otherVerticalsOnlyThroughTheirContract.check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("io.pyahu.fixture.orders.StandIn");
    }

    @Test
    void aClassUsingAContractKeepsTheRule() {
        var classes = new ClassFileImporter().importClasses(UsesAContract.class);

        assertThatCode(() -> ArchitectureTest.otherVerticalsOnlyThroughTheirContract.check(classes))
                .doesNotThrowAnyException();
    }
}
