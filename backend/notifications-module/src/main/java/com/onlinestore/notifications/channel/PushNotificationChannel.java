package com.onlinestore.notifications.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PushNotificationChannel implements NotificationChannel {

    @Override
    public String getChannelType() {
        return "PUSH";
    }

    @Override
    public void send(NotificationMessage message) {
        log.info(
            "Push notification dispatched: recipient={}, subject={}, dataKeys={}",
            message.recipient(),
            message.subject(),
            message.data().keySet()
        );
    }
}
