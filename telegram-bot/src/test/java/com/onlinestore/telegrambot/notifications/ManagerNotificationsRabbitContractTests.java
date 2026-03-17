package com.onlinestore.telegrambot.notifications;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.onlinestore.telegrambot.config.ManagerNotificationsRabbitConfiguration;
import com.onlinestore.telegrambot.integration.dto.orders.OrderDto;
import com.onlinestore.telegrambot.notifications.dto.ProductLowStockEventPayload;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class ManagerNotificationsRabbitContractTests {

    @Container
    private static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management");

    @Test
    void orderExchangeBindingsDeliverEventsToManagerOrderListener() {
        try (AnnotationConfigApplicationContext context = createContext()) {
            RabbitTemplate rabbitTemplate = context.getBean(RabbitTemplate.class);
            ManagerNotificationService managerNotificationService = context.getBean(ManagerNotificationService.class);

            rabbitTemplate.convertAndSend(
                ManagerNotificationsRabbitConfiguration.ORDER_EXCHANGE,
                "order.created",
                new OrderDto(42L, 11L, "PENDING", new BigDecimal("19.99"), "USD", List.of(), Instant.parse("2026-03-17T18:00:00Z"))
            );
            rabbitTemplate.convertAndSend(
                ManagerNotificationsRabbitConfiguration.ORDER_EXCHANGE,
                "order.status-changed",
                new OrderDto(42L, 11L, "PAID", new BigDecimal("19.99"), "USD", List.of(), Instant.parse("2026-03-17T18:05:00Z"))
            );

            verify(managerNotificationService, timeout(5_000)).notifyOrderCreated(argThat(order ->
                order != null
                    && order.id().equals(42L)
                    && order.userId().equals(11L)
                    && "PENDING".equals(order.status())
            ));
            verify(managerNotificationService, timeout(5_000)).notifyOrderStatusChanged(argThat(order ->
                order != null
                    && order.id().equals(42L)
                    && order.userId().equals(11L)
                    && "PAID".equals(order.status())
            ));
        }
    }

    @Test
    void productExchangeBindingDeliversLowStockEventsToManagerProductListener() {
        try (AnnotationConfigApplicationContext context = createContext()) {
            RabbitTemplate rabbitTemplate = context.getBean(RabbitTemplate.class);
            ManagerNotificationService managerNotificationService = context.getBean(ManagerNotificationService.class);

            rabbitTemplate.convertAndSend(
                ManagerNotificationsRabbitConfiguration.PRODUCT_EXCHANGE,
                "product.low-stock",
                new ProductLowStockEventPayload(
                    10L,
                    "Laptop",
                    20L,
                    "Silver",
                    "SKU-20",
                    5,
                    5,
                    Instant.parse("2026-03-17T18:00:00Z")
                )
            );

            verify(managerNotificationService, timeout(5_000)).notifyProductLowStock(argThat(event ->
                event != null
                    && event.productId().equals(10L)
                    && event.variantId().equals(20L)
                    && "SKU-20".equals(event.sku())
            ));
        }
    }

    private AnnotationConfigApplicationContext createContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(TestRabbitConfiguration.class);
        context.refresh();

        RabbitAdmin rabbitAdmin = context.getBean(RabbitAdmin.class);
        rabbitAdmin.initialize();
        rabbitAdmin.purgeQueue(ManagerNotificationsRabbitConfiguration.MANAGER_ORDER_QUEUE, true);
        rabbitAdmin.purgeQueue(ManagerNotificationsRabbitConfiguration.MANAGER_PRODUCT_QUEUE, true);
        reset(context.getBean(ManagerNotificationService.class));
        return context;
    }

    @Configuration
    @EnableRabbit
    @Import({
        ManagerNotificationsRabbitConfiguration.class,
        ManagerOrderEventListener.class,
        ManagerProductEventListener.class
    })
    static class TestRabbitConfiguration {

        @Bean
        ConnectionFactory connectionFactory() {
            CachingConnectionFactory connectionFactory = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
            connectionFactory.setUsername(RABBIT.getAdminUsername());
            connectionFactory.setPassword(RABBIT.getAdminPassword());
            return connectionFactory;
        }

        @Bean
        RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
            RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
            rabbitAdmin.setAutoStartup(true);
            return rabbitAdmin;
        }

        @Bean
        RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter telegramRabbitMessageConverter) {
            RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
            rabbitTemplate.setMessageConverter(telegramRabbitMessageConverter);
            return rabbitTemplate;
        }

        @Bean
        ManagerNotificationService managerNotificationService() {
            return mock(ManagerNotificationService.class);
        }
    }
}
