package com.onlinestore.telegrambot.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ManagerNotificationsRabbitConfiguration {

    public static final String ORDER_EXCHANGE = "order.events";
    public static final String PRODUCT_EXCHANGE = "product.events";
    public static final String MANAGER_ORDER_QUEUE = "telegram.manager.orders";
    public static final String MANAGER_PRODUCT_QUEUE = "telegram.manager.products.low-stock";

    @Bean
    public TopicExchange telegramManagerOrderExchange() {
        return new TopicExchange(ORDER_EXCHANGE);
    }

    @Bean
    public TopicExchange telegramManagerProductExchange() {
        return new TopicExchange(PRODUCT_EXCHANGE);
    }

    @Bean
    public Queue telegramManagerOrderQueue() {
        return new Queue(MANAGER_ORDER_QUEUE, true);
    }

    @Bean
    public Queue telegramManagerProductQueue() {
        return new Queue(MANAGER_PRODUCT_QUEUE, true);
    }

    @Bean
    public Binding telegramManagerOrderCreatedBinding() {
        return BindingBuilder.bind(telegramManagerOrderQueue()).to(telegramManagerOrderExchange()).with("order.created");
    }

    @Bean
    public Binding telegramManagerOrderStatusChangedBinding() {
        return BindingBuilder.bind(telegramManagerOrderQueue()).to(telegramManagerOrderExchange()).with("order.status-changed");
    }

    @Bean
    public Binding telegramManagerProductLowStockBinding() {
        return BindingBuilder.bind(telegramManagerProductQueue()).to(telegramManagerProductExchange()).with("product.low-stock");
    }

    @Bean
    public MessageConverter telegramRabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory telegramBotRabbitListenerContainerFactory(
        ConnectionFactory connectionFactory,
        MessageConverter telegramRabbitMessageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(telegramRabbitMessageConverter);
        return factory;
    }
}
