package com.onlinestore.telegrambot.support;

import com.onlinestore.telegrambot.config.BotProperties;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserInteractionLockService {

    private static final String KEY_PREFIX = "telegram-bot:user-lock:";
    private static final Duration ACQUIRE_RETRY_DELAY = Duration.ofMillis(25);
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
        """
        if redis.call('get', KEYS[1]) == ARGV[1] then
            return redis.call('del', KEYS[1])
        end
        return 0
        """,
        Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;
    private final BotProperties botProperties;

    public <T> T withUserLock(Long userId, Supplier<T> action) {
        if (userId == null) {
            return action.get();
        }

        String lockKey = KEY_PREFIX + userId;
        String lockToken = UUID.randomUUID().toString();

        while (!tryAcquire(lockKey, lockToken)) {
            pauseBeforeRetry();
        }

        try {
            return action.get();
        } finally {
            release(lockKey, lockToken);
        }
    }

    private boolean tryAcquire(String lockKey, String lockToken) {
        Boolean acquired = stringRedisTemplate.opsForValue()
            .setIfAbsent(lockKey, lockToken, botProperties.getInteractionLockTtl());
        return Boolean.TRUE.equals(acquired);
    }

    private void release(String lockKey, String lockToken) {
        stringRedisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(lockKey), lockToken);
    }

    private void pauseBeforeRetry() {
        try {
            Thread.sleep(ACQUIRE_RETRY_DELAY.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("User interaction lock acquisition was interrupted.", ex);
        }
    }
}
