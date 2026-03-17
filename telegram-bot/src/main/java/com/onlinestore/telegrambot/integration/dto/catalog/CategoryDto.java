package com.onlinestore.telegrambot.integration.dto.catalog;

public record CategoryDto(
    Long id,
    String name,
    String slug,
    String description
) {
}
