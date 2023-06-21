package org.gradle.caching.ng.test;

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.async.MinimalHttpAsyncClient;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.openjdk.jmh.infra.Blackhole;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class PipeliningHttpClient5Requester extends AbstractHttpRequester {
    private final HttpHost target;
    private final MinimalHttpAsyncClient httpClient;

    public PipeliningHttpClient5Requester() {
        this.target = HttpHost.create(HttpBenchmark.ROOT_URI);
        this.httpClient = HttpAsyncClients.createMinimal(
            H2Config.DEFAULT,
            Http1Config.DEFAULT,
            IOReactorConfig.DEFAULT,
            PoolingAsyncClientConnectionManagerBuilder.create()
                .setDefaultTlsConfig(
                    TlsConfig.custom()
                        .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1)
                        .build())
                .setMaxConnPerRoute(1024)
                .build());
        this.httpClient.start();
    }

    @Override
    protected void doRequest(List<URI> urls, Blackhole blackhole, Recorder recorder) throws Exception {
        AsyncClientEndpoint endpoint = httpClient.lease(target, null)
            .get(30, java.util.concurrent.TimeUnit.SECONDS);
        try {
            var latch = new CountDownLatch(urls.size());
            for (var requestUri : urls) {
                logger.debug("Requesting {}", requestUri);
                var request = SimpleRequestBuilder.get().setHttpHost(target).setPath(requestUri.getPath()).build();

                endpoint.execute(SimpleRequestProducer.create(request), SimpleResponseConsumer.create(), new FutureCallback<>() {

                    @Override
                    public void completed(final SimpleHttpResponse response) {
                        try {
                            recorder.recordReceived(() -> new ByteArrayInputStream(response.getBody().getBodyBytes()));
                        } finally {
                            latch.countDown();
                        }
                    }

                    @Override
                    public void failed(final Exception ex) {
                        try {
                            logger.error("Failed to get {}", request, ex);
                        } finally {
                            latch.countDown();
                        }
                    }

                    @Override
                    public void cancelled() {
                        try {
                            logger.error("Request {} cancelled", request);
                        } finally {
                            latch.countDown();
                        }
                    }
                });
            }

            latch.await();
        } finally {
            endpoint.releaseAndReuse();
        }
    }

    @Override
    public void close() {
        httpClient.close();
    }
}
