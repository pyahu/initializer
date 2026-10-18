package io.pyahu.start.generation.application;

import io.pyahu.start.generation.domain.Architecture;
import io.pyahu.start.generation.domain.Baseline;
import io.pyahu.start.generation.domain.Baselines;
import io.pyahu.start.generation.domain.ProjectField;
import java.util.List;
import org.springframework.stereotype.Service;

/** What a form or a client may ask for: the fields with their rules, the choices and the baseline. */
@Service
public class DescribeOptions {

    /** The options, as the engine supports them today. */
    public record Options(Baseline baseline, List<ProjectField> fields, List<Architecture> architectures) {}

    private final Baselines baselines;

    DescribeOptions(Baselines baselines) {
        this.baselines = baselines;
    }

    public Options describe() {
        return new Options(baselines.active(), ProjectField.ALL, List.of(Architecture.values()));
    }
}
