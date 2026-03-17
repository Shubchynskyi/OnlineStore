package com.onlinestore.telegrambot.support;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SecurityAuditService {

    public void logWebhookRejected(String reason, Integer updateId) {
        log.warn("Telegram webhook request rejected. reason={}, updateId={}", reason, updateId);
    }

    public void logRateLimitExceeded(String scope, Long userId, Long chatId, Duration retryAfter) {
        log.warn(
            "Telegram interaction throttled. scope={}, userId={}, chatId={}, retryAfterMillis={}",
            scope,
            userId,
            chatId,
            retryAfter.toMillis()
        );
    }

    public void logReplayRejected(String scope, Long userId, Long chatId) {
        log.warn("Telegram interaction replay rejected. scope={}, userId={}, chatId={}", scope, userId, chatId);
    }

    public void logManagerActionDenied(String reason, Long userId, Long chatId, String callbackData) {
        log.warn(
            "Manager action denied. reason={}, userId={}, chatId={}, callbackData={}",
            reason,
            userId,
            chatId,
            callbackData
        );
    }

    public void logManagerActionCompleted(String action, Long userId, Long chatId, Long targetId) {
        log.info(
            "Manager action completed. action={}, userId={}, chatId={}, targetId={}",
            action,
            userId,
            chatId,
            targetId
        );
    }

    public void logManagerActionFailed(String action, Long userId, Long chatId, Long targetId, Exception ex) {
        log.warn(
            "Manager action failed. action={}, userId={}, chatId={}, targetId={}",
            action,
            userId,
            chatId,
            targetId,
            ex
        );
    }

    public void logFollowUpDeliveryFailure(String action, Long userId, Long chatId, Long targetId, Exception ex) {
        log.warn(
            "Telegram follow-up delivery failed after action completion. action={}, userId={}, chatId={}, targetId={}",
            action,
            userId,
            chatId,
            targetId,
            ex
        );
    }

    public void logAssistantStatePersistenceFailure(Long userId, Long chatId, Exception ex) {
        log.warn("Assistant reply state persistence failed. userId={}, chatId={}", userId, chatId, ex);
    }
}
