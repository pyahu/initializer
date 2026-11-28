package io.pyahu.start.generation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MonorepoRequestTest {

    private static MonorepoRequest of(String verticals) {
        return MonorepoRequest.of(Map.of("layout", "monorepo", "name", "shop", "verticals", verticals));
    }

    private static String errorsOf(String verticals) {
        try {
            of(verticals);
        } catch (InvalidProjectRequest e) {
            return e.errors().toString();
        }
        throw new AssertionError("accepted " + verticals);
    }

    @Test
    void eachVerticalHasItsStyleDatabaseAndPorts() {
        MonorepoRequest r = of("orders:hexagonal:postgres,billing:ddd-layered");

        assertThat(r.verticals())
                .containsExactly(
                        new Vertical("orders", Architecture.HEXAGONAL, Database.POSTGRES, 8110),
                        new Vertical("billing", Architecture.DDD_LAYERED, Database.NONE, 8120));
        assertThat(r.service(r.verticals().get(1)).packageName()).isEqualTo("com.example.billing");
    }

    @Test
    void aNameTheBuildUsesForContractsOrLibrariesIsReserved() {
        assertThat(errorsOf("orders,orders-api")).contains("orders-api: a reserved name");
        assertThat(errorsOf("lib-money")).contains("lib-money: a reserved name");
        assertThat(errorsOf("api")).contains("api: a reserved name");
    }

    @Test
    void twoNamesWithOnePackageAreRefused() {
        assertThat(errorsOf("order-history,orderhistory")).contains("orderhistory: listed twice, or the same package");
    }

    @Test
    void eachNameKeepsTheRuleOfAProjectName() {
        assertThat(errorsOf("a".repeat(51))).contains("at most 50 characters");
        assertThat(errorsOf("orders:onion")).contains("architecture must be one of");
    }

    @Test
    void theOptionsOfOneServiceAreRefused() {
        assertThatThrownBy(() ->
                        MonorepoRequest.of(Map.of("layout", "monorepo", "verticals", "orders", "database", "postgres")))
                .isInstanceOf(InvalidProjectRequest.class)
                .hasMessageContaining("database");
    }
}
