package com.onlinestore.telegrambot.integration.dto.catalog;

import java.util.Map;

public record ProductAttributeDto(
    Long id,
    String name,
    Map<String, Object> value
) {
}
