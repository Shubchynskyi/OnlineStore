package com.onlinestore.search.listener;

import com.onlinestore.catalog.dto.ProductDTO;
import com.onlinestore.common.config.RabbitMQConfig;
import com.onlinestore.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "onlinestore.search.enabled", havingValue = "true", matchIfMissing = true)
public class ProductEventListener {

    private final SearchService searchService;

    @RabbitListener(queues = RabbitMQConfig.PRODUCT_SEARCH_QUEUE)
    public void handleProductEvent(
        ProductDTO product,
        @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey
    ) {
        switch (routingKey) {
            case "product.created", "product.updated" -> searchService.indexProduct(product);
            case "product.deleted" -> searchService.deleteProduct(product.id());
            default -> {
                // ignore unknown routing keys
            }
        }
    }
}
