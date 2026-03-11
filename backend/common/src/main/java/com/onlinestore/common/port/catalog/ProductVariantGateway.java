package com.onlinestore.common.port.catalog;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface ProductVariantGateway {

    Optional<ProductVariantOrderView> findById(Long variantId);

    Map<Long, ProductVariantOrderView> findByIds(Collection<Long> variantIds);

    void reserveStock(Long variantId, Integer quantity);
}
