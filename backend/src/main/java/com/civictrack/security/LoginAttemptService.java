package com.civictrack.security;

import com.civictrack.exception.ApiException;
import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force protection (in-memory). After MAX_PER_ACCOUNT consecutive failures for the same email+IP,
 * or MAX_PER_IP failures from one IP, further attempts are rejected with HTTP 429 for LOCK_MS.
 */
@Service
public class LoginAttemptService {
    private static final int MAX_PER_ACCOUNT = 5;
    private static final int MAX_PER_IP = 30;
    private static final long LOCK_MS = 15 * 60 * 1000L;

    private static final class Bucket { int fails; long lastFail; long lockedUntil; }
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public void assertAllowed(String email, String ip) {
        check("a|" + email + "|" + ip);
        check("i|" + ip);
    }

    public void failed(String email, String ip) {
        register("a|" + email + "|" + ip, MAX_PER_ACCOUNT);
        register("i|" + ip, MAX_PER_IP);
        if (buckets.size() > 20000) purge();
    }

    public void succeeded(String email, String ip) { buckets.remove("a|" + email + "|" + ip); }

    private void check(String key) {
        Bucket b = buckets.get(key);
        if (b == null) return;
        synchronized (b) {
            long now = System.currentTimeMillis();
            if (b.lockedUntil > now) {
                long mins = Math.max(1, (b.lockedUntil - now + 59_999) / 60_000);
                throw ApiException.tooMany("Too many failed login attempts. Try again in " + mins + " minute(s).");
            }
        }
    }

    private void register(String key, int max) {
        Bucket b = buckets.computeIfAbsent(key, k -> new Bucket());
        synchronized (b) {
            long now = System.currentTimeMillis();
            if (now - b.lastFail > LOCK_MS) b.fails = 0;     // old failures expire
            b.fails++;
            b.lastFail = now;
            if (b.fails >= max) { b.lockedUntil = now + LOCK_MS; b.fails = 0; }
        }
    }

    private void purge() {
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(e -> now - e.getValue().lastFail > LOCK_MS && e.getValue().lockedUntil < now);
    }
}
