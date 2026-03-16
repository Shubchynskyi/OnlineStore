package com.onlinestore.telegrambot.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.telegrambot.config.BotProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@ExtendWith(MockitoExtension.class)
class TelegramApiExecutorTests {

    @Mock
    private TelegramClient telegramClient;

    private TelegramApiExecutor telegramApiExecutor;

    @BeforeEach
    void setUp() {
        BotProperties botProperties = new BotProperties();
        botProperties.setToken("test-token");
        botProperties.getRetry().setMaxAttempts(3);
        botProperties.getRetry().setBackoff(Duration.ZERO);
        telegramApiExecutor = new TelegramApiExecutor(telegramClient, botProperties);
    }

    @Test
    void retriesGenericTelegramFailuresAndEventuallySucceeds() throws TelegramApiException {
        SendMessage sendMessage = SendMessage.builder().chatId("1").text("Hello").build();
        Message deliveredMessage = new Message();

        when(telegramClient.execute(sendMessage))
            .thenThrow(new TelegramApiException("temporary network error"))
            .thenReturn(deliveredMessage);

        Message response = telegramApiExecutor.execute(sendMessage);

        assertThat(response).isSameAs(deliveredMessage);
        verify(telegramClient, times(2)).execute(sendMessage);
    }

    @Test
    void doesNotRetryNonRetryableRequestFailures() throws TelegramApiException {
        SendMessage sendMessage = SendMessage.builder().chatId("1").text("Hello").build();

        when(telegramClient.execute(sendMessage))
            .thenThrow(new TelegramApiRequestException("bad request"));

        assertThatThrownBy(() -> telegramApiExecutor.execute(sendMessage))
            .isInstanceOf(TelegramInteractionException.class)
            .hasMessageContaining("Telegram API request failed");

        verify(telegramClient, times(1)).execute(sendMessage);
    }
}
