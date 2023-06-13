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
import java.util.concurrent.ExecutionException;

public class AsyncHttpClientRequester extends AbstractHttpRequester {
    private final CloseableHttpAsyncClient httpClient;

    public AsyncHttpClientRequester() {
        this.httpClient = HttpAsyncClients.custom()
            .setMaxConnTotal(100)
            .setMaxConnPerRoute(100)
            .build();
        this.httpClient.start();
    }

    @Override
    protected void doRequest(List<URI> urls, Blackhole blackhole, Recorder recorder) throws Exception {
        urls.stream()
            .map(uri -> {
                HttpGet httpGet = new HttpGet(uri);
                httpGet.addHeader(HttpHeaders.ACCEPT, "*/*");
                return httpClient.execute(httpGet, new FutureCallback<>() {
                    @Override
                    public void completed(HttpResponse response) {
                        System.out.println(uri.getPath());
                        try {
                            IOUtils.copyLarge(response.getEntity().getContent(), NullOutputStream.nullOutputStream(), ThreadLocalBuffer.getBuffer());
                            recorder.recordReceived(response.getEntity().getContentLength());
                        } catch (IOException ex) {
                            throw new RuntimeException(String.format("Couldn't fetch URL %s", uri), ex);
                        }
                    }

                    @Override
                    public void failed(Exception ex) {
                        throw new RuntimeException(String.format("Couldn't fetch URL %s", uri), ex);
                    }

                    @Override
                    public void cancelled() {
                        throw new RuntimeException(String.format("Cancelled fetching URL %s", uri));
                    }
                });
            })
            .forEach(httpResponseFuture -> {
                try {
                    httpResponseFuture.get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }
}
