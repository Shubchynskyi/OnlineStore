package com.onlinestore.telegrambot.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.config.RedisDialogStateConfiguration;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class RedisUserSessionStoreContractTests {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
        .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private RedisTemplate<String, UserSession> redisTemplate;
    private RedisUserSessionStore redisUserSessionStore;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(
            new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379))
        );
        connectionFactory.afterPropertiesSet();

        redisTemplate = new RedisDialogStateConfiguration().userSessionRedisTemplate(connectionFactory);
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });

        BotProperties botProperties = new BotProperties();
        botProperties.setToken("test-token");
        botProperties.setSessionTtl(Duration.ofSeconds(45));
        redisUserSessionStore = new RedisUserSessionStore(redisTemplate, botProperties);
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void saveRoundTripsSessionAndAppliesConfiguredTtl() {
        UserSession userSession = UserSession.builder()
            .userId(42L)
            .chatId(84L)
            .state(UserState.SEARCHING)
            .lastCommand("/search")
            .attributes(new LinkedHashMap<>(java.util.Map.of("searchQuery", "green tea")))
            .updatedAtEpochMillis(1_742_242_400_000L)
            .build();

        redisUserSessionStore.save(userSession);

        assertThat(redisUserSessionStore.findByUserId(42L)).contains(userSession);
        Long ttlSeconds = redisTemplate.getExpire("telegram-bot:dialog-state:42", TimeUnit.SECONDS);
        assertThat(ttlSeconds)
            .isNotNull()
            .isPositive()
            .isLessThanOrEqualTo(45L);
    }

    @Test
    void deleteByUserIdRemovesPersistedSession() {
        redisUserSessionStore.save(UserSession.initial(99L, 100L));

        redisUserSessionStore.deleteByUserId(99L);

        assertThat(redisUserSessionStore.findByUserId(99L)).isEmpty();
    }
}
