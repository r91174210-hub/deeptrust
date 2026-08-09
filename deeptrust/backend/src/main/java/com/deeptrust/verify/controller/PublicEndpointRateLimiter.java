package com.deeptrust.verify.controller;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal in-memory, fixed-window rate limiter keyed by client IP.
 * Sufficient for a single-instance viva demo. For a real multi-instance
 * deployment, replace with a Redis-backed token bucket (e.g. Bucket4j +
 * Redis) so limits are enforced consistently across all app instances.
 */
@Component
public class PublicEndpointRateLimiter {

    private static final int MAX_REQUESTS_PER_WINDOW = 20;
    private static final long WINDOW_SECONDS = 60;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    private record Window(AtomicInteger count, Instant windowStart) {}

    public boolean tryAcquire(String clientIp) {
        Instant now = Instant.now();
        Window window = windows.compute(clientIp, (ip, existing) -> {
            if (existing == null || now.getEpochSecond() - existing.windowStart().getEpochSecond() > WINDOW_SECONDS) {
                return new Window(new AtomicInteger(1), now);
            }
            existing.count().incrementAndGet();
            return existing;
        });
        return window.count().get() <= MAX_REQUESTS_PER_WINDOW;
    }
}
