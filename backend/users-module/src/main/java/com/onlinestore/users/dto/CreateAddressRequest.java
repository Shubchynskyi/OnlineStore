package com.onlinestore.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAddressRequest(
    @Size(max = 100) String label,
    @NotBlank @Size(max = 3) String country,
    @NotBlank @Size(max = 100) String city,
    @NotBlank @Size(max = 255) String street,
    @Size(max = 50) String building,
    @Size(max = 50) String apartment,
    @NotBlank @Size(max = 20) String postalCode,
    boolean isDefault
) {
}
