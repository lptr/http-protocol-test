package org.gradle.caching.ng.test;

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.google.common.io.CountingOutputStream;
import org.apache.commons.io.IOUtils;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.util.NullOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings("UnstableApiUsage")
public abstract class AbstractHttpRequester implements HttpRequester {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @SuppressWarnings("deprecation")
    private final HashFunction hashFunction = Hashing.md5();

    @Override
    public final void request(List<URI> urls, Blackhole blackhole) throws Exception {
        AtomicLong totalCount = new AtomicLong(0);
        AtomicLong totalSize = new AtomicLong(0);
        Map<String, Long> hashes = Maps.newConcurrentMap();
        doRequest(urls, blackhole, opener -> {
            try (InputStream input = opener.get()) {
                CountingOutputStream counter = new CountingOutputStream(NullOutputStream.nullOutputStream());
                HashingOutputStream hasher = new HashingOutputStream(hashFunction, counter);
                IOUtils.copyLarge(input, hasher, ThreadLocalBuffer.getBuffer());
                long bytes = counter.getCount();
                String hash = hasher.hash().toString();
                hashes.put(hash, bytes);
                logger.debug("Received {} bytes with hash {} on thread {}",
                    bytes, hash, Thread.currentThread().getName());
                totalCount.incrementAndGet();
                totalSize.addAndGet(bytes);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        Hasher hasher = hashFunction.newHasher();
        ImmutableSortedMap.copyOf(hashes).forEach((hash, size) -> {
            hasher.putLong(size);
            hasher.putUnencodedChars(hash);
        });
        HashCode combinedHash = hasher.hash();

        logger.info("Received {} files with {} bytes in total, combined hash: {} ({})",
            totalCount.get(), totalSize.get(), combinedHash, getClass().getSimpleName());
    }

    protected abstract void doRequest(List<URI> urls, Blackhole blackhole, Recorder recorder) throws Exception;

    protected interface IoSupplier<T> {
        T get() throws IOException;
    }

    protected interface Recorder {
        void recordReceived(IoSupplier<InputStream> opener);
    }

    protected static class CounterThreadFactory implements ThreadFactory {
        private static final Logger LOGGER = LoggerFactory.getLogger(CounterThreadFactory.class);
        private final AtomicInteger count = new AtomicInteger(0);

        @Override
        public Thread newThread(@Nonnull Runnable r) {
            String name = "Dispatcher " + count.getAndIncrement();
            LOGGER.debug("Starting thread: {}", name);
            return new Thread(r, name);
        }
    }

    protected static class ThreadLocalBuffer {
        private static final int BUFFER_SIZE = 4096;

        private static final ThreadLocal<byte[]> buffers = ThreadLocal.withInitial(() -> new byte[BUFFER_SIZE]);

        public static byte[] getBuffer() {
            return buffers.get();
        }
    }
}
