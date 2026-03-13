package com.onlinestore.catalog.dto;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record VariantDTO(
    Long id,
    String sku,
    String name,
    BigDecimal price,
    String currency,
    BigDecimal compareAtPrice,
    Integer stock,
    Map<String, Object> attributes,
    boolean active
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public VariantDTO {
        attributes = attributes == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
