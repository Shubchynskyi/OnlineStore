package com.onlinestore.telegrambot.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.telegrambot.config.BotProperties;
import java.time.Duration;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisUserSessionStoreTests {

    @Mock
    private RedisTemplate<String, UserSession> redisTemplate;

    @Mock
    private ValueOperations<String, UserSession> valueOperations;

    private RedisUserSessionStore redisUserSessionStore;

    @BeforeEach
    void setUp() {
        BotProperties botProperties = new BotProperties();
        botProperties.setToken("test-token");
        botProperties.setSessionTtl(Duration.ofMinutes(30));

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        redisUserSessionStore = new RedisUserSessionStore(redisTemplate, botProperties);
    }

    @Test
    void saveUsesPrefixedKeyAndConfiguredTtl() {
        UserSession userSession = UserSession.builder()
            .userId(42L)
            .chatId(84L)
            .state(UserState.MAIN_MENU)
            .attributes(new LinkedHashMap<>())
            .updatedAtEpochMillis(System.currentTimeMillis())
            .build();

        redisUserSessionStore.save(userSession);

        verify(valueOperations).set("telegram-bot:dialog-state:42", userSession, Duration.ofMinutes(30));
    }

    @Test
    void findByUserIdReadsFromRedis() {
        UserSession userSession = UserSession.initial(42L, 84L);
        when(valueOperations.get("telegram-bot:dialog-state:42")).thenReturn(userSession);

        assertThat(redisUserSessionStore.findByUserId(42L)).contains(userSession);
    }
}
