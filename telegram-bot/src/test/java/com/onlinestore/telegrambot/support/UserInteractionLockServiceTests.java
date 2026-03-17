package com.onlinestore.telegrambot.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.telegrambot.config.BotProperties;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class UserInteractionLockServiceTests {

    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private UserInteractionLockService userInteractionLockService;
    private Duration interactionLockTtl;

    @BeforeEach
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> typedValueOperations = mock(ValueOperations.class);
        valueOperations = typedValueOperations;
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        doReturn(1L).when(stringRedisTemplate).execute(any(), any(), any());

        BotProperties botProperties = new BotProperties();
        botProperties.setToken("test-token");
        botProperties.setInteractionLockTtl(Duration.ofSeconds(5));
        interactionLockTtl = botProperties.getInteractionLockTtl();
        userInteractionLockService = new UserInteractionLockService(stringRedisTemplate, botProperties);
    }

    @Test
    void acquiresRedisLockBeforeRunningActionAndReleasesItAfterwards() {
        when(valueOperations.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(true);

        String result = userInteractionLockService.withUserLock(10L, () -> "ok");

        assertThat(result).isEqualTo("ok");
        verify(valueOperations).setIfAbsent(any(), any(), eq(interactionLockTtl));
        verify(stringRedisTemplate).execute(any(), any(), any());
    }

    @Test
    void retriesUntilRedisLockBecomesAvailable() {
        when(valueOperations.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(false, true);
        AtomicInteger invocations = new AtomicInteger();

        String result = userInteractionLockService.withUserLock(10L, () -> {
            invocations.incrementAndGet();
            return "acquired";
        });

        assertThat(result).isEqualTo("acquired");
        assertThat(invocations).hasValue(1);
        verify(valueOperations, times(2)).setIfAbsent(any(), any(), eq(interactionLockTtl));
        verify(stringRedisTemplate).execute(any(), any(), any());
    }
}
