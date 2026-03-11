package com.onlinestore.users.dto;

public record AddressDTO(
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
