package org.gradle.caching.ng.test;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.util.NullOutputStream;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class AsyncHttpClient4Requester extends AbstractHttpRequester {
    private final CloseableHttpAsyncClient httpClient;

    public AsyncHttpClient4Requester() {
        this.httpClient = HttpAsyncClients.custom()
            .setThreadFactory(new CounterThreadFactory())
            .setMaxConnPerRoute(1024)
            .build();
        this.httpClient.start();
    }

    @Override
    protected void doRequest(List<URI> urls, Blackhole blackhole, Recorder recorder) throws Exception {
        CountDownLatch counter = new CountDownLatch(urls.size());
        urls.forEach(uri -> {
            HttpGet httpGet = new HttpGet(uri);
            httpGet.addHeader(HttpHeaders.ACCEPT, "*/*");
            httpClient.execute(httpGet, new FutureCallback<>() {
                @Override
                public void completed(HttpResponse response) {
                    System.out.println(uri.getPath());
                    try {
                        IOUtils.copyLarge(response.getEntity().getContent(), NullOutputStream.nullOutputStream(), ThreadLocalBuffer.getBuffer());
                        System.out.printf("Received %s on thread %s%n", uri.getPath(), Thread.currentThread().getName());
                        recorder.recordReceived(response.getEntity().getContentLength());
                    } catch (IOException ex) {
                        throw new RuntimeException(String.format("Couldn't fetch URL %s", uri), ex);
                    } finally {
                        counter.countDown();
                    }
                }

                @Override
                public void failed(Exception ex) {
                    try {
                        throw new RuntimeException(String.format("Couldn't fetch URL %s", uri), ex);
                    } finally {
                        counter.countDown();
                    }
                }

                @Override
                public void cancelled() {
                    try {
                        throw new RuntimeException(String.format("Cancelled fetching URL %s", uri));
                    } finally {
                        counter.countDown();
                    }
                }
            });
        });
        counter.await();
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }
}
