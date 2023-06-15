package org.gradle.caching.ng.test;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.util.NullOutputStream;

import java.io.IOException;
import java.net.URI;
import java.util.List;

public class SimpleHttpClientRequester extends AbstractHttpRequester {
    private final CloseableHttpClient httpClient;

    public SimpleHttpClientRequester() {
        this.httpClient = HttpClients.createDefault();
    }

    @Override
    protected void doRequest(List<URI> urls, Blackhole blackhole, Recorder recorder) throws Exception {
        for (URI uri : urls) {
            System.out.printf("Requesting %s%n", uri);
            HttpGet httpGet = new HttpGet(uri);
            httpGet.addHeader(HttpHeaders.ACCEPT, "*/*");

            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                StatusLine statusLine = response.getStatusLine();
                int statusCode = statusLine.getStatusCode();
                if (statusCode == 200) {
                    IOUtils.copyLarge(response.getEntity().getContent(), NullOutputStream.nullOutputStream(), ThreadLocalBuffer.getBuffer());
                    recorder.recordReceived(response.getEntity().getContentLength());
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
