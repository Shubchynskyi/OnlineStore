package com.onlinestore.telegrambot.support;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PendingWriteGuardService {

    private static final String KEY_PREFIX = "telegram-bot:pending-write:";
    private static final Duration GUARD_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate stringRedisTemplate;
    private final ConcurrentHashMap<String, Instant> fallbackGuards = new ConcurrentHashMap<>();

    public PendingWriteGuardService() {
        this(null);
    }

    @Autowired
    public PendingWriteGuardService(@Autowired(required = false) StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public boolean isPending(String scope, Long userId) {
        String key = buildKey(scope, userId);
        if (key == null) {
            return false;
        }
        if (stringRedisTemplate != null) {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
        }

        cleanupExpiredFallbackGuards();
        Instant expiresAt = fallbackGuards.get(key);
        return expiresAt != null && expiresAt.isAfter(Instant.now());
    }

    public void markPending(String scope, Long userId) {
        String key = buildKey(scope, userId);
        if (key == null) {
            return;
        }
        if (stringRedisTemplate != null) {
            stringRedisTemplate.opsForValue().set(key, "pending", GUARD_TTL);
            return;
        }
        fallbackGuards.put(key, Instant.now().plus(GUARD_TTL));
    }

    public void clearPending(String scope, Long userId) {
        String key = buildKey(scope, userId);
        if (key == null) {
            return;
        }
        if (stringRedisTemplate != null) {
            stringRedisTemplate.delete(key);
            return;
        }
        fallbackGuards.remove(key);
    }

    public Duration guardTtl() {
        return GUARD_TTL;
    }

    private String buildKey(String scope, Long userId) {
        if (!StringUtils.hasText(scope) || userId == null) {
            return null;
        }
        return KEY_PREFIX + scope + ":" + userId;
    }

    private void cleanupExpiredFallbackGuards() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, Instant>> iterator = fallbackGuards.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Instant> entry = iterator.next();
            if (entry.getValue() == null || !entry.getValue().isAfter(now)) {
                iterator.remove();
            }
        }
    }
}
