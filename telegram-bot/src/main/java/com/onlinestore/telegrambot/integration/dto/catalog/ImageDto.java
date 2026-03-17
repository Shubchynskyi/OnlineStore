package com.onlinestore.telegrambot.integration.dto.catalog;

public record ImageDto(
    Long id,
    String url,
    String altText,
    int sortOrder,
    boolean isMain
) {
}
