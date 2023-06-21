package org.gradle.caching.ng.test;


import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Fork(1)
@Warmup(iterations = 1, batchSize = 1, time = 1)
@Measurement(iterations = 1, batchSize = 1, time = 1)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class HttpBenchmark {

    public static final URI ROOT_URI = URI.create("https://eu-build-cache.gradle.org/cache/");
    ImmutableList<URI> cacheUrls;

    @Param({
//        "org.gradle.caching.ng.test.SimpleHttpClientRequester",
        "org.gradle.caching.ng.test.AsyncHttpClient4Requester",
        "org.gradle.caching.ng.test.PipeliningHttpClient4Requester",
        "org.gradle.caching.ng.test.PipeliningHttpClient5Requester",
        "org.gradle.caching.ng.test.ThreadPoolSimpleHttpClientRequester$With16Threads",
        "org.gradle.caching.ng.test.ThreadPoolSimpleHttpClientRequester$With100Threads",
        "org.gradle.caching.ng.test.ThreadPoolSimpleHttpClientRequester$With100ThreadsLimited",
        "org.gradle.caching.ng.test.ThreadPoolSimpleHttpClientRequester$With100ThreadsUnlimited",
    })
    private String requesterType;

    private HttpRequester requester;

    @Setup(Level.Trial)
    public void setupTrial() throws IOException {
        this.cacheUrls = readCacheUrls();
    }

    private static ImmutableList<URI> readCacheUrls() throws IOException {
        return Resources.asCharSource(Objects.requireNonNull(HttpBenchmark.class.getResource("/cache-ids.txt")), StandardCharsets.UTF_8).readLines().stream()
            .map(id -> ROOT_URI.resolve("./" + id))
            .collect(ImmutableList.toImmutableList());
    }

    @Setup(Level.Invocation)
    public void setupInvocation() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        this.requester = (HttpRequester) Class.forName(requesterType).getConstructor().newInstance();
    }

    @TearDown
    public void tearDownInvocation() throws IOException {
        requester.close();
    }

    @Benchmark
    public void benchmark(Blackhole blackhole) throws Exception {
        requester.request(cacheUrls, blackhole);
    }

    public static void main(String[] args) throws Exception {
        List<URI> cacheUrls = HttpBenchmark.readCacheUrls();
        Blackhole blackhole = new Blackhole("Today's password is swordfish. I understand instantiating Blackholes directly is dangerous.");
        try (HttpRequester benchmark = new ThreadPoolSimpleHttpClientRequester.With100Threads()) {
            System.out.println("Benchmark: " + benchmark.getClass().getSimpleName());

            long start = System.currentTimeMillis();
            benchmark.request(cacheUrls, blackhole);
            long end = System.currentTimeMillis();

            System.out.printf("Benchmark '%s' finished in %f s%n", benchmark.getClass().getSimpleName(), (end - start) / 1000d);
        }
    }
}
