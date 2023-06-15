package org.gradle.caching.ng.test;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.openjdk.jmh.infra.Blackhole;

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
            System.out.printf("Requesting %s%n", uri);
            HttpGet httpGet = new HttpGet(uri);
            httpGet.addHeader(HttpHeaders.ACCEPT, "*/*");
            httpClient.execute(httpGet, new FutureCallback<>() {
                @Override
                public void completed(HttpResponse response) {
                    try {
                        recorder.recordReceived(response.getEntity()::getContent);
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
