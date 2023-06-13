package org.gradle.caching.ng.test;

import org.apache.commons.io.*;
import org.apache.http.*;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.*;
import org.openjdk.jmh.infra.*;
import org.openjdk.jmh.util.*;

import java.io.*;
import java.net.*;
import java.util.*;

public class SimpleHttpClientRequester implements HttpRequester, Closeable {
    private final CloseableHttpClient httpClient;

    public SimpleHttpClientRequester() {
        this.httpClient = HttpClients.createDefault();
    }

    @Override
    public void request(List<URI> urls, Blackhole blackhole) throws Exception {
        int counter = 0;
        for (URI uri : urls) {
            HttpGet httpGet = new HttpGet(uri);
            httpGet.addHeader(HttpHeaders.ACCEPT, "*/*");

            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                StatusLine statusLine = response.getStatusLine();
                int statusCode = statusLine.getStatusCode();
                if (statusCode == 200) {
                    System.out.printf("%s (%d / %d)%n", uri.getPath(), ++counter, urls.size());
                    IOUtils.copyLarge(response.getEntity().getContent(), NullOutputStream.nullOutputStream(), ThreadLocalBuffer.getBuffer());
                } else {
                    throw new RuntimeException(String.format("Received status code %d for URL %s", statusCode, uri));
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }
}
