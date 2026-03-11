package com.onlinestore.notifications.channel;

public interface NotificationChannel {

    String getChannelType();

    void send(NotificationMessage message);
}
