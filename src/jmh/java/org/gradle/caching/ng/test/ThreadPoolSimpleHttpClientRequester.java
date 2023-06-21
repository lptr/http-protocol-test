package org.gradle.caching.ng.test;

import org.apache.http.HttpHeaders;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public abstract class ThreadPoolSimpleHttpClientRequester extends AbstractHttpRequester {
    private final int threadCount;
    private final CloseableHttpClient httpClient;
    private final ExecutorService executor;
    private final int maxConn;

    public static class With16Threads extends ThreadPoolSimpleHttpClientRequester {
        public With16Threads() {
            super(16);
        }
    }

    public static class With100Threads extends ThreadPoolSimpleHttpClientRequester {
        public With100Threads() {
            super(100);
        }
    }

    public static class With100ThreadsLimited extends ThreadPoolSimpleHttpClientRequester {
        public With100ThreadsLimited() {
            super(100, 4);
        }
    }

    public static class With100ThreadsUnlimited extends ThreadPoolSimpleHttpClientRequester {
        public With100ThreadsUnlimited() {
            super(100, 1024);
        }
    }

    protected ThreadPoolSimpleHttpClientRequester(int threadCount) {
        this(threadCount, threadCount);
    }

    protected ThreadPoolSimpleHttpClientRequester(int threadCount, int maxConn) {
        this.threadCount = threadCount;
        this.maxConn = maxConn;
        this.httpClient = HttpClients.custom()
            .setMaxConnTotal(maxConn)
            .setMaxConnPerRoute(maxConn)
            .build();
        this.executor = Executors.newFixedThreadPool(threadCount, new CounterThreadFactory());
    }

    @Override
    protected void doRequest(List<URI> urls, Blackhole blackhole, Recorder recorder) throws InterruptedException {
        logger.info("Running with thread pooling HTTP connector, threads: {}, max connections: {}",
            threadCount, maxConn);
        CountDownLatch counter = new CountDownLatch(urls.size());
        urls.stream()
            .map(uri -> {
                HttpGet httpGet = new HttpGet(uri);
                httpGet.addHeader(HttpHeaders.ACCEPT, "*/*");
                return httpGet;
            })
            .forEach(httpGet ->
                executor.execute(() -> {
                    logger.debug("Requesting {}", httpGet.getRequestLine().getUri());
                    try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                        StatusLine statusLine = response.getStatusLine();
                        String uri = httpGet.getRequestLine().getUri();
                        int statusCode = statusLine.getStatusCode();
                        if (statusCode == 200) {
                            recorder.recordReceived(response.getEntity()::getContent);
                        } else {
                            throw new RuntimeException(String.format("Received status code %d for URL %s", statusCode, uri));
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    } finally {
                        counter.countDown();
                    }
                })
            );
        counter.await();
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
        executor.shutdown();
        try {
            //noinspection ResultOfMethodCallIgnored
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
