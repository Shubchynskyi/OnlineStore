package com.onlinestore.telegrambot.notifications;

import com.onlinestore.telegrambot.config.BotProperties;
import com.onlinestore.telegrambot.integration.BackendApiException;
import com.onlinestore.telegrambot.integration.dto.orders.OrderDto;
import com.onlinestore.telegrambot.integration.service.ManagerOrdersIntegrationService;
import com.onlinestore.telegrambot.routing.BotUpdateContext;
import com.onlinestore.telegrambot.support.InteractionThrottlingService;
import com.onlinestore.telegrambot.support.SecurityAuditService;
import com.onlinestore.telegrambot.support.TelegramApiExecutor;
import com.onlinestore.telegrambot.support.TelegramInteractionException;
import com.onlinestore.telegrambot.support.TelegramMessageFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;

@Component
@RequiredArgsConstructor
public class ManagerActionHandler {

    private static final String PREFIX = "manager:";
    private static final String ORDER_PREFIX = PREFIX + "order:";
    private static final String STOCK_PREFIX = PREFIX + "stock:";
    private static final String MANAGER_RATE_LIMIT_MESSAGE =
        "Manager actions are throttled right now. Please wait a moment and try again.";
    private static final String MANAGER_DUPLICATE_ACTION_MESSAGE =
        "This manager action is already being processed. Please wait for the previous attempt to finish.";

    private final BotProperties botProperties;
    private final ManagerOrdersIntegrationService managerOrdersIntegrationService;
    private final TelegramMessageFactory telegramMessageFactory;
    private final TelegramApiExecutor telegramApiExecutor;
    private final InteractionThrottlingService interactionThrottlingService;
    private final SecurityAuditService securityAuditService;

    public static String acknowledgeOrderCallback(Long orderId) {
        return ORDER_PREFIX + "ack:" + orderId;
    }

    public static String acceptOrderCallback(Long orderId) {
        return ORDER_PREFIX + "accept:" + orderId;
    }

    public static String customerHandoffCallback(Long orderId) {
        return ORDER_PREFIX + "handoff:" + orderId;
    }

    public static String acknowledgeLowStockCallback(Long variantId) {
        return STOCK_PREFIX + "ack:" + variantId;
    }

    public BotApiMethod<?> handleCallback(BotUpdateContext updateContext) {
        BotProperties.ManagerNotifications config = botProperties.getManagerNotifications();
        String callbackData = updateContext.callbackData().orElse("");

        if (!isAllowedChat(config, updateContext.getChatId())) {
            securityAuditService.logManagerActionDenied(
                "unauthorized_chat",
                updateContext.getUserId(),
                updateContext.getChatId(),
                callbackData
            );
            return callbackNotice(updateContext, "Manager actions are not available in this chat.");
        }
        if (!isAllowedActor(config, updateContext.getUserId())) {
            securityAuditService.logManagerActionDenied(
                "unauthorized_actor",
                updateContext.getUserId(),
                updateContext.getChatId(),
                callbackData
            );
            return callbackNotice(updateContext, "Manager actions are not available for this account.");
        }
        BotApiMethod<?> protectionResponse = enforceActionProtection(updateContext, callbackData);
        if (protectionResponse != null) {
            return protectionResponse;
        }
        if (callbackData.startsWith(ORDER_PREFIX)) {
            return handleOrderAction(updateContext, callbackData.substring(ORDER_PREFIX.length()));
        }
        if (callbackData.startsWith(STOCK_PREFIX)) {
            return handleLowStockAction(updateContext, callbackData.substring(STOCK_PREFIX.length()));
        }
        return callbackNotice(updateContext, "Unknown manager action.");
    }

    private BotApiMethod<?> handleOrderAction(BotUpdateContext updateContext, String actionPayload) {
        String[] tokens = actionPayload.split(":");
        if (tokens.length != 2) {
            return callbackNotice(updateContext, "Unknown manager order action.");
        }

        Long orderId = safeParseLong(tokens[1]);
        if (orderId == null) {
            return callbackNotice(updateContext, "Unknown order reference.");
        }

        return switch (tokens[0]) {
            case "ack" -> acknowledgeOrder(updateContext, orderId);
            case "accept" -> acceptOrder(updateContext, orderId);
            case "handoff" -> requestCustomerHandoff(updateContext, orderId);
            default -> callbackNotice(updateContext, "Unknown manager order action.");
        };
    }

