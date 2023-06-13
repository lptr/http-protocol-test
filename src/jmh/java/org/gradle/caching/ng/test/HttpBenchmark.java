package org.gradle.caching.ng.test;


import com.google.common.collect.*;
import com.google.common.io.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.*;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;

/*
 * Benchmark                     Mode  Cnt          Score         Error  Units
 * OptionalBenchmark.nullCheck  thrpt   20  107887111.061 ±  882182.482  ops/s
 * OptionalBenchmark.optional   thrpt   20   86746312.090 ± 1150860.296  ops/s
 **/
@Fork(1)
@Warmup(iterations = 0)
@Measurement(iterations = 1, batchSize = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
public class HttpBenchmark {

    final URI root = URI.create("https://eu-build-cache.gradle.org/cache/");
    ImmutableList<URI> cacheUrls;

    HttpRequester simple = new SimpleHttpClientRequester();
    HttpRequester async = new AsyncHttpClientRequester();
    HttpRequester pipelining = new PipeliningHttpClientRequester();

    final List<HttpRequester> requesters = ImmutableList.of(simple, async, pipelining);

    @Setup(Level.Trial)
    public void setupTrial() throws IOException {
        this.cacheUrls = Resources.asCharSource(Objects.requireNonNull(getClass().getResource("/cache-ids.txt")), StandardCharsets.UTF_8).readLines().stream()
            .map(id -> root.resolve("./" + id))
            .collect(ImmutableList.toImmutableList())
            .subList(0, 10);
    }

    @TearDown
    @SuppressWarnings("UnstableApiUsage")
    public void tearDown() throws IOException {
        Closer closer = Closer.create();
        requesters.forEach(closer::register);
        closer.close();
    }

//    @Benchmark
//    public void simpleHttpClient(Blackhole blackhole) throws Exception {
//        simple.request(cacheUrls, blackhole);
//    }
//
//    @Benchmark
//    public void asyncHttpClient(Blackhole blackhole) throws Exception {
//        async.request(cacheUrls, blackhole);
//    }
//
    @Benchmark
    public void pipeliningHttpClient(Blackhole blackhole) throws Exception {
        pipelining.request(cacheUrls, blackhole);
    }
}
