package io.github.summerwenlabs.log.mask.benchmarks;

import org.openjdk.jmh.annotations.Benchmark;

/**
 * Smoke benchmark ensuring the profile compiles JMH workloads.
 */
public class BenchmarkProfileSmokeBenchmark {

    @Benchmark
    public int constant() {
        return 1;
    }
}
