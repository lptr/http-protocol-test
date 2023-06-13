package org.gradle.caching.ng.test;


import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static java.util.concurrent.TimeUnit.SECONDS;

/*
 * Benchmark                     Mode  Cnt          Score         Error  Units
 * OptionalBenchmark.nullCheck  thrpt   20  107887111.061 ±  882182.482  ops/s
 * OptionalBenchmark.optional   thrpt   20   86746312.090 ± 1150860.296  ops/s
 **/
@Fork(1)
@Warmup(iterations = 2, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 2, time = 1, timeUnit = SECONDS)
@State(Scope.Benchmark)
public class HttpBenchmark {

    final Path path = Paths.get(".");
    final Path pathRoot = Paths.get(".").toAbsolutePath().getRoot();

    @Benchmark
    public void nullCheck(Blackhole bh) {
        bh.consume(nullCheck(path));
        bh.consume(nullCheck(pathRoot));
    }

    private static Object nullCheck(Path path) {
        Path fileName = path.getFileName();
        if (fileName != null) {
            return fileName.toString();
        } else {
            return "";
        }
    }

    @Benchmark
    public void optional(Blackhole bh) {
        bh.consume(optional(path));
        bh.consume(optional(pathRoot));
    }

    private static Object optional(Path path) {
        return Optional.ofNullable(path.getFileName())
            .map(Object::toString)
            .orElse("");
    }
}
