package org.gradle.caching.ng.test;

import org.apache.http.*;
import org.apache.http.client.methods.*;
import org.apache.http.impl.nio.client.*;
import org.apache.http.nio.*;
import org.apache.http.nio.client.methods.*;
import org.apache.http.nio.protocol.*;
import org.apache.http.protocol.*;
import org.openjdk.jmh.infra.*;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;

public class PipeliningHttpClientRequester implements HttpRequester, Closeable {
    private final CloseableHttpPipeliningClient httpClient;

    public PipeliningHttpClientRequester() {
        this.httpClient = HttpAsyncClients.createPipelining();
        this.httpClient.start();
    }

    @Override
    public void request(List<URI> urls, Blackhole blackhole) throws Exception {
        HttpHost httpHost = new HttpHost(urls.get(0).getHost(), 80);

        List<HttpAsyncRequestProducer> requestProducers = new ArrayList<>();
        List<HttpAsyncResponseConsumer<Object>> responseConsumers = new ArrayList<>();
        for (URI uri : urls) {
            HttpGet httpGet = new HttpGet(uri);
            httpGet.addHeader(HttpHeaders.ACCEPT, "*/*");
            requestProducers.add(new BasicAsyncRequestProducer(httpHost, httpGet) {
                @Override
                public HttpRequest generateRequest() {
                    System.out.printf("Requesting %s%n", uri);
                    return super.generateRequest();
                }
            });
            responseConsumers.add(new MyAsyncByteConsumer());
        }

        Future<List<Object>> execute = httpClient.execute(httpHost, requestProducers, responseConsumers, null);
        execute.get();
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }

    private static class MyAsyncByteConsumer extends AsyncByteConsumer<Object> {
        @Override
        protected void onResponseReceived(HttpResponse response) throws HttpException, IOException {
            System.out.printf("Response: %s%n", response.getStatusLine().toString());
        }

        @Override
        protected Object buildResult(HttpContext context) throws Exception {
            return null;
        }

        @Override
        protected void onByteReceived(ByteBuffer buf, IOControl ioControl) throws IOException {
            System.out.printf("Received %d%n", buf.limit());
        }
    }
}
