package com.onlinestore.telegrambot.support;

import com.onlinestore.telegrambot.config.BotProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class InteractionThrottlingService {

    private static final String RATE_LIMIT_PREFIX = "telegram-bot:rate-limit:";
    private static final String REPLAY_GUARD_PREFIX = "telegram-bot:replay-guard:";
    private static final Duration REDIS_KEY_GRACE_TTL = Duration.ofSeconds(5);

    private final BotProperties botProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final ConcurrentHashMap<String, CounterWindow> fallbackCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> fallbackReplayGuards = new ConcurrentHashMap<>();

    @Autowired
    public InteractionThrottlingService(
        BotProperties botProperties,
        @Autowired(required = false) StringRedisTemplate stringRedisTemplate
    ) {
        this.botProperties = botProperties;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public ThrottleDecision consumeUserUpdate(Long userId) {
        return consume("user-updates", userId, botProperties.getProtection().getRateLimit().getUserUpdates());
    }

    public ThrottleDecision consumeAssistantRequest(Long userId) {
        return consume("assistant-requests", userId, botProperties.getProtection().getRateLimit().getAiRequests());
    }

    public ThrottleDecision consumeManagerAction(Long userId) {
        return consume("manager-actions", userId, botProperties.getProtection().getRateLimit().getManagerActions());
    }

    public boolean tryAcquireCartMutation(Long userId, String actionFingerprint) {
        return tryAcquireReplayGuard(
            "cart-mutation",
            userId,
            actionFingerprint,
            botProperties.getProtection().getDuplicateCartMutationTtl()
        );
    }

    public boolean tryAcquireManagerAction(Long userId, String actionFingerprint) {
        return tryAcquireReplayGuard(
            "manager-action",
            userId,
            actionFingerprint,
            botProperties.getProtection().getDuplicateManagerActionTtl()
        );
    }

    private ThrottleDecision consume(String scope, Long userId, BotProperties.RateLimitWindow policy) {
        if (userId == null) {
            return ThrottleDecision.allow();
        }
        if (stringRedisTemplate != null) {
            try {
                return consumeWithRedis(scope, userId, policy);
            } catch (RuntimeException ex) {
                log.warn("Redis-backed interaction throttling failed. scope={}, userId={}", scope, userId, ex);
            }
        }
        return consumeWithFallback(scope, userId, policy);
    }

    private ThrottleDecision consumeWithRedis(String scope, Long userId, BotProperties.RateLimitWindow policy) {
        long nowMillis = System.currentTimeMillis();
        long windowMillis = Math.max(1L, policy.getWindow().toMillis());
        long bucketStartMillis = (nowMillis / windowMillis) * windowMillis;
        String key = RATE_LIMIT_PREFIX + scope + ":" + userId + ":" + bucketStartMillis;
        Long counter = stringRedisTemplate.opsForValue().increment(key);
        if (counter == null) {
            return consumeWithFallback(scope, userId, policy);
        }
        if (counter == 1L) {
            stringRedisTemplate.expire(key, policy.getWindow().plus(REDIS_KEY_GRACE_TTL));
        }

        return counter <= policy.getMaxEvents()
            ? ThrottleDecision.allow()
            : new ThrottleDecision(false, retryAfter(policy.getWindow(), nowMillis, bucketStartMillis));
    }

    private ThrottleDecision consumeWithFallback(String scope, Long userId, BotProperties.RateLimitWindow policy) {
        cleanupExpiredFallbackCounters();

        long nowMillis = System.currentTimeMillis();
        long windowMillis = Math.max(1L, policy.getWindow().toMillis());
        long bucketStartMillis = (nowMillis / windowMillis) * windowMillis;
        String key = RATE_LIMIT_PREFIX + scope + ":" + userId + ":" + bucketStartMillis;
        Instant expiresAt = Instant.ofEpochMilli(bucketStartMillis + windowMillis);
        CounterWindow counterWindow = fallbackCounters.compute(
            key,
            (ignored, existing) -> existing == null || !existing.expiresAt().isAfter(Instant.now())
                ? new CounterWindow(1, expiresAt)
                : existing.incremented()
        );

        return counterWindow.count() <= policy.getMaxEvents()
            ? ThrottleDecision.allow()
            : new ThrottleDecision(false, retryAfter(policy.getWindow(), nowMillis, bucketStartMillis));
    }

    private boolean tryAcquireReplayGuard(String scope, Long userId, String actionFingerprint, Duration ttl) {
        if (userId == null || !StringUtils.hasText(actionFingerprint)) {
            return true;
        }
        if (stringRedisTemplate != null) {
            try {
                return tryAcquireReplayGuardWithRedis(scope, userId, actionFingerprint, ttl);
            } catch (RuntimeException ex) {
                log.warn("Redis-backed replay guard failed. scope={}, userId={}", scope, userId, ex);
            }
        }
        return tryAcquireReplayGuardWithFallback(scope, userId, actionFingerprint, ttl);
    }

    private boolean tryAcquireReplayGuardWithRedis(String scope, Long userId, String actionFingerprint, Duration ttl) {
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
            replayGuardKey(scope, userId, actionFingerprint),
            "1",
            ttl
        );
        return acquired == null || acquired;
    }

    private boolean tryAcquireReplayGuardWithFallback(String scope, Long userId, String actionFingerprint, Duration ttl) {
        cleanupExpiredFallbackReplayGuards();

        String key = replayGuardKey(scope, userId, actionFingerprint);
        Instant now = Instant.now();
        Instant expiresAt = fallbackReplayGuards.get(key);
        if (expiresAt != null && expiresAt.isAfter(now)) {
            return false;
        }
        fallbackReplayGuards.put(key, now.plus(ttl));
        return true;
    }

    private String replayGuardKey(String scope, Long userId, String actionFingerprint) {
        return REPLAY_GUARD_PREFIX + scope + ":" + userId + ":" + actionFingerprint;
    }

    private Duration retryAfter(Duration window, long nowMillis, long bucketStartMillis) {
        long retryAfterMillis = Math.max(0L, bucketStartMillis + Math.max(1L, window.toMillis()) - nowMillis);
        return Duration.ofMillis(retryAfterMillis);
    }

    private void cleanupExpiredFallbackCounters() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, CounterWindow>> iterator = fallbackCounters.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CounterWindow> entry = iterator.next();
            if (entry.getValue() == null || !entry.getValue().expiresAt().isAfter(now)) {
                iterator.remove();
            }
        }
    }

    private void cleanupExpiredFallbackReplayGuards() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, Instant>> iterator = fallbackReplayGuards.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Instant> entry = iterator.next();
            if (entry.getValue() == null || !entry.getValue().isAfter(now)) {
                iterator.remove();
            }
        }
    }

    public record ThrottleDecision(
        boolean allowed,
        Duration retryAfter
    ) {

        public static ThrottleDecision allow() {
            return new ThrottleDecision(true, Duration.ZERO);
        }
    }

    private record CounterWindow(
        int count,
        Instant expiresAt
    ) {

        private CounterWindow incremented() {
            return new CounterWindow(count + 1, expiresAt);
        }
    }
}
