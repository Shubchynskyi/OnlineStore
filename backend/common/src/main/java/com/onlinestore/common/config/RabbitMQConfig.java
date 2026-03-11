package com.onlinestore.common.config;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String PRODUCT_EXCHANGE = "product.events";
    public static final String ORDER_EXCHANGE = "order.events";
    public static final String PAYMENT_EXCHANGE = "payment.events";
    public static final String NOTIFICATION_EXCHANGE = "notification.events";
    public static final String CACHE_INVALIDATION_EXCHANGE = "cache.invalidation";
    public static final String DLQ_EXCHANGE = "dlq.exchange";

    public static final String PRODUCT_SEARCH_QUEUE = "product.search.sync";
    public static final String PRODUCT_CACHE_QUEUE = "product.cache.invalidation";
    public static final String ORDER_NOTIFICATION_QUEUE = "order.notification";
    public static final String ORDER_PAYMENT_QUEUE = "order.payment.process";
    public static final String PAYMENT_ORDER_QUEUE = "payment.order.update";
    public static final String NOTIFICATION_EMAIL_QUEUE = "notification.email";
    public static final String NOTIFICATION_PUSH_QUEUE = "notification.push";

    @Bean
    public TopicExchange productExchange() {
        return new TopicExchange(PRODUCT_EXCHANGE);
    }

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(ORDER_EXCHANGE);
    }

    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(PAYMENT_EXCHANGE);
    }

    @Bean
    public FanoutExchange notificationExchange() {
        return new FanoutExchange(NOTIFICATION_EXCHANGE);
    }

    @Bean
    public FanoutExchange cacheInvalidationExchange() {
        return new FanoutExchange(CACHE_INVALIDATION_EXCHANGE);
    }

    @Bean
    public DirectExchange dlqExchange() {
        return new DirectExchange(DLQ_EXCHANGE);
    }

    @Bean
    public Queue productSearchQueue() {
        return QueueBuilder.durable(PRODUCT_SEARCH_QUEUE)
            .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE)
            .withArgument("x-dead-letter-routing-key", PRODUCT_SEARCH_QUEUE + ".dlq")
            .build();
    }

    @Bean
    public Binding productSearchBinding() {
        return BindingBuilder.bind(productSearchQueue()).to(productExchange()).with("product.#");
    }

    @Bean
    public Queue orderNotificationQueue() {
        return QueueBuilder.durable(ORDER_NOTIFICATION_QUEUE)
            .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE)
            .withArgument("x-dead-letter-routing-key", ORDER_NOTIFICATION_QUEUE + ".dlq")
            .build();
    }

    @Bean
    public Binding orderNotificationBinding() {
        return BindingBuilder.bind(orderNotificationQueue()).to(orderExchange()).with("order.#");
    }

    @Bean
    public Queue paymentOrderQueue() {
        return QueueBuilder.durable(PAYMENT_ORDER_QUEUE)
            .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE)
            .withArgument("x-dead-letter-routing-key", PAYMENT_ORDER_QUEUE + ".dlq")
            .build();
    }

    @Bean
    public Binding paymentOrderBinding() {
        return BindingBuilder.bind(paymentOrderQueue()).to(paymentExchange()).with("payment.completed");
    }

    @Bean
    public Queue notificationEmailQueue() {
        return QueueBuilder.durable(NOTIFICATION_EMAIL_QUEUE)
            .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE)
            .build();
    }

    @Bean
    public Queue notificationPushQueue() {
        return QueueBuilder.durable(NOTIFICATION_PUSH_QUEUE)
            .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE)
            .build();
    }

    @Bean
    public Binding notificationEmailBinding() {
        return BindingBuilder.bind(notificationEmailQueue()).to(notificationExchange());
    }

    @Bean
    public Binding notificationPushBinding() {
        return BindingBuilder.bind(notificationPushQueue()).to(notificationExchange());
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
        ConnectionFactory connectionFactory,
        Jackson2JsonMessageConverter converter
    ) {
        var template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
