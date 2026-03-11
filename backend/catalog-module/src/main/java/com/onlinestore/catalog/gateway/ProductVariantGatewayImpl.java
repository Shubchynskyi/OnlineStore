package com.onlinestore.catalog.gateway;

import com.onlinestore.catalog.entity.ProductVariant;
import com.onlinestore.catalog.repository.ProductVariantRepository;
import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.common.port.catalog.ProductVariantGateway;
import com.onlinestore.common.port.catalog.ProductVariantOrderView;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductVariantGatewayImpl implements ProductVariantGateway {

    private final ProductVariantRepository productVariantRepository;

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
}
