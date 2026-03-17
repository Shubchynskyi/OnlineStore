package com.onlinestore.telegrambot.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.onlinestore.telegrambot.config.BotProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InteractionThrottlingServiceTests {

    private BotProperties botProperties;
    private InteractionThrottlingService interactionThrottlingService;

    @BeforeEach
    void setUp() {
        botProperties = new BotProperties();
        botProperties.setToken("test-token");
        botProperties.getProtection().getRateLimit().getUserUpdates().setMaxEvents(2);
        botProperties.getProtection().getRateLimit().getUserUpdates().setWindow(Duration.ofMinutes(1));
        botProperties.getProtection().setDuplicateManagerActionTtl(Duration.ofMillis(10));
        interactionThrottlingService = new InteractionThrottlingService(botProperties, null);
    }

    @Test
    void userUpdateThrottlingRejectsRequestsAboveConfiguredWindow() {
        assertThat(interactionThrottlingService.consumeUserUpdate(42L).allowed()).isTrue();
        assertThat(interactionThrottlingService.consumeUserUpdate(42L).allowed()).isTrue();

        InteractionThrottlingService.ThrottleDecision blockedDecision =
            interactionThrottlingService.consumeUserUpdate(42L);

        assertThat(blockedDecision.allowed()).isFalse();
        assertThat(blockedDecision.retryAfter()).isGreaterThan(Duration.ZERO);
    }

    @Test
    void replayGuardRejectsDuplicateManagerActionWithinConfiguredTtl() throws InterruptedException {
        assertThat(interactionThrottlingService.tryAcquireManagerAction(42L, "manager:order:accept:55")).isTrue();
        assertThat(interactionThrottlingService.tryAcquireManagerAction(42L, "manager:order:accept:55")).isFalse();

        Thread.sleep(20L);

        assertThat(interactionThrottlingService.tryAcquireManagerAction(42L, "manager:order:accept:55")).isTrue();
    }
}
