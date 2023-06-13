package org.gradle.caching.ng.test;

import org.apache.commons.io.*;
import org.apache.http.*;
import org.apache.http.client.methods.*;
import org.apache.http.concurrent.*;
import org.apache.http.impl.nio.client.*;
import org.openjdk.jmh.infra.*;
import org.openjdk.jmh.util.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class AsyncHttpClientRequester implements HttpRequester, Closeable {
    private final CloseableHttpAsyncClient httpClient;

    public AsyncHttpClientRequester() {
        this.httpClient = HttpAsyncClients.custom()
            .setMaxConnTotal(100)
            .setMaxConnPerRoute(100)
            .build();
        this.httpClient.start();
    }

    @Override
    public void request(List<URI> urls, Blackhole blackhole) throws Exception {
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
