package com.onlinestore.catalog.gateway;

import com.onlinestore.catalog.event.ProductLowStockEvent;
import com.onlinestore.catalog.entity.ProductVariant;
import com.onlinestore.catalog.repository.ProductVariantRepository;
import com.onlinestore.common.config.RabbitMQConfig;
import com.onlinestore.common.event.OutboxService;
import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.common.port.catalog.ProductVariantGateway;
import com.onlinestore.common.port.catalog.ProductVariantOrderView;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ProductVariantGatewayImpl implements ProductVariantGateway {

    private final ProductVariantRepository productVariantRepository;
    private final OutboxService outboxService;

    @Override
    public Optional<ProductVariantOrderView> findById(Long variantId) {
        return Optional.ofNullable(findByIds(java.util.List.of(variantId)).get(variantId));
    }

    @Override
    public Map<Long, ProductVariantOrderView> findByIds(Collection<Long> variantIds) {
        if (variantIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, ProductVariantOrderView> variantsById = new LinkedHashMap<>();
        for (var variant : productVariantRepository.findAllWithProductByIdIn(variantIds)) {
            variantsById.put(variant.getId(), toOrderView(variant));
        }
        return variantsById;
    }

    @Override
    @Transactional
    public void reserveStock(Long variantId, Integer quantity) {
        int updatedRows = productVariantRepository.reserveStock(variantId, quantity);
        if (updatedRows == 0) {
            throw new BusinessException("INSUFFICIENT_STOCK", "Insufficient stock for variant id: " + variantId);
        }

        publishLowStockEventIfThresholdReached(variantId, quantity);
    }

    private ProductVariantOrderView toOrderView(ProductVariant variant) {
        return new ProductVariantOrderView(
            variant.getId(),
            variant.getSku(),
            variant.getName(),
            variant.getProduct().getName(),
            variant.getPriceAmount(),
            variant.getPriceCurrency(),
            variant.getStock()
        );
    }

    private void publishLowStockEventIfThresholdReached(Long variantId, Integer reservedQuantity) {
        if (reservedQuantity == null || reservedQuantity <= 0) {
            return;
        }

        ProductVariant variant = productVariantRepository.findAllWithProductByIdIn(List.of(variantId)).stream()
            .findFirst()
            .orElse(null);
        if (variant == null) {
            log.warn("Reserved stock for variant {}, but the variant could not be reloaded for low-stock evaluation", variantId);
            return;
        }

        Integer currentStock = variant.getStock();
        Integer lowStockThreshold = variant.getLowStockThreshold();
        boolean crossedThreshold = currentStock != null
            && lowStockThreshold != null
            && currentStock <= lowStockThreshold
            && currentStock + reservedQuantity > lowStockThreshold;
        if (!crossedThreshold) {
            return;
        }

        outboxService.queueEvent(
            RabbitMQConfig.PRODUCT_EXCHANGE,
            "product.low-stock",
            new ProductLowStockEvent(
                variant.getProduct().getId(),
                variant.getProduct().getName(),
                variant.getId(),
                variant.getName(),
                variant.getSku(),
                currentStock,
                lowStockThreshold,
                Instant.now()
            )
        );
    }
}
