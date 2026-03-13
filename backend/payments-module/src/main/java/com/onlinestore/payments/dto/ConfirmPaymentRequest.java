package com.onlinestore.payments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmPaymentRequest(
    @NotBlank @Size(max = 128) String idempotencyKey
) {
}
