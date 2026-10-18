package io.pyahu.start.generation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JavaImportsTest {

    @Test
    void sortsStaticsFirstThenTheRest() {
        String source = """
                package org.zeta.demo;

                import static org.mockito.BDDMockito.given;
                import static org.assertj.core.api.Assertions.assertThat;

                import org.zeta.demo.ping.Ping;
                import org.junit.jupiter.api.Test;

                class PingTest {}
                """;
        assertThat(JavaImports.sort(source)).isEqualTo("""
                package org.zeta.demo;

                import static org.assertj.core.api.Assertions.assertThat;
                import static org.mockito.BDDMockito.given;

                import org.junit.jupiter.api.Test;
                import org.zeta.demo.ping.Ping;

                class PingTest {}
                """);
    }

    @Test
    void aNestedClassSortsAfterItsOuterClass() {
        String source = "package a;\n\nimport x.Rules.Depth;\nimport x.Rules;\n\nclass A {}\n";
        assertThat(JavaImports.sort(source))
                .isEqualTo("package a;\n\nimport x.Rules;\nimport x.Rules.Depth;\n\nclass A {}\n");
    }

    @Test
    void leavesFilesWithoutImportsAlone() {
        String source = "package a;\n\nclass A {}\n";
        assertThat(JavaImports.sort(source)).isEqualTo(source);
    }
}
