package io.pyahu.start;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithTest {

    @Test
    void capabilitiesOnlyUseEachOthersPublicApi() {
        ApplicationModules.of(Application.class).verify();
    }
}
