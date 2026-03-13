package com.onlinestore.notifications.service;

import com.onlinestore.notifications.dto.NotificationEvent;
import com.onlinestore.orders.dto.OrderDTO;

public interface NotificationService {

    void send(NotificationEvent event);

    void notifyOrderCreated(OrderDTO order);

    void notifyOrderStatusChanged(OrderDTO order);
}
