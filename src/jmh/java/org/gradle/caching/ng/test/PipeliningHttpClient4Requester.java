package org.gradle.caching.ng.test;

import org.apache.http.ContentTooLongException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.nio.client.CloseableHttpPipeliningClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.entity.ContentBufferEntity;
import org.apache.http.nio.protocol.AbstractAsyncResponseConsumer;
import org.apache.http.nio.protocol.BasicAsyncRequestProducer;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.nio.util.SimpleInputBuffer;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Asserts;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class PipeliningHttpClient4Requester extends AbstractHttpRequester {
    private final CloseableHttpPipeliningClient httpClient;
    private final HttpHost httpHost;

    public PipeliningHttpClient4Requester() {
        try {
            this.httpClient = HttpAsyncClients.createPipelining(
                new PoolingNHttpClientConnectionManager(
                    new DefaultConnectingIOReactor(
                        IOReactorConfig.DEFAULT,
                        new CounterThreadFactory())));
        } catch (IOReactorException e) {
            throw new RuntimeException(e);
        }
        this.httpClient.start();

        URI root = HttpBenchmark.ROOT_URI;
        this.httpHost = new HttpHost(root.getHost(), root.getPort(), root.getScheme());
    }

    @Override
    protected void doRequest(List<URI> urls, Blackhole blackhole, Recorder recorder) throws Exception {
        List<HttpAsyncRequestProducer> requestProducers = new ArrayList<>();
        List<HttpAsyncResponseConsumer<Long>> responseConsumers = new ArrayList<>();
        CountDownLatch counter = new CountDownLatch(urls.size());
        for (URI uri : urls) {
            HttpGet httpGet = new HttpGet(uri);
            httpGet.addHeader(HttpHeaders.ACCEPT, "*/*");
            requestProducers.add(new BasicAsyncRequestProducer(httpHost, httpGet) {
                @Override
                public HttpRequest generateRequest() {
                    logger.debug("Requesting {}", uri);
                    return super.generateRequest();
                }
            });
            responseConsumers.add(new CountingAsyncResponseConsumer(counter, recorder));
        }

        httpClient.execute(httpHost, requestProducers, responseConsumers, null);
        counter.await();
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }

    private static class CountingAsyncResponseConsumer extends AbstractAsyncResponseConsumer<Long> {

        private static final int MAX_INITIAL_BUFFER_SIZE = 256 * 1024;
        private final CountDownLatch counter;
        private final Recorder recorder;

        private volatile HttpResponse response;
        private volatile SimpleInputBuffer buf;

        public CountingAsyncResponseConsumer(CountDownLatch counter, Recorder recorder) {
            super();
            this.counter = counter;
            this.recorder = recorder;
        }

        @Override
        protected void onResponseReceived(final HttpResponse response) {
            this.response = response;
        }

        @Override
        protected void onEntityEnclosed(
            final HttpEntity entity, final ContentType contentType) throws IOException {
            long len = entity.getContentLength();
            if (len > Integer.MAX_VALUE) {
                throw new ContentTooLongException("Entity content is too long: %,d", len);
            }
            if (len < 0) {
                len = 4096;
            }
            final int initialBufferSize = Math.min((int) len, MAX_INITIAL_BUFFER_SIZE);
            this.buf = new SimpleInputBuffer(initialBufferSize, new HeapByteBufferAllocator());
            this.response.setEntity(new ContentBufferEntity(entity, this.buf));
        }

        @Override
        protected void onContentReceived(
            final ContentDecoder decoder, final IOControl ioControl) throws IOException {
            Asserts.notNull(this.buf, "Content buffer");
            this.buf.consumeContent(decoder);
        }

        @Override
        protected void releaseResources() {
            if (response != null) {
                recorder.recordReceived(response.getEntity()::getContent);
            }
            this.response = null;
            this.buf = null;
            counter.countDown();
        }

        @Override
        protected Long buildResult(final HttpContext context) {
            return this.response.getEntity().getContentLength();
        }
    }
}
