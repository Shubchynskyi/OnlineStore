package com.onlinestore.telegrambot.session;

import com.onlinestore.telegrambot.config.BotProperties;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RedisUserSessionStore implements UserSessionStore {

    private static final String KEY_PREFIX = "telegram-bot:dialog-state:";

    private final RedisTemplate<String, UserSession> userSessionRedisTemplate;
    private final BotProperties botProperties;

    @Override
    public Optional<UserSession> findByUserId(Long userId) {
        ValueOperations<String, UserSession> valueOperations = userSessionRedisTemplate.opsForValue();
        return Optional.ofNullable(valueOperations.get(buildKey(userId)));
    }

    @Override
    public UserSession save(UserSession userSession) {
        ValueOperations<String, UserSession> valueOperations = userSessionRedisTemplate.opsForValue();
        valueOperations.set(buildKey(userSession.getUserId()), userSession, botProperties.getSessionTtl());
        return userSession;
    }

    @Override
    public void deleteByUserId(Long userId) {
        userSessionRedisTemplate.delete(buildKey(userId));
    }

    private String buildKey(Long userId) {
        return KEY_PREFIX + userId;
    }
}
