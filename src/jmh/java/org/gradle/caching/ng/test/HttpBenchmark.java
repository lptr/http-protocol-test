package org.gradle.caching.ng.test;


import com.google.common.collect.ImmutableList;
import com.google.common.io.Closer;
import com.google.common.io.Resources;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/*
 * Benchmark                     Mode  Cnt          Score         Error  Units
 * OptionalBenchmark.nullCheck  thrpt   20  107887111.061 ±  882182.482  ops/s
 * OptionalBenchmark.optional   thrpt   20   86746312.090 ± 1150860.296  ops/s
 **/
@Fork(1)
@Warmup(iterations = 1)
@Measurement(iterations = 2, batchSize = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
public class HttpBenchmark {

    final URI root = URI.create("https://eu-build-cache.gradle.org/cache/");
    ImmutableList<URI> cacheUrls;

    HttpRequester simple = new SimpleHttpClientRequester();
    HttpRequester threadPool = new ThreadPoolSimpleHttpClientRequester();
    HttpRequester async = new AsyncHttpClientRequester();
    HttpRequester pipelining4 = new PipeliningHttpClient4Requester(root);
    HttpRequester pipelining5 = new PipeliningHttpClient5Requester(root);

    final List<HttpRequester> requesters = ImmutableList.of(simple, threadPool, async, pipelining4, pipelining5);

    @Setup(Level.Trial)
    public void setupTrial() throws IOException {
        this.cacheUrls = Resources.asCharSource(Objects.requireNonNull(getClass().getResource("/cache-ids.txt")), StandardCharsets.UTF_8).readLines().stream()
            .map(id -> root.resolve("./" + id))
            .collect(ImmutableList.toImmutableList());
    }

    @TearDown
    @SuppressWarnings("UnstableApiUsage")
    public void tearDown() throws IOException {
        Closer closer = Closer.create();
        requesters.forEach(closer::register);
        closer.close();
    }

    @Benchmark
    public void simpleHttpClient(Blackhole blackhole) throws Exception {
        simple.request(cacheUrls, blackhole);
    }

    @Benchmark
    public void threadPoolHttpClient(Blackhole blackhole) throws Exception {
        threadPool.request(cacheUrls, blackhole);
    }

    @Benchmark
    public void asyncHttpClient4(Blackhole blackhole) throws Exception {
        async.request(cacheUrls, blackhole);
    }

    @Benchmark
    public void pipeliningHttpClient4(Blackhole blackhole) throws Exception {
        pipelining4.request(cacheUrls, blackhole);
    }

    @Benchmark
    public void pipeliningHttpClient5(Blackhole blackhole) throws Exception {
        pipelining5.request(cacheUrls, blackhole);
    }

    public static void main(String[] args) throws Exception {
        HttpBenchmark benchmark = new HttpBenchmark();
        benchmark.setupTrial();
        try {
            benchmark.threadPoolHttpClient(new Blackhole("Today's password is swordfish. I understand instantiating Blackholes directly is dangerous."));
        } finally {
            benchmark.tearDown();
        }
    }
}
