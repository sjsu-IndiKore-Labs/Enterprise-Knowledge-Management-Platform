package backend.knowledge_api_service.service;

import backend.knowledge_api_service.exception.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatRateLimiter {

    @Value("${ekm.rate-limit.chat-per-minute:20}")
    private int maxRequestsPerMinute = 20;

    private final Map<UUID, Deque<Long>> userRequestTimestamps = new ConcurrentHashMap<>();

    public void checkRateLimit(UUID userId) {
        UUID key = userId != null ? userId : UUID.fromString("00000000-0000-0000-0000-000000000001");
        long nowMs = Instant.now().toEpochMilli();
        long windowStartMs = nowMs - 60_000L;

        Deque<Long> timestamps = userRequestTimestamps.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStartMs) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequestsPerMinute) {
                throw new RateLimitExceededException(
                        "Chat rate limit exceeded (" + maxRequestsPerMinute + " requests per minute). Please wait before sending another query.");
            }
            timestamps.addLast(nowMs);
        }
    }

    public void reset() {
        userRequestTimestamps.clear();
    }
}

