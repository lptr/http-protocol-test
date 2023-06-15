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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ThreadPoolSimpleHttpClientRequester extends AbstractHttpRequester {
    private final CloseableHttpClient httpClient;
    private final ExecutorService executor;

    public ThreadPoolSimpleHttpClientRequester() {
        this.httpClient = HttpClients.createDefault();
        this.executor = Executors.newFixedThreadPool(24);
    }

    @Override
    protected void doRequest(List<URI> urls, Blackhole blackhole, Recorder recorder) throws InterruptedException {
        CountDownLatch counter = new CountDownLatch(urls.size());
        urls.stream()
            .map(uri -> {
                HttpGet httpGet = new HttpGet(uri);
                httpGet.addHeader(HttpHeaders.ACCEPT, "*/*");
                return httpGet;
            })
            .forEach(httpGet ->
                CompletableFuture.runAsync(() -> {
                        System.out.printf("Requesting %s%n", httpGet.getRequestLine().getUri());
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
                    }, executor)
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
