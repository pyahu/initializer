package io.pyahu.fixture.hexagonal;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithTest {

    @Test
    void moduleStructureVerifies() {
        ApplicationModules.of(Application.class).verify();
    }
}
