package com.onlinestore.catalog.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ProductAttributeDTO(
    Long id,
    String name,
    Map<String, Object> value
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public ProductAttributeDTO {
        value = value == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