    private BotApiMethod<?> handleLowStockAction(BotUpdateContext updateContext, String actionPayload) {
        String[] tokens = actionPayload.split(":");
        if (tokens.length != 2 || !"ack".equals(tokens[0])) {
            return callbackNotice(updateContext, "Unknown inventory action.");
        }

        Long variantId = safeParseLong(tokens[1]);
        if (variantId == null) {
            return callbackNotice(updateContext, "Unknown inventory reference.");
        }

        sendFollowUp(
            updateContext.getChatId(),
            "Low-stock alert for variant #" + variantId + " was acknowledged by manager " + updateContext.getUserId() + ".",
            "acknowledge-low-stock",
            updateContext.getUserId(),
            variantId
        );
        securityAuditService.logManagerActionCompleted(
            "acknowledge-low-stock",
            updateContext.getUserId(),
            updateContext.getChatId(),
            variantId
        );
        return callbackNotice(updateContext, "Alert acknowledged.");
    }

    private BotApiMethod<?> acknowledgeOrder(BotUpdateContext updateContext, Long orderId) {
        sendFollowUp(
            updateContext.getChatId(),
            "Order #" + orderId + " was acknowledged by manager " + updateContext.getUserId() + ".",
            "acknowledge-order",
            updateContext.getUserId(),
            orderId
        );
        securityAuditService.logManagerActionCompleted(
            "acknowledge-order",
            updateContext.getUserId(),
            updateContext.getChatId(),
            orderId
        );
        return callbackNotice(updateContext, "Order acknowledged.");
    }

    private BotApiMethod<?> acceptOrder(BotUpdateContext updateContext, Long orderId) {
        try {
            OrderDto updatedOrder = managerOrdersIntegrationService.confirmOrder(
                orderId,
                "Accepted from Telegram by manager " + updateContext.getUserId()
            );
            sendFollowUp(
                updateContext.getChatId(),
                "Order #" + updatedOrder.id() + " was accepted from Telegram. Backend status is now "
                    + updatedOrder.status() + ".",
                "accept-order",
                updateContext.getUserId(),
                updatedOrder.id()
            );
            securityAuditService.logManagerActionCompleted(
                "accept-order",
                updateContext.getUserId(),
                updateContext.getChatId(),
                updatedOrder.id()
            );
            return callbackNotice(updateContext, "Order accepted.");
        } catch (BackendApiException ex) {
            securityAuditService.logManagerActionFailed(
                "accept-order",
                updateContext.getUserId(),
                updateContext.getChatId(),
                orderId,
                ex
            );
            return callbackNotice(updateContext, ex.getMessage());
        } catch (IllegalStateException ex) {
            securityAuditService.logManagerActionFailed(
                "accept-order",
                updateContext.getUserId(),
                updateContext.getChatId(),
                orderId,
                ex
            );
            return callbackNotice(updateContext, ex.getMessage());
        }
    }

    private BotApiMethod<?> requestCustomerHandoff(BotUpdateContext updateContext, Long orderId) {
        sendFollowUp(
            updateContext.getChatId(),
            "Customer handoff was requested for order #" + orderId
                + " by manager " + updateContext.getUserId()
                + ". Continue customer follow-up in the admin panel.",
            "customer-handoff",
            updateContext.getUserId(),
            orderId
        );
        securityAuditService.logManagerActionCompleted(
            "customer-handoff",
            updateContext.getUserId(),
            updateContext.getChatId(),
            orderId
        );
        return callbackNotice(updateContext, "Customer handoff requested.");
    }

    private BotApiMethod<?> enforceActionProtection(BotUpdateContext updateContext, String callbackData) {
        InteractionThrottlingService.ThrottleDecision throttleDecision =
            interactionThrottlingService.consumeManagerAction(updateContext.getUserId());
        if (!throttleDecision.allowed()) {
            securityAuditService.logRateLimitExceeded(
                "manager-actions",
                updateContext.getUserId(),
                updateContext.getChatId(),
                throttleDecision.retryAfter()
            );
            return callbackNotice(updateContext, MANAGER_RATE_LIMIT_MESSAGE);
        }
        if (!interactionThrottlingService.tryAcquireManagerAction(updateContext.getUserId(), callbackData)) {
            securityAuditService.logReplayRejected("manager-actions", updateContext.getUserId(), updateContext.getChatId());
            return callbackNotice(updateContext, MANAGER_DUPLICATE_ACTION_MESSAGE);
        }
        return null;
    }

    private void sendFollowUp(Long chatId, String text, String action, Long actorId, Long targetId) {
        try {
            telegramApiExecutor.execute(telegramMessageFactory.message(chatId, text));
        } catch (TelegramInteractionException ex) {
            securityAuditService.logFollowUpDeliveryFailure(action, actorId, chatId, targetId, ex);
        }
    }

    private boolean isAllowedChat(BotProperties.ManagerNotifications config, Long chatId) {
        return config.resolveChatIds().contains(chatId);
    }

    private boolean isAllowedActor(BotProperties.ManagerNotifications config, Long userId) {
        return config.resolveUserIds().contains(userId);
    }

    private BotApiMethod<?> callbackNotice(BotUpdateContext updateContext, String text) {
        return telegramMessageFactory.callbackNotice(updateContext.callbackQueryId().orElseThrow(), text);
    }

    private Long safeParseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
