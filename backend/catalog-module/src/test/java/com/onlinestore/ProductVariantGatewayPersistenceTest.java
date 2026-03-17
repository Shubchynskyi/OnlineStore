package com.onlinestore;

import static org.assertj.core.api.Assertions.assertThat;

import com.onlinestore.catalog.entity.Product;
import com.onlinestore.catalog.entity.ProductVariant;
import com.onlinestore.catalog.gateway.ProductVariantGatewayImpl;
import com.onlinestore.catalog.repository.ProductVariantRepository;
import com.onlinestore.common.config.AuditorConfig;
import com.onlinestore.common.event.OutboxEventRepository;
import com.onlinestore.common.event.OutboxService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    classes = ProductVariantGatewayPersistenceTest.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog-gateway-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
    }
)
class ProductVariantGatewayPersistenceTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ProductVariantGatewayImpl gateway;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    @Transactional
    void reserveStockShouldPublishLowStockEventAfterBulkUpdateWhenVariantWasLoadedEarlierInTransaction() {
        Long variantId = persistVariantWithStock(6, 5);

        assertThat(gateway.findByIds(List.of(variantId)))
            .containsKey(variantId)
            .extractingByKey(variantId)
            .extracting(orderView -> orderView.stock())
            .isEqualTo(6);

        gateway.reserveStock(variantId, 1);

        assertThat(productVariantRepository.findById(variantId))
            .get()
            .extracting(ProductVariant::getStock)
            .isEqualTo(5);

        assertThat(outboxEventRepository.findAll())
            .singleElement()
            .satisfies(event -> {
                assertThat(event.getRoutingKey()).isEqualTo("product.low-stock");
                assertThat(event.getEventType()).isEqualTo("product.low-stock");
                assertThat(event.getPayload()).contains("\"variantId\":" + variantId);
                assertThat(event.getPayload()).contains("\"currentStock\":5");
                assertThat(event.getPayload()).contains("\"lowStockThreshold\":5");
            });
    }

    private Long persistVariantWithStock(int stock, int lowStockThreshold) {
        var product = new Product();
        product.setName("Laptop");
        product.setSlug("laptop");
        product.setDescription("Manager notification test product");
        entityManager.persist(product);

        var variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku("SKU-LOW-STOCK");
        variant.setName("Silver");
        variant.setPriceAmount(new BigDecimal("199.99"));
        variant.setPriceCurrency("EUR");
        variant.setStock(stock);
        variant.setLowStockThreshold(lowStockThreshold);
        variant.setAttributes(Map.of("color", "silver"));
        entityManager.persist(variant);
        entityManager.flush();
        return variant.getId();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJpaRepositories(basePackageClasses = {ProductVariantRepository.class, OutboxEventRepository.class})
    @Import({
        AuditorConfig.class,
        OutboxService.class,
        ProductVariantGatewayImpl.class,
        JacksonTestConfiguration.class
    })
    static class TestApplication {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JacksonTestConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
