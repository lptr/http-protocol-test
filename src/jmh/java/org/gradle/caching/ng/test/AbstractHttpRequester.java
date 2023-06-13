package org.gradle.caching.ng.test;

import org.openjdk.jmh.infra.Blackhole;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractHttpRequester implements HttpRequester {
    @Override
    public final void request(List<URI> urls, Blackhole blackhole) throws Exception {
        AtomicLong totalCount = new AtomicLong(0);
        AtomicLong totalSize = new AtomicLong(0);
        doRequest(urls, blackhole, new Recorder() {
            @Override
            public void recordReceived(long bytes) {
                totalCount.incrementAndGet();
                totalSize.addAndGet(bytes);
            }
        });
        System.out.printf("Received %d files with %d bytes in total%n", totalCount.get(), totalSize.get());
    }

    protected abstract void doRequest(List<URI> urls, Blackhole blackhole, Recorder recorder) throws Exception;

    protected interface Recorder {
        void recordReceived(long bytes);
    }
}