package org.gradle.caching.ng.test;

public class ThreadLocalBuffer {
    private static final int BUFFER_SIZE = 4096;

    private static final ThreadLocal<byte[]> buffers = new ThreadLocal<>() {
        @Override
        protected byte[] initialValue() {
            return new byte[BUFFER_SIZE];
        }
    };

    public static byte[] getBuffer() {
        return buffers.get();
    }
}
