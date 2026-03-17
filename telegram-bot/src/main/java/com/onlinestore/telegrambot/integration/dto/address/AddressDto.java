package com.onlinestore.telegrambot.integration.dto.address;

public record AddressDto(
    Long id,
    String label,
    String country,
    String city,
    String street,
    String building,
    String apartment,
    String postalCode,
    boolean isDefault
) {
}
