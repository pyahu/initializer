package io.pyahu.start.generation.application;

import io.pyahu.start.generation.domain.Baseline;
import io.pyahu.start.generation.domain.Baselines;
import org.springframework.stereotype.Service;

/** Tells which versions a generated project will use. */
@Service
public class DescribeBaseline {

    private final Baselines baselines;

    DescribeBaseline(Baselines baselines) {
        this.baselines = baselines;
    }

    public Baseline active() {
        return baselines.active();
    }
}
