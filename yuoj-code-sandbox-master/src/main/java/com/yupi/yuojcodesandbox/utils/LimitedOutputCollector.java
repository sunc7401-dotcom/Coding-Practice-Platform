package com.yupi.yuojcodesandbox.utils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Thread-safe, combined stdout/stderr buffer with a hard byte limit.
 */
public final class LimitedOutputCollector {

    private final int maxBytes;

    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();

    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    private int collectedBytes;

    private boolean limitExceeded;

    public LimitedOutputCollector(int maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        this.maxBytes = maxBytes;
    }

    /**
     * Appends bytes without ever retaining more than maxBytes in total.
     *
     * @return true when the complete payload was accepted
     */
    public synchronized boolean append(byte[] payload, boolean errorStream) {
        if (payload == null || payload.length == 0) {
            return !limitExceeded;
        }
        if (limitExceeded) {
            return false;
        }
        int remaining = maxBytes - collectedBytes;
        int acceptedBytes = Math.min(remaining, payload.length);
        if (acceptedBytes > 0) {
            ByteArrayOutputStream target = errorStream ? stderr : stdout;
            target.write(payload, 0, acceptedBytes);
            collectedBytes += acceptedBytes;
        }
        if (acceptedBytes < payload.length) {
            limitExceeded = true;
            return false;
        }
        return true;
    }

    public synchronized boolean isLimitExceeded() {
        return limitExceeded;
    }

    public synchronized int getCollectedBytes() {
        return collectedBytes;
    }

    public synchronized String getStdout() {
        return new String(stdout.toByteArray(), StandardCharsets.UTF_8);
    }

    public synchronized String getStderr() {
        return new String(stderr.toByteArray(), StandardCharsets.UTF_8);
    }
}
